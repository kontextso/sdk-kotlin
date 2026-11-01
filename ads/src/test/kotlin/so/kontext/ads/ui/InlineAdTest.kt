package so.kontext.ads.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import so.kontext.ads.Ad
import so.kontext.ads.Session
import so.kontext.ads.TEST_INSTALL_ID
import so.kontext.ads.model.SessionOptions
import so.kontext.ads.network.HttpClient
import so.kontext.ads.resolveConfig

/**
 * Compose tests for which pooled WebView `InlineAd` keeps on screen.
 * JUnit 4 + Robolectric (run by the vintage engine), because the Compose
 * test rule is JUnit 4 only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InlineAdTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = Session(
        context = null,
        config = resolveConfig(
            SessionOptions(publisherToken = "tok", userId = "user", conversationId = "conv"),
            installId = TEST_INSTALL_ID,
        ),
        httpClient = HttpClient { _, _, _, _ -> throw IllegalStateException("No-op") },
    )

    @After
    fun clearPool() {
        WebViewPool.clearAll()
    }

    // The pool and createAd give the same message a new Ad with a new
    // iframe URL when `theme` changes; that is the case under test.
    private fun makeAd(theme: String): Ad =
        Ad(messageId = "m1", code = "inlineAd", theme = theme, session = session).apply {
            iframeUrl = "https://server.megabrain.co/api/frame/bid-1?theme=$theme"
            height = 120f
        }

    private lateinit var hostView: View

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            hostView = LocalView.current
            content()
        }
    }

    /** The one WebView attached in the window; fails if there are none or several. */
    private fun attachedWebView(): WebView {
        val found = mutableListOf<WebView>()
        fun walk(view: View) {
            if (view is WebView) found += view
            if (view is ViewGroup) (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
        }
        walk(hostView.rootView)
        assertEquals("attached WebViews", 1, found.size)
        return found.single()
    }

    @Test
    fun `a new Ad on a theme switch attaches its own WebView`() {
        var ad by mutableStateOf(makeAd("light"))
        setContent { InlineAd(ad = ad) }

        val lightWebView = composeRule.runOnIdle { attachedWebView() }

        ad = makeAd("dark")

        composeRule.runOnIdle {
            val darkWebView = attachedWebView()
            assertNotSame(lightWebView, darkWebView)
            assertNull("the old WebView must be detached", lightWebView.parent)
        }
    }

    @Test
    fun `recomposing the same Ad keeps the same WebView`() {
        val ad = makeAd("light")
        setContent { InlineAd(ad = ad) }

        val before = composeRule.runOnIdle { attachedWebView() }

        // A height change from the iframe's `resize` recomposes InlineAd.
        composeRule.runOnIdle { ad.height = 240f }

        composeRule.runOnIdle {
            assertSame(before, attachedWebView())
        }
    }
}
