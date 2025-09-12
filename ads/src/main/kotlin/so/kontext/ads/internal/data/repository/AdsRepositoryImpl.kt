package so.kontext.ads.internal.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.data.api.AdsApi
import so.kontext.ads.internal.data.dto.request.ErrorRequest
import so.kontext.ads.internal.data.error.ApiError
import so.kontext.ads.internal.data.mapper.createPreloadRequest
import so.kontext.ads.internal.data.mapper.toDomain
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import so.kontext.ads.internal.utils.withApiCall

internal class AdsRepositoryImpl(
    private val adsApi: AdsApi,
) : AdsRepository {

    override suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfiguration: AdsConfiguration,
        timeout: Long,
    ): ApiResponse<PreloadResult> {
        val preloadRequest = createPreloadRequest(
            adsConfiguration = adsConfiguration,
            deviceInfo = deviceInfo,
            sessionId = sessionId,
            messages = messages,
        )

        val apiResponse = withApiCall {
            adsApi.preload(
                body = preloadRequest,
                timeout = timeout,
            )
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
}
