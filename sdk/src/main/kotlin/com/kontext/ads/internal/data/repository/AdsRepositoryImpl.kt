package com.kontext.ads.internal.data.repository

import com.kontext.ads.domain.AdConfig
import com.kontext.ads.domain.ChatMessage
import com.kontext.ads.internal.AdsConfig
import com.kontext.ads.internal.AdsProperties
import com.kontext.ads.internal.data.api.AdsApi
import com.kontext.ads.internal.data.api.createAdsApi
import com.kontext.ads.internal.data.dto.request.ErrorRequest
import com.kontext.ads.internal.data.dto.request.PreloadRequest
import com.kontext.ads.internal.data.dto.response.BidDto
import com.kontext.ads.internal.data.error.ApiError
import com.kontext.ads.internal.data.mapper.toDomain
import com.kontext.ads.internal.data.mapper.toDto
import com.kontext.ads.internal.utils.ApiResponse
import com.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import com.kontext.ads.internal.utils.withApiCall
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
        .baseUrl(adServerUrl)
        .httpClient(httpClient)
        .build()

    private val adsApi: AdsApi = ktorfit.createAdsApi()

    override suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfig: AdsConfig,
    ): ApiResponse<List<AdConfig>?> {
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
                val adConfigs = getAdConfigs(
                    messages = messages,
                    bids = data.bids,
                    theme = adsConfig.theme
                )

                ApiResponse.Success(adConfigs)
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

    private fun getAdConfigs(
        messages: List<ChatMessage>,
        bids: List<BidDto>?,
        theme: String?,
    ): List<AdConfig>? {
        val lastMessage = messages.lastOrNull() ?: return null

        return bids?.map { bid ->
            val iframeUrl = AdsProperties.iframeUrl(
                baseUrl = adServerUrl,
                bidId = bid.bidId,
                bidCode = bid.code,
                messageId = lastMessage.id,
            )
            AdConfig(
                url = iframeUrl,
                messages = messages.takeLast(AdsProperties.NumberOfMessages),
                messageId = lastMessage.id,
                sdk = AdsProperties.SdkName,
                otherParams = theme?.let { mapOf("theme" to it) } ?: emptyMap(),
                bid = bid.toDomain(),
            )
        }
    }

    override fun close() {
        httpClient.close()
    }
}
