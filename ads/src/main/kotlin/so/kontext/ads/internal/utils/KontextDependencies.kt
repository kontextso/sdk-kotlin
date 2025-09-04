package so.kontext.ads.internal.utils

import so.kontext.ads.internal.ui.IFrameEventParser

internal object KontextDependencies {

    val iFrameEventParser: IFrameEventParser by lazy {
        IFrameEventParser()
    }
}
