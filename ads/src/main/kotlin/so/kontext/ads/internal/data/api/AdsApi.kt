package so.kontext.ads.internal.data.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.dto.response.PreloadResponse

internal interface AdsApi {

    @POST("preload")
    suspend fun preload(@Body body: PreloadRequest): PreloadResponse

    @POST("error")
    suspend fun reportError(@Body body: ErrorRequest)
}
