package so.kontext.ads.internal.utils.extension

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

internal fun Context.launchCustomTab(url: String) {
    val customTab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .build()

    customTab.launchUrl(this, url.toUri())
}
