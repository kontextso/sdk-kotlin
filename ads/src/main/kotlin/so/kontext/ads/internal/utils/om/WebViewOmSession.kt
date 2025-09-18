package so.kontext.ads.internal.utils.om

import android.util.Log
import android.webkit.WebView
import com.iab.omid.library.kontextso.adsession.AdSession
import com.iab.omid.library.kontextso.adsession.AdSessionConfiguration
import com.iab.omid.library.kontextso.adsession.AdSessionContext
import com.iab.omid.library.kontextso.adsession.CreativeType
import com.iab.omid.library.kontextso.adsession.ImpressionType
import com.iab.omid.library.kontextso.adsession.Owner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.WeakHashMap
import kotlin.collections.set

internal object WebViewOmSession {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = WeakHashMap<WebView, AdSession>()

    fun startIfNeeded(webView: WebView, contentUrl: String?) {
        if (sessions.containsKey(webView)) return

        mainScope.launch {
            try {
                val sessionContext = AdSessionContext.createHtmlAdSessionContext(
                    OmSdk.partner,
                    webView,
                    contentUrl,
                    "",
                )
                val sessionConfiguration = AdSessionConfiguration.createAdSessionConfiguration(
                    CreativeType.HTML_DISPLAY,
                    ImpressionType.BEGIN_TO_RENDER,
                    Owner.JAVASCRIPT,
                    Owner.NONE,
                    false,
                )
                val session = AdSession.createAdSession(sessionConfiguration, sessionContext).apply {
                    registerAdView(webView)
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

    fun finish(webView: WebView) {
        mainScope.launch {
            sessions.remove(webView)?.finish()

            // Keep a strong reference to the webview for ≥ 1s per OMID guidance
            // The lambda now captures 'webView', creating a strong reference
            // that prevents it from being garbage collected for the delay period
            withContext(Dispatchers.Main) {
                webView.postDelayed(
                    { webView.hashCode() },
                    1100,
                )
            }
        }
    }

    fun close() {
        mainScope.cancel()
    }
}
