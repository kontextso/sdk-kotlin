package so.kontext.ads.internal

import androidx.core.net.toUri

internal object AdsProperties {
    const val GooglePlayStoreUrl = "https://play.google.com/store/apps/details?id="
    const val SdkName = "sdk-kotlin"
    const val NumberOfMessages = 10

    fun baseIFrameUrl(
        baseUrl: String,
        bidId: String,
        bidCode: String,
        messageId: String,
        otherParams: Map<String, String>,
    ): String {
        return iFrameUrl(
            baseUrl = baseUrl,
            bidId = bidId,
            bidCode = bidCode,
            messageId = messageId,
            otherParams = otherParams,
            component = "frame",
        )
    }

    fun modalIFrameUrl(
        baseUrl: String,
        bidId: String,
        bidCode: String,
        messageId: String,
        otherParams: Map<String, String>,
    ): String {
        return iFrameUrl(
            baseUrl = baseUrl,
            bidId = bidId,
            bidCode = bidCode,
            messageId = messageId,
            otherParams = otherParams,
            component = "modal",
        )
    }

    @Suppress("LongParameterList")
    private fun iFrameUrl(
        baseUrl: String,
        bidId: String,
        bidCode: String,
        messageId: String,
        component: String,
        otherParams: Map<String, String>,
    ): String {
        val uriBuilder = "$baseUrl/api/$component/$bidId".toUri()
            .buildUpon()
            .appendQueryParameter("messageId", messageId)
            .appendQueryParameter("code", bidCode)
            .appendQueryParameter("sdk", SdkName)

        otherParams.forEach { (key, value) ->
            uriBuilder.appendQueryParameter(key, value)
        }

        return uriBuilder.build().toString()
    }
}
