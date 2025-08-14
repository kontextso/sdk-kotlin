package com.kontext.ads.internal.data.api

import com.kontext.ads.internal.data.dto.request.ErrorRequest
import com.kontext.ads.internal.data.dto.request.PreloadRequest
import com.kontext.ads.internal.data.dto.response.PreloadResponse
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST

internal interface AdsApi {

    @POST("preload")
    suspend fun preload(@Body body: PreloadRequest): PreloadResponse

    @POST("error")
    suspend fun reportError(@Body body: ErrorRequest)
}
