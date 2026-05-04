package so.kontext.ads.internal.ui

import android.webkit.WebView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import so.kontext.ads.R
import so.kontext.ads.domain.OmCreativeType
import so.kontext.ads.internal.utils.om.WebViewOmSession

/**
 * Regression test for KON-1714 — IAB OMID compliance flagged the v3 sdk-kotlin
 * interstitial reporting `adView.geometry` of 1×1 in `geometryChange`. Root
 * cause: `WebViewOmSession.start(...)` ran while the modal WebView was still
 * `View.INVISIBLE` / pre-layout, so `registerAdView` cached 1×1 forever.
 *
 * Fix: gate the OMID start behind `isVisible = true` + `doOnPreDraw`.
 */
@RunWith(RobolectricTestRunner::class)
class ModalAdActivityOmSessionTest {

    @After
    fun tearDown() {
        unmockkObject(WebViewOmSession)
    }

    @Test
    fun `AdDoneComponent flips visibility but defers OMID start until first preDraw`() {
        mockkObject(WebViewOmSession)
        every { WebViewOmSession.start(any(), any(), any()) } just Runs
        every { WebViewOmSession.finish(any()) } just Runs

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ModalAdActivity.getMainActivityIntent(
            context = context,
            timeout = ModalTimeoutDefault,
            adServerUrl = "https://ads.test",
            modalUrl = "https://ads.test/modal",
            omCreativeType = OmCreativeType.VIDEO,
        )

        val controller = Robolectric.buildActivity(ModalAdActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .visible()

        val webView: WebView = controller.get().findViewById(R.id.webView)
        assertNotNull(webView)
        // Activity sets isInvisible = true during setupWebView.
        assertFalse("WebView should start invisible", webView.isVisible)

        val bridge = Shadows.shadowOf(webView).getJavascriptInterface(IFrameBridgeName) as IFrameBridge

        bridge.onMessage("""{"type":"ad-done-component-iframe"}""")
        // Drain WebView.post() that hops the bridge worker thread back to main.
        ShadowLooper.idleMainLooper()

        assertTrue("WebView should be visible after AdDoneComponent", webView.isVisible)
        verify(exactly = 0) { WebViewOmSession.start(any(), any(), any()) }

        webView.viewTreeObserver.dispatchOnPreDraw()
        ShadowLooper.idleMainLooper()

        verify(exactly = 1) {
            WebViewOmSession.start(webView, "https://ads.test/modal", OmCreativeType.VIDEO)
        }
    }
}
