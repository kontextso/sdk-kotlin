package so.kontext.ads.internal.data.api

import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.dto.response.PreloadResponse

internal interface AdsApi {

    suspend fun preload(body: PreloadRequest): PreloadResponse

    suspend fun reportError(body: ErrorRequest)
}
