package so.kontext.ads.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import so.kontext.ads.domain.AdConfig

public class InlineAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentConfig: AdConfig? = null

    /**
     * A listener that gets invoked when an ad-related event occurs.
     */
    public var onAdEventListener: ((AdEvent) -> Unit)? = null

    private val composeView = ComposeView(context).apply {
        id = generateViewId()
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    init {
        addView(composeView)
    }

    public fun setConfig(config: AdConfig?) {
        currentConfig = config
        isVisible = config != null
        render()
    }

    private fun render() {
        val config = currentConfig
        composeView.setContent {
            if (config != null) {
                InlineAd(
                    config = config,
                    modifier = Modifier.fillMaxWidth(),
                    onEvent = { event ->
                        onAdEventListener?.invoke(event)
                    },
                )
            }
        }
    }
}
