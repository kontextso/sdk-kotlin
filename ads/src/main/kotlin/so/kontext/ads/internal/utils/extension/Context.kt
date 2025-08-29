package so.kontext.ads.internal.utils.extension

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

internal fun Context.launchCustomTab(url: String) {
    try {
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()

        customTab.launchUrl(this, url.toUri())
    } catch (exception: ActivityNotFoundException) {
        Log.e(
            "Kontext SDK",
            "Failed to launch Custom Tab for URL: $url. No browser application found on the device.",
            exception,
        )
    }
}
