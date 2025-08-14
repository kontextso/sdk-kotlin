package com.kontext.ads.internal

internal object AdsProperties {
    const val BaseAdServerUrl = "https://server.megabrain.co/"
    const val SdkName = "sdk-kotlin"
    const val NumberOfMessages = 10

    fun iframeUrl(
        baseUrl: String = BaseAdServerUrl,
        bidId: String,
        bidCode: String,
        messageId: String,
    ): String {
        return "${baseUrl}api/frame/$bidId?messageId=$messageId&code=$bidCode"
    }
}
