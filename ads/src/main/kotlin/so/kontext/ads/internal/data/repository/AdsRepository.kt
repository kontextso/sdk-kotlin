package so.kontext.ads.internal.data.repository

import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.PreloadResult
import so.kontext.ads.internal.AdsConfig
import so.kontext.ads.internal.utils.ApiResponse
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo
import java.io.Closeable

internal interface AdsRepository : Closeable {

    suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfig: AdsConfig,
    ): ApiResponse<PreloadResult>
    suspend fun reportError(
        message: String,
        additionalData: String? = null,
    ): ApiResponse<Unit>
}
