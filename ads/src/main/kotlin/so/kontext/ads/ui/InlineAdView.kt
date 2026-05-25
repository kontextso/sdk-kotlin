package so.kontext.ads.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.flow.distinctUntilChanged
import so.kontext.ads.Constants
import so.kontext.ads.Session
import so.kontext.ads.model.AdOptions

/**
 * Traditional Android View for rendering inline ads (XML layouts /
 * RecyclerView / any ViewGroup-based UI).
 *
 * Thin wrapper around a [ComposeView] that hosts the Compose [InlineAd].
 * It deliberately does NOT manage a WebView itself — all rendering, the
 * `WebViewPool` reuse, dimension reporting, and the OMID session lifecycle
 * (start / grace-period retire / restart) live in [InlineAd] and are shared
 * verbatim with the Compose integration. This is the v3 design: the View
 * path and the Compose path run one implementation, so they can't diverge.
 *
 * `ViewCompositionStrategy.DisposeOnDetachedFromWindow` means a
 * RecyclerView recycle (detach → re-bind → re-attach) tears down only the
 * *composition*, not the pooled WebView or the Session-owned Ad: re-binding
 * re-attaches the same WebView without reloading the iframe or starting a
 * second OMID session.
 *
 * Usage:
 * ```kotlin
 * val adView = InlineAdView(context)
 * adView.bind(messageId = "a1", session = session)
 * parentLayout.addView(adView)
 * ```
 */
public class InlineAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Invoked when the ad's height changes to a non-zero value (CSS px,
     * 1:1 with dp). The [ComposeView] already sizes itself to the ad, so
     * the host usually resizes automatically — this is a convenience hook
     * for publishers that need to react to the size change explicitly.
     */
    public var onHeightChange: ((Float) -> Unit)? = null

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    public fun bind(messageId: String, session: Session, code: String? = null, theme: String? = null) {
        val resolvedCode = code ?: Constants.DEFAULT_PLACEMENT_CODE
        // `createAd` is idempotent for a live (messageId, code, theme) — this
        // returns the same Ad the InlineAd composable resolves internally, so
        // we can observe its height for `onHeightChange` without a 2nd Ad.
        val ad = session.createAd(messageId, AdOptions(code = resolvedCode, theme = theme))

        composeView.setContent {
            InlineAd(messageId = messageId, session = session, code = resolvedCode, theme = theme)

            LaunchedEffect(ad) {
                snapshotFlow { ad.height }
                    .distinctUntilChanged()
                    .collect { height -> if (height > 0f) onHeightChange?.invoke(height) }
            }
        }
    }
}
