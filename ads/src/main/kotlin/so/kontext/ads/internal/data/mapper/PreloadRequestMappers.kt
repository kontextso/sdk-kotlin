package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.internal.AdsConfiguration
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.data.dto.request.PreloadRequest
import so.kontext.ads.internal.utils.deviceinfo.DeviceInfo

internal fun createPreloadRequest(
    adsConfiguration: AdsConfiguration,
    deviceInfo: DeviceInfo,
    sessionId: String?,
    messages: List<ChatMessage>,
): PreloadRequest {
    val messagesDto = messages
        .takeLast(AdsProperties.NumberOfMessages)
        .map { it.toDto() }

    return PreloadRequest(
        publisherToken = adsConfiguration.publisherToken,
        conversationId = adsConfiguration.conversationId,
        userId = adsConfiguration.userId,
        regulatory = adsConfiguration.regulatory?.toDto(),
        variantId = adsConfiguration.variantId,
        character = adsConfiguration.character?.toDto(),
        advertisingId = adsConfiguration.advertisingId,
        enabledPlacementCodes = adsConfiguration.enabledPlacementCodes,
        device = deviceInfo.toDto(),
        sdk = deviceInfo.sdkInfo.toDto(),
        app = deviceInfo.appInfo.toDto(),
        messages = messagesDto,
        sessionId = sessionId,
    )
}
