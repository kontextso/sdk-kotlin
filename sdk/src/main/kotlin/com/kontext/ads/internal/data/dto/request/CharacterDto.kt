package com.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CharacterDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("isNsfw") val isNsfw: Boolean? = null,
    @SerialName("greeting") val greeting: String? = null,
    @SerialName("persona") val persona: String? = null,
    @SerialName("tags") val tags: List<String>? = null,
)
