package com.kontext.ads.internal.data.repository

import com.kontext.ads.AdsConfig
import com.kontext.ads.domain.AdConfig
import com.kontext.ads.domain.ChatMessage
import com.kontext.ads.domain.DeviceInfo
import com.kontext.ads.internal.utils.ApiResponse
import java.io.Closeable

internal interface AdsRepository : Closeable {

    suspend fun preload(
        sessionId: String?,
        messages: List<ChatMessage>,
        deviceInfo: DeviceInfo,
        adsConfig: AdsConfig,
    ): ApiResponse<List<AdConfig>?>
    suspend fun reportError(
        message: String,
        additionalData: String? = null,
    ): ApiResponse<Unit>
}
