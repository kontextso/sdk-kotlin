package so.kontext.ads.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import so.kontext.ads.Ad
import so.kontext.ads.Constants
import so.kontext.ads.Session
import so.kontext.ads.model.AdOptions

@Immutable
private data class LayoutSnapshot(
    val windowSize: IntSize,
    val containerSize: IntSize,
    val containerPos: Offset,
)

/**
 * Renders an inline ad. Mirrors the v2.0.1 production shape:
 *
 *  - one pooled WebView (reused across recompose / recycle, never reloaded),
 *  - a `Box(fillMaxWidth().height(heightDp))` whose `onGloballyPositioned`
 *    captures a [LayoutSnapshot]; the `AndroidView` inside just `fillMaxSize`s.
 *    The Box (pure Compose) determines the width independently of the
 *    WebView's intrinsic measurement, so a recycled RecyclerView row still
 *    gets a full-width container (putting `fillMaxWidth` directly on the
 *    AndroidView collapsed to 0 width on recycle),
 *  - a 200 ms dimension heartbeat reading that snapshot (required by the ad
 *    server for viewport measurement — every SDK posts it continuously),
 *  - `onResume()` while in composition, `onPause()` when out,
 *  - height driven purely by the iframe's `resize` (via [Ad.height]).
 */
@Composable
public fun InlineAd(
    ad: Ad,
    modifier: Modifier = Modifier,
) {
    val iframeUrl = ad.iframeUrl
    val height = ad.height
    val destroyed = ad.destroyed

    if (iframeUrl == null || destroyed) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    // Pool key: unique per bid so re-composition reuses the same WebView.
    val poolKey = remember(ad.messageId, iframeUrl) {
        "${ad.messageId}-${iframeUrl.hashCode()}"
    }

    val entry = remember(poolKey) {
        WebViewPool.obtain(
            key = poolKey,
            appContext = context.applicationContext,
            ad = ad,
        ) { e ->
            e.adWebView.load()
        }
    }

    // Height comes from the iframe's resize (CSS px == dp); 0 until it fires.
    val heightDp = if (height > 0f) height.dp else 0.dp

    var lastLayoutSnapshot by remember { mutableStateOf<LayoutSnapshot?>(null) }
    var lastKeyboard by remember { mutableStateOf(0f) }

    // Cache height in the pool so a recycle restores it without a flash.
    LaunchedEffect(height) {
        WebViewPool.updateHeight(poolKey, height.toInt())
    }

    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density).toFloat() }
            .distinctUntilChanged()
            .collect { lastKeyboard = it }
    }

    DisposableEffect(entry.webView) {
        entry.webView.onResume()
        onDispose { entry.webView.onPause() }
    }

    // Dimension heartbeat (200 ms) — required by the ad server. Reads the
    // layout snapshot captured by the Box's onGloballyPositioned.
    LaunchedEffect(entry.webView) {
        while (isActive) {
            delay(Constants.DIMENSION_REPORT_INTERVAL_MS)
            val snap = lastLayoutSnapshot ?: continue
            val displayMetrics = context.resources.displayMetrics

            entry.adWebView.sendDimensions(
                windowWidth = snap.windowSize.width.toFloat(),
                windowHeight = snap.windowSize.height.toFloat(),
                screenWidth = displayMetrics.widthPixels.toFloat(),
                screenHeight = displayMetrics.heightPixels.toFloat(),
                containerWidth = snap.containerSize.width.toFloat(),
                containerHeight = snap.containerSize.height.toFloat(),
                containerX = snap.containerPos.x,
                containerY = snap.containerPos.y,
                keyboardHeight = lastKeyboard,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .onGloballyPositioned { coords ->
                lastLayoutSnapshot = LayoutSnapshot(
                    windowSize = coords.findRootCoordinates().size,
                    containerSize = coords.size,
                    containerPos = coords.positionInWindow(),
                )
            },
    ) {
        AndroidView(
            factory = {
                // Ensure no previous parent before attaching here.
                (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                entry.webView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Convenience composable that resolves the [Ad] from a session. The Ad's
 * lifecycle is owned by the Session, so LazyColumn / RecyclerView recycling
 * finds the same Ad and reattaches the pooled WebView without reloading.
 */
@Composable
public fun InlineAd(
    messageId: String,
    session: Session,
    code: String? = null,
    theme: String? = null,
    modifier: Modifier = Modifier,
) {
    val ad = remember(messageId, session, code, theme) {
        session.createAd(
            messageId = messageId,
            options = AdOptions(
                code = code ?: Constants.DEFAULT_PLACEMENT_CODE,
                theme = theme,
            ),
        )
    }

    InlineAd(ad = ad, modifier = modifier)
}
