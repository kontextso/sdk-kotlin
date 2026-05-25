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
 * Thin wrapper around a [ComposeView] that hosts the Compose [InlineAd], so
 * the View path and the Compose path share one rendering implementation
 * (WebViewPool reuse, dimension reporting, OMID lifecycle) and can't diverge.
 * `DisposeOnDetachedFromWindow` means a RecyclerView recycle tears down only
 * the composition, not the pooled WebView or the Session-owned Ad — re-binding
 * reattaches the same WebView without reloading the iframe.
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
     * Invoked when the ad's height changes to a non-zero value (CSS px, 1:1
     * with dp). The [ComposeView] already sizes itself to the ad, so the host
     * usually resizes automatically — this is a convenience hook for hosts
     * (e.g. a RecyclerView item) that need to react to the size change.
     */
    public var onHeightChange: ((Float) -> Unit)? = null

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Binds the ad slot to [messageId] in [session] and renders its ad. */
    public fun bind(messageId: String, session: Session, code: String? = null, theme: String? = null) {
        val resolvedCode = code ?: Constants.DEFAULT_PLACEMENT_CODE
        // createAd is idempotent for a live (messageId, code, theme) — this is
        // the same Ad the InlineAd composable resolves, so we can observe its
        // height for onHeightChange without creating a second instance.
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

    /**
     * Clears the ad slot — disposes the hosted composition (pausing the
     * WebView) and shows nothing. The v4 equivalent of v2.0.1's
     * `setConfig(null)`. Call this for a recycled / no-longer-active slot
     * (e.g. when a newer message takes over the ad).
     */
    public fun unbind() {
        composeView.setContent { }
    }
}
