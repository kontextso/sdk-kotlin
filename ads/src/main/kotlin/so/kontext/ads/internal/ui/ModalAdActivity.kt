package so.kontext.ads.internal.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import so.kontext.ads.R
import so.kontext.ads.domain.OmCreativeType
import so.kontext.ads.internal.data.mapper.toPublicAdEvent
import so.kontext.ads.internal.di.KontextDependencies
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.extension.launchCustomTab
import so.kontext.ads.internal.utils.om.WebViewOmSession

internal const val ModalTimeoutDefault = 5_000

private const val TimeoutIntentArg = "timeout_intent_arg"
private const val ModalUrlIntentArg = "modal_url_intent_arg"
private const val AdServerUrlIntentArg = "ad_server_url_intent_arg"
private const val OmCreativeTypeIntentArg = "om_creative_type_intent_arg"

internal class ModalAdActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val initDeferred = CompletableDeferred<Unit>()

    companion object {
        fun getMainActivityIntent(
            context: Context,
            timeout: Int,
            adServerUrl: String,
            modalUrl: String,
            omCreativeType: OmCreativeType?,
        ) = Intent(context, ModalAdActivity::class.java).apply {
            putExtra(TimeoutIntentArg, timeout)
            putExtra(AdServerUrlIntentArg, adServerUrl)
            putExtra(ModalUrlIntentArg, modalUrl)
            putExtra(OmCreativeTypeIntentArg, omCreativeType?.name)

            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Disable back button while modal ad is displayed
                }
            },
        )

        val timeout = intent.getIntExtra(TimeoutIntentArg, ModalTimeoutDefault)
        val adServerUrl = intent.getStringExtra(AdServerUrlIntentArg)
        val modalUrl = intent.getStringExtra(ModalUrlIntentArg)
        val omCreativeType = intent.getStringExtra(OmCreativeTypeIntentArg)
            ?.let { name -> OmCreativeType.entries.find { it.name == name } }

        if (adServerUrl == null || modalUrl == null) {
            finish()
            return
        }

        setContentView(R.layout.webview_layout)

        webView = findViewById(R.id.webView)
        setupWebView(
            modalUrl = modalUrl,
            adServerUrl = adServerUrl,
            omCreativeType = omCreativeType,
        )

        lifecycleScope.launch {
            finishOnTimeout(timeout = timeout)
        }
    }

    private fun setupWebView(
        modalUrl: String,
        adServerUrl: String,
        omCreativeType: OmCreativeType?,
    ) {
        webView.apply {
            baseAdSetup(adServerUrl)

            setBackgroundColor(Color.BLACK)
            isInvisible = true

            with(webView.settings) {
                // Do not use cache, because some parts of layout like buttons, are not visible on the second opening of the webview
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            val iFrameBridge = IFrameBridge(
                eventParser = KontextDependencies.iFrameEventParser,
            ) { event ->
                when (event) {
                    is IFrameEvent.Click -> {
                        this@ModalAdActivity.launchCustomTab(adServerUrl + event.url)
                    }
                    is IFrameEvent.InitComponent -> {
                        if (initDeferred.isCompleted.not()) {
                            initDeferred.complete(Unit)
                        }
                    }
                    is IFrameEvent.AdDoneComponent -> {
                        // OMID caches the WebView's measured size at registerAdView time;
                        // gate behind visibility + first layout, otherwise it samples 1×1
                        // and reports that for the entire session (IAB compliance).
                        // post() hops from the JS-bridge worker thread back to main.
                        post {
                            isVisible = true
                            doOnPreDraw {
                                WebViewOmSession.start(this, modalUrl, omCreativeType)
                            }
                        }
                    }
                    is IFrameEvent.CloseComponent -> {
                        WebViewOmSession.finish(this)
                        finish()
                    }
                    is IFrameEvent.ErrorComponent -> {
                        WebViewOmSession.logError(this, event.code)
                        WebViewOmSession.finish(this)
                        finish()
                    }
                    is IFrameEvent.CallbackEvent -> {
                        lifecycleScope.launch {
                            KontextDependencies.modalAdEvents.emit(event.toPublicAdEvent())
                        }
                    }
                    else -> {}
                }
            }
            addJavascriptInterface(iFrameBridge, IFrameBridgeName)
            loadUrl(modalUrl)
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
        WebViewOmSession.finish(webView)
        webView.destroyDelayed()

        super.onDestroy()
    }
}
