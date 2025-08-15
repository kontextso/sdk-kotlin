package com.kontext.ads.internal

internal object AdsProperties {
    const val GooglePlayStoreUrl = "https://play.google.com/store/apps/details?id="
    const val SdkName = "sdk-kotlin"
    const val NumberOfMessages = 10

    fun iframeUrl(
        baseUrl: String,
        bidId: String,
        bidCode: String,
        messageId: String,
    ): String {
        return "${baseUrl}api/frame/$bidId?messageId=$messageId&code=$bidCode"
    }
}
