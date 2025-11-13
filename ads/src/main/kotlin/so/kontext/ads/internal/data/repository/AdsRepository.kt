package so.kontext.ads.internal.data.repository

import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo

internal interface AdsRepository {

    @Suppress("LongParameterList")
    suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfiguration: AdsConfiguration,
        timeout: Long,
        isDisabled: Boolean,
    ): ApiResponse<PreloadResult>
    suspend fun reportError(
        message: String,
        additionalData: String? = null,
    ): ApiResponse<Unit>
}
