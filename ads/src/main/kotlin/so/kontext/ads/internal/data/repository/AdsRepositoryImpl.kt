package so.kontext.ads.internal.data.repository

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.AdsConfig
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.api.createAdsApi
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.mapper.toDomain
import so.kontext.ads.internal.data.mapper.toDto
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.withApiCall

internal class AdsRepositoryImpl(
    private val adServerUrl: String,
) : AdsRepository {

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }

    val ktorfit = Ktorfit.Builder()
        .baseUrl("$adServerUrl/")
        .httpClient(httpClient)
        .build()

    private val adsApi: AdsApi = ktorfit.createAdsApi()

    override suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfig: AdsConfig,
    ): ApiResponse<PreloadResult> {
        val messagesDto = messages
            .takeLast(AdsProperties.NumberOfMessages)
            .map { it.toDto() }

        val preloadRequest = PreloadRequest(
            publisherToken = adsConfig.publisherToken,
            conversationId = adsConfig.conversationId,
            userId = adsConfig.userId,
            messages = messagesDto,
            device = deviceInfo.toDto(),
            variantId = adsConfig.variantId,
            character = adsConfig.character?.toDto(),
            advertisingId = adsConfig.advertisingId,
            vendorId = adsConfig.vendorId,
            sessionId = sessionId,
            sdk = AdsProperties.SdkName,
            sdkVersion = "0.0.1", // TODO add sdk version
        )

        val response = withApiCall {
            adsApi.preload(body = preloadRequest)
        }

        return when (response) {
            is ApiResponse.Error -> {
                reportError("Preload failed", response.error.cause?.stackTraceToString())
                return ApiResponse.Error(response.error)
            }
            is ApiResponse.Success -> {
                val data = response.data

                if (data.errCode != null) {
                    when (data.permanent) {
                        true -> ApiError.PermanentError(code = data.errCode)
                        false, null -> ApiError.TemporaryError(code = data.errCode)
                    }
                }

                val preloadResult = PreloadResult(
                    bids = data.bids?.map { it.toDomain() },
                    sessionId = data.sessionId,
                    remoteLogLevel = data.remoteLogLevel,
                    preloadTimeout = data.preloadTimeout,
                )

                ApiResponse.Success(preloadResult)
            }
        }
    }

    override suspend fun reportError(
        message: String,
        additionalData: String?,
    ): ApiResponse<Unit> {
        val errorBody = ErrorRequest(
            error = message,
            additionalData = buildJsonObject {
                put("stacktrace", additionalData)
            },
        )

        return withApiCall {
            adsApi.reportError(body = errorBody)
        }
    }

    override fun close() {
        httpClient.close()
    }
}
