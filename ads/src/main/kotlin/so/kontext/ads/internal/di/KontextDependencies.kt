package so.kontext.ads.internal.di

import kotlinx.coroutines.flow.MutableSharedFlow
import so.kontext.ads.internal.ui.IFrameEventParser
import so.kontext.ads.ui.AdEvent

internal object KontextDependencies {

    /**
     * A shared flow to broadcast events from modal ads back to any interested collectors.
     */
    val modalAdEvents = MutableSharedFlow<AdEvent>()

    val iFrameEventParser: IFrameEventParser by lazy {
        IFrameEventParser()
    }
}
