package so.kontext.ads.internal.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import so.kontext.ads.R
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.extension.launchCustomTab

internal const val ModalTimeoutDefault = 5_000

private const val TimeoutIntentArg = "timeout_intent_arg"
private const val ModalUrlIntentArg = "modal_url_intent_arg"
private const val AdServerUrlIntentArg = "ad_server_url_intent_arg"

internal class ModalAdActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val initDeferred = CompletableDeferred<Unit>()

    companion object {
        fun getMainActivityIntent(context: Context, timeout: Int, adServerUrl: String, modalUrl: String) =
            Intent(context, ModalAdActivity::class.java).apply {
                putExtra(TimeoutIntentArg, timeout)
                putExtra(AdServerUrlIntentArg, adServerUrl)
                putExtra(ModalUrlIntentArg, modalUrl)

                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timeout = intent.getIntExtra(TimeoutIntentArg, ModalTimeoutDefault)
        val adServerUrl = intent.getStringExtra(AdServerUrlIntentArg)
        val modalUrl = intent.getStringExtra(ModalUrlIntentArg)

        if (adServerUrl == null || modalUrl == null) {
            finish()
            return
        }

        setContentView(R.layout.webview_layout)

        webView = findViewById(R.id.webView)
        webView.setBackgroundColor(Color.BLACK)
        webView.isInvisible = true

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true

            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                IFrameBridge.DocumentStartScript.trimIndent(),
                setOf("*"),
            )
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                IFrameBridge.PosterStartScript.trimIndent(),
                setOf("*"),
            )
        }

        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(
            IFrameBridge { event ->
                when (event) {
                    is IFrameEvent.Click -> {
                        this@ModalAdActivity.launchCustomTab(adServerUrl + event.url)
                    }
                    is IFrameEvent.InitComponent -> {
                        if (initDeferred.isCompleted.not()) {
                            initDeferred.complete(Unit)
                        }
                    }
                    is IFrameEvent.CloseComponent,
                    is IFrameEvent.ErrorComponent,
                    -> {
                        finish()
                    }
                    else -> {}
                }
            },
            IFrameBridgeName,
        )
        webView.loadUrl(modalUrl)

        lifecycleScope.launch {
            finishOnTimeout(timeout = timeout)
        }
    }

    private suspend fun finishOnTimeout(timeout: Int) {
        val initialized = withTimeoutOrNull(timeout.toLong()) {
            initDeferred.await()
            true
        } ?: false

        if (initialized) {
            webView.isVisible = true
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        initDeferred.cancel()

        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()

        lifecycleScope.cancel()
        super.onDestroy()
    }
}
