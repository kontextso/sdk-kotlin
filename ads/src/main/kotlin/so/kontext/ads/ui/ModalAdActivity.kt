package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import so.kontext.ads.internal.ui.IFrameBridge
import so.kontext.ads.internal.ui.doc_start_js
import so.kontext.ads.internal.ui.model.InlineAdEvent
import so.kontext.ads.internal.utils.extension.launchCustomTab

private const val TimeoutDefault = 5000
private const val TimeoutIntentArg = "timeout_intent_arg"
private const val UrlIntentArg = "url_intent_arg"

internal class ModalAdActivity : ComponentActivity() {

    companion object {
        const val ResultUrl = "result_url"

        fun getMainActivityIntent(context: Context, timeout: Int, url: String) =
            Intent(context, ModalAdActivity::class.java).apply {
                putExtra(TimeoutIntentArg, timeout)
                putExtra(UrlIntentArg, url)

                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timeout = intent.getIntExtra(TimeoutIntentArg, TimeoutDefault)
        val url = intent.getStringExtra(UrlIntentArg) ?: run {
            finish()
            return
        }

        setContent {
            ModalAdScreen(
                url = url,
//                timeout = timeout,
//                config = config,
//                onClose = { finish() },
//                onCloseAndOpenUrl = {
//                    val resultIntent = Intent()
//                    resultIntent.putExtra(ResultUrl, url)
//                    setResult(RESULT_OK, resultIntent)
//                    finish()
//                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun ModalAdScreen(url: String) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            setBackgroundColor(android.graphics.Color.BLACK)

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    doc_start_js.trimIndent(),
                    setOf("*"),
                )
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean {
                    if (request.isForMainFrame) {
                        context.launchCustomTab(request.url.toString())
                        return true
                    }
                    return false
                }

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    context.launchCustomTab(url)
                    return true
                }
            }

            addJavascriptInterface(
                IFrameBridge { event ->
                    when (event) {
                        is InlineAdEvent.InitComponentIframe -> {}
                        is InlineAdEvent.ClickIframe -> {}
                        is InlineAdEvent.CloseComponentIframe,
                        is InlineAdEvent.ErrorComponentIframe,
                        -> {}
                        else -> {}
                    }
                },
                IFrameBridgeName,
            )
            loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        webView.onResume()
        webView.resumeTimers()

        onDispose {
            webView.onPause()
            webView.pauseTimers()

            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
