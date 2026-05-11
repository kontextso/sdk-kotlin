package so.kontext.ads.ui

import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
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

    // Container geometry — captured via onGloballyPositioned so we get the
    // AndroidView's actual position-in-window. This updates as the user
    // scrolls (parent LazyColumn re-lays out → callback fires), and as the
    // ad's height grows from 0 → final after iframe `resize-iframe`.
    var containerOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Dimension reporting every 200ms — heartbeat for viewport optimisation.
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
                containerWidth = containerSize.width.toFloat(),
                containerHeight = containerSize.height.toFloat(),
                containerX = containerOffset.x,
                containerY = containerOffset.y,
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
            .height(heightDp)
            .onGloballyPositioned { coords: LayoutCoordinates ->
                containerOffset = coords.positionInWindow()
                containerSize = coords.size
            },
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
    // Ad lifecycle: created lazily, owned by the Session. On dispose we
    // *schedule* a destroy (500 ms) instead of destroying immediately —
    // LazyColumn recycling causes rapid dispose → remount cycles, and we
    // cancel the scheduled destroy on remount. Only a real removal (new
    // message replaces this one, app navigation) lets the 500 ms elapse,
    // at which point Ad.destroy() retires + finishes the OMID session and
    // the JS verification scripts have ~1 s of WebView lifetime left to
    // POST `sessionFinish` to the validator (see WebView.destroyDelayed).
    val ad = remember(messageId, session, code, theme) {
        session.createAd(
            messageId = messageId,
            options = AdOptions(
                code = code ?: Constants.DEFAULT_PLACEMENT_CODE,
                theme = theme,
            ),
        )
    }

    DisposableEffect(ad) {
        ad.cancelPendingDestroy()
        onDispose { ad.schedulePendingDestroy() }
    }

    InlineAd(ad = ad, modifier = modifier)
}

private fun getKeyboardHeight(view: android.view.View): Float {
    val rect = Rect()
    view.getWindowVisibleDisplayFrame(rect)
    val screenHeight = view.rootView.height
    return (screenHeight - rect.bottom).toFloat().coerceAtLeast(0f)
}
