package com.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatMessageDto(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("createdAt") val createdAt: String,
)
