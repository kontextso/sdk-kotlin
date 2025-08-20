package so.kontext.ads.internal.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.dto.response.PreloadResponse

internal class AdsApiImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : AdsApi {

    override suspend fun preload(body: PreloadRequest): PreloadResponse {
        return httpClient.post("$baseUrl/preload") {
            setBody(body)
        }.body()
    }

    override suspend fun reportError(body: ErrorRequest) {
        httpClient.post("$baseUrl/error") {
            setBody(body)
        }
    }
}
