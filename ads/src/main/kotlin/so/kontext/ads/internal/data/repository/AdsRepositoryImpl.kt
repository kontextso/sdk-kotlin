package so.kontext.ads.internal.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.api.AdsApiImpl
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.mapper.createPreloadRequest
import so.kontext.ads.internal.data.mapper.toDomain
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.withApiCall

internal class AdsRepositoryImpl(
    adServerUrl: String,
) : AdsRepository {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    android.util.Log.d("Kontext SDK Ktor", message)
                }
            }
            level = LogLevel.ALL
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }

    private val adsApi: AdsApi = AdsApiImpl(
        httpClient = httpClient,
        baseUrl = adServerUrl,
    )

    override suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfiguration: AdsConfiguration,
        sdkVersion: String,
    ): ApiResponse<PreloadResult> {
        val preloadRequest = createPreloadRequest(
            adsConfiguration = adsConfiguration,
            deviceInfo = deviceInfo,
            sessionId = sessionId,
            messages = messages,
        )

        val apiResponse = withApiCall {
            adsApi.preload(body = preloadRequest)
        }

        return when (apiResponse) {
            is ApiResponse.Error -> {
                reportError("Preload failed", apiResponse.error.cause?.stackTraceToString())
                ApiResponse.Error(apiResponse.error)
            }
            is ApiResponse.Success -> {
                val data = apiResponse.data

                if (data.errCode != null) {
                    val error = when (data.permanent) {
                        true -> ApiError.PermanentError(code = data.errCode)
                        false, null -> ApiError.TemporaryError(code = data.errCode)
                    }
                    return ApiResponse.Error(error)
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
