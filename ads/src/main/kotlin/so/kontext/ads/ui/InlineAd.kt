package so.kontext.ads.ui

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
 * Renders an inline ad. Kept close to the v2.0.1 shape:
 *
 *  - one pooled WebView (reused across recompose / recycle, never reloaded),
 *  - a single always-on 200 ms dimension heartbeat that reports the WebView's
 *    **live** on-screen position (`getLocationInWindow`) so the iframe's
 *    viewability math stays continuous while the list scrolls (Compose's
 *    `onGloballyPositioned` does not re-fire on a parent-scroll translation),
 *  - `onResume()` while in composition, `onPause()` when out,
 *  - height driven purely by the iframe's `resize`.
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

    DisposableEffect(entry.webView) {
        entry.webView.onResume()
        onDispose { entry.webView.onPause() }
    }

    // Dimension heartbeat — always on, reads the WebView's live window
    // position each tick so viewability tracks scroll.
    LaunchedEffect(entry.webView) {
        while (isActive) {
            val wv = entry.webView
            if (wv.isAttachedToWindow) {
                val location = IntArray(2).also { wv.getLocationInWindow(it) }
                val rootView = wv.rootView
                val displayMetrics = wv.resources.displayMetrics
                val imeInset = ViewCompat.getRootWindowInsets(wv)
                    ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f

                entry.adWebView.sendDimensions(
                    windowWidth = rootView.width.toFloat(),
                    windowHeight = rootView.height.toFloat(),
                    screenWidth = displayMetrics.widthPixels.toFloat(),
                    screenHeight = displayMetrics.heightPixels.toFloat(),
                    containerWidth = wv.width.toFloat(),
                    containerHeight = wv.height.toFloat(),
                    containerX = location[0].toFloat(),
                    containerY = location[1].toFloat(),
                    keyboardHeight = imeInset,
                )
            }
            delay(Constants.DIMENSION_REPORT_INTERVAL_MS)
        }
    }

    // Cache height in the pool so a recycle restores it without a flash.
    LaunchedEffect(height) {
        WebViewPool.updateHeight(poolKey, height.toInt())
    }

    // Height comes from the iframe's resize (CSS px == dp); 0 until it fires.
    val heightDp = if (height > 0f) height.dp else 0.dp

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
