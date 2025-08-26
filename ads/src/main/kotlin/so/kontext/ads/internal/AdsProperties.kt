package so.kontext.ads.internal

import androidx.core.net.toUri

internal object AdsProperties {
    const val GooglePlayStoreUrl = "https://play.google.com/store/apps/details?id="
    const val SdkName = "sdk-kotlin"
    const val NumberOfMessages = 10

    fun iFrameUrl(
        baseUrl: String,
        bidId: String,
        bidCode: String,
        messageId: String,
        component: String,
        theme: String? = null,
    ): String {
        val uriBuilder = "$baseUrl/api/$component/$bidId".toUri()
            .buildUpon()
            .appendQueryParameter("messageId", messageId)
            .appendQueryParameter("code", bidCode)
            .appendQueryParameter("sdk", SdkName)

        theme?.let { uriBuilder.appendQueryParameter("theme", it) }

        return uriBuilder.build().toString()
    }
}
