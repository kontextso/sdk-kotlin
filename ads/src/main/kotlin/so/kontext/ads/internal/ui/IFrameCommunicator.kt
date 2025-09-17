package so.kontext.ads.internal.ui

import android.webkit.WebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.data.dto.request.iframe.UpdateDimensionsDataDto
import so.kontext.ads.internal.data.dto.request.iframe.UpdateDimensionsRequest
import so.kontext.ads.internal.data.dto.request.iframe.UpdateIFrameDataDto
import so.kontext.ads.internal.data.dto.request.iframe.UpdateIFrameRequest
import so.kontext.ads.internal.data.mapper.toDto
import so.kontext.ads.internal.ui.model.AdDimensions

private const val UpdateIFrame = "update-iframe"
private const val UpdateDimensionsIFrame = "update-dimensions-iframe"

internal interface IFrameCommunicator {
    fun sendUpdate(config: AdConfig)
    fun sendDimensions(adDimensions: AdDimensions)
}

internal class IFrameCommunicatorImpl(
    private val webView: WebView,
) : IFrameCommunicator {

    private var lastSentAdDimens: AdDimensions? = null

    override fun sendUpdate(config: AdConfig) {
        val updatePayload = UpdateIFrameRequest(
            type = UpdateIFrame,
            code = config.bid.code,
            data = UpdateIFrameDataDto(
                messages = config.messages.map { it.toDto() },
                messageId = config.messageId,
                sdk = config.sdk,
                otherParams = config.otherParams,
            ),
        )
        val json = Json.encodeToString(updatePayload)
        postMessage(json)
    }

    override fun sendDimensions(adDimensions: AdDimensions) {
        if (lastSentAdDimens == adDimensions) return
        lastSentAdDimens = adDimensions

        val updatePayload = UpdateDimensionsRequest(
            type = UpdateDimensionsIFrame,
            data = UpdateDimensionsDataDto(
                windowWidth = adDimensions.windowWidth,
                windowHeight = adDimensions.windowHeight,
                containerWidth = adDimensions.containerWidth,
                containerHeight = adDimensions.containerHeight,
                containerX = adDimensions.containerX,
                containerY = adDimensions.containerY,
                keyboardHeight = adDimensions.keyboardHeight,
            ),
        )
        val json = Json.encodeToString(updatePayload)
        postMessage(json)
    }

    private fun postMessage(json: String) {
        if (!webView.isAttachedToWindow) return
        webView.post {
            webView.evaluateJavascript("window.postMessage($json, '*');", null)
        }
    }
}
