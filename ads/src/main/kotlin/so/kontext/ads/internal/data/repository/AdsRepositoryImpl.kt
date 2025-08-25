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
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.api.AdsApiImpl
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.mapper.toDomain
import so.kontext.ads.internal.data.mapper.toDto
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
                    android.util.Log.d("Ktor", message)
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
        val messagesDto = messages
            .takeLast(AdsProperties.NumberOfMessages)
            .map { it.toDto() }

        val preloadRequest = PreloadRequest(
            publisherToken = adsConfiguration.publisherToken,
            conversationId = adsConfiguration.conversationId,
            userId = adsConfiguration.userId,
            messages = messagesDto,
            device = deviceInfo.toDto(),
            regulatory = adsConfiguration.regulatory?.toDto(),
            variantId = adsConfiguration.variantId,
            character = adsConfiguration.character?.toDto(),
            advertisingId = adsConfiguration.advertisingId,
            vendorId = adsConfiguration.vendorId,
            sessionId = sessionId,
            sdk = AdsProperties.SdkName,
            sdkVersion = sdkVersion,
        )

        val response = withApiCall {
            adsApi.preload(body = preloadRequest)
        }

        return when (response) {
            is ApiResponse.Error -> {
                reportError("Preload failed", response.error.cause?.stackTraceToString())
                ApiResponse.Error(response.error)
            }
            is ApiResponse.Success -> {
                val data = response.data

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
