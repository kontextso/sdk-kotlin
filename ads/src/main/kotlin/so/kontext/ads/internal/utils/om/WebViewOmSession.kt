package so.kontext.ads.internal.utils.om

import android.util.Log
import android.webkit.WebView
import com.iab.omid.library.megabrainco.adsession.AdSession
import com.iab.omid.library.megabrainco.adsession.AdSessionConfiguration
import com.iab.omid.library.megabrainco.adsession.AdSessionContext
import com.iab.omid.library.megabrainco.adsession.CreativeType
import com.iab.omid.library.megabrainco.adsession.ErrorType
import com.iab.omid.library.megabrainco.adsession.ImpressionType
import com.iab.omid.library.megabrainco.adsession.Owner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import so.kontext.ads.domain.OmCreativeType
import java.util.Collections
import java.util.WeakHashMap
import kotlin.collections.set

internal object WebViewOmSession {

    // @Volatile ensures the reassignment after close() is visible across threads.
    @Volatile
    private var mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Recreates the scope if it was previously cancelled via close(), so the object
    // can be reused when a new AdsProviderImpl is created after the previous one is closed.
    private val activeScope: CoroutineScope
        get() {
            if (!mainScope.isActive) {
                mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            }
            return mainScope
        }

    private val sessions = Collections.synchronizedMap(WeakHashMap<WebView, AdSession>())

    fun start(webView: WebView, contentUrl: String?, creativeType: OmCreativeType?) {
        if (creativeType == null) return
        if (sessions.containsKey(webView)) return

        activeScope.launch {
            try {
                val sessionContext = AdSessionContext.createHtmlAdSessionContext(
                    OmSdk.partner,
                    webView,
                    contentUrl,
                    "",
                )
                val (omCreativeType, mediaEventsOwner) = when (creativeType) {
                    OmCreativeType.DISPLAY -> CreativeType.HTML_DISPLAY to Owner.NONE
                    OmCreativeType.VIDEO -> CreativeType.VIDEO to Owner.JAVASCRIPT
                }
                val sessionConfiguration = AdSessionConfiguration.createAdSessionConfiguration(
                    omCreativeType,
                    ImpressionType.BEGIN_TO_RENDER,
                    Owner.JAVASCRIPT,
                    mediaEventsOwner,
                    false,
                )
                val session = AdSession.createAdSession(sessionConfiguration, sessionContext).apply {
                    registerAdView(webView)
                    delay(50)
                    start()
                }
                sessions[webView] = session
            } catch (exception: IllegalArgumentException) {
                Log.e("Kontext SDK", "Om session creation failed", exception)
            } catch (exception: IllegalStateException) {
                Log.e("Kontext SDK", "Om session creation failed", exception)
            }
        }
    }

    fun logError(webView: WebView, message: String) {
        activeScope.launch {
            sessions[webView]?.error(ErrorType.GENERIC, message)
        }
    }

    fun finish(webView: WebView) {
        activeScope.launch {
            webView.evaluateJavascript("window.postMessage({ type: 'retire-iframe' }, '*');", null)
            sessions.remove(webView)?.finish()

            // Keep a strong reference to the webview for ≥ 1s per OMID guidance.
            // The coroutine captures 'webView', preventing GC for the delay period.
            // Using delay() rather than postDelayed() because the WebView is already
            // detached from its parent at this point, making postDelayed() unreliable.
            delay(1100)
        }
    }

    fun close() {
        mainScope.cancel()
    }
}
