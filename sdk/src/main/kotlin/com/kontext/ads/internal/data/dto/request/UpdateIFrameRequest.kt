package com.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateIFrameRequest(
    @SerialName("type") val type: String,
    @SerialName("code") val code: String,
    @SerialName("data") val data: UpdateIFrameDataDto,
)

@Serializable
internal data class UpdateIFrameDataDto(
    @SerialName("messages") val messages: List<ChatMessageDto>,
    @SerialName("messageId") val messageId: String,
    @SerialName("sdk") val sdk: String,
    @SerialName("otherParams") val otherParams: Map<String, String>,
)
