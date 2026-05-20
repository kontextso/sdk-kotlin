package so.kontext.ads.ui

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    //
    // `windowWidth/Height` is the full activity window (matches
    // sdk-swift's `UIWindow.bounds`), NOT the visible-area-minus-insets.
    // The iframe's visibility math at `ad-formats/src/react/visibility.ts`
    // subtracts `keyboardHeight` from `windowHeight` itself; if we
    // pre-subtracted insets here we'd double-count them.
    //
    // `keyboardHeight` is strictly the IME inset (0 when no keyboard).
    // The previous `view.rootView.height - rect.bottom` formula also
    // included the navigation-bar inset, so it reported ~63px even with
    // the keyboard closed, mislabeling the gesture bar as a keyboard.
    LaunchedEffect(entry, isVisible) {
        if (!isVisible) return@LaunchedEffect

        delay(500) // initial delay for layout to settle

        while (isActive) {
            val displayMetrics = view.resources.displayMetrics
            val rootView = view.rootView
            val imeInset = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f

            entry.adWebView.sendDimensions(
                windowWidth = rootView.width.toFloat(),
                windowHeight = rootView.height.toFloat(),
                screenWidth = displayMetrics.widthPixels.toFloat(),
                screenHeight = displayMetrics.heightPixels.toFloat(),
                containerWidth = containerSize.width.toFloat(),
                containerHeight = containerSize.height.toFloat(),
                containerX = containerOffset.x,
                containerY = containerOffset.y,
                keyboardHeight = imeInset,
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
    // Ad lifecycle is owned by the Session, not by composition.
    // `session.createAd` returns the existing Ad for `(messageId, code,
    // theme)` if one is still alive (Session.kt), so LazyColumn recycling
    // — including the "scroll off-screen, read other messages, scroll
    // back" pattern — finds the same Ad and reattaches the WebView from
    // WebViewPool without rebuilding state, reloading the iframe, or
    // restarting the OMID session. Cleanup happens via `Session.destroy()`
    // (which destroys every Ad) or via `createAd` replacing the Ad for
    // the same messageId with a different code/theme.
    //
    // Mirrors sdk-swift's InlineAdUIView — its `deinit` destroys the Ad,
    // and UITableViewCell reuse doesn't trigger deinit, so cell recycling
    // leaves the Ad alone.
    val ad = remember(messageId, session, code, theme) {
        session.createAd(
            messageId = messageId,
            options = AdOptions(
                code = code ?: Constants.DEFAULT_PLACEMENT_CODE,
                theme = theme,
            ),
        )
    }

    // OMID lifecycle is composition-scoped while the Ad lifecycle is
    // Session-scoped. On dispose we schedule a deferred sessionFinish
    // (grace window absorbs LazyColumn scroll-off/on) and on re-mount
    // we cancel it. Without this hook, the JS verification script polls
    // geometry on the detached WebView, reports `notFound`, and the IAB
    // validator records the impression as missing sessionFinish.
    DisposableEffect(ad) {
        ad.cancelOmSessionFinish()
        onDispose {
            ad.scheduleOmSessionFinish()
        }
    }

    InlineAd(ad = ad, modifier = modifier)
}
