package so.kontext.ads.ui

import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import so.kontext.ads.Ad
import so.kontext.ads.Constants
import so.kontext.ads.Session
import so.kontext.ads.model.AdOptions

/**
 * Jetpack Compose composable that renders an inline ad for a given message.
 *
 * Uses [WebViewPool] so WebViews survive LazyColumn/RecyclerView recycling
 * without reloading the iframe.
 *
 * Usage:
 * ```kotlin
 * InlineAd(ad = ad)
 * InlineAd(messageId = "a1", session = session)
 * ```
 */
@Composable
public fun InlineAd(
    ad: Ad,
    modifier: Modifier = Modifier,
) {
    val iframeUrl = ad.iframeUrl
    val height = ad.height
    val isVisible = ad.isVisible
    val destroyed = ad.destroyed

    if (iframeUrl == null || destroyed) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val view = LocalView.current

    // Pool key: unique per bid so re-composition reuses the same WebView
    val poolKey = remember(ad.messageId, iframeUrl) {
        "${ad.messageId}-${iframeUrl.hashCode()}"
    }

    val entry = remember(poolKey) {
        WebViewPool.obtain(
            key = poolKey,
            appContext = context.applicationContext,
            ad = ad,
        ) { entry ->
            // First-time setup: load the iframe URL
            entry.adWebView.load()
        }
    }

    // Track height in pool for restoration after recycling
    LaunchedEffect(height) {
        WebViewPool.updateHeight(poolKey, height.toInt())
    }

    // Dimension reporting every 200ms
    LaunchedEffect(entry, isVisible) {
        if (!isVisible) return@LaunchedEffect

        delay(500) // initial delay for layout to settle

        while (isActive) {
            val windowRect = Rect()
            view.getWindowVisibleDisplayFrame(windowRect)
            val displayMetrics = view.resources.displayMetrics

            entry.adWebView.sendDimensions(
                windowWidth = windowRect.width().toFloat(),
                windowHeight = windowRect.height().toFloat(),
                screenWidth = displayMetrics.widthPixels.toFloat(),
                screenHeight = displayMetrics.heightPixels.toFloat(),
                containerWidth = view.width.toFloat(),
                containerHeight = height,
                containerX = 0f,
                containerY = 0f,
                keyboardHeight = getKeyboardHeight(view),
            )

            delay(Constants.DIMENSION_REPORT_INTERVAL_MS)
        }
    }

    // Always attach the WebView so it can load and process JS.
    // Height is 0dp until the iframe sends resize-iframe with a real height.
    // The iframe reports height in CSS pixels, which map 1:1 to Android dp.
    val heightDp = if (isVisible && height > 0f) height.dp else 0.dp

    AndroidView(
        factory = {
            val wv = entry.webView
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv
        },
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
    )
}

/**
 * Convenience composable that creates an Ad from a session.
 */
@Composable
public fun InlineAd(
    messageId: String,
    session: Session,
    code: String? = null,
    theme: String? = null,
    modifier: Modifier = Modifier,
) {
    val ad = remember(messageId, session) {
        session.createAd(
            messageId = messageId,
            options = AdOptions(
                code = code ?: Constants.DEFAULT_PLACEMENT_CODE,
                theme = theme,
            ),
        )
    }

    DisposableEffect(ad) {
        onDispose { ad.destroy() }
    }

    InlineAd(ad = ad, modifier = modifier)
}

private fun getKeyboardHeight(view: android.view.View): Float {
    val rect = Rect()
    view.getWindowVisibleDisplayFrame(rect)
    val screenHeight = view.rootView.height
    return (screenHeight - rect.bottom).toFloat().coerceAtLeast(0f)
}
