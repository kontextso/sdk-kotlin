package com.kontext.ads

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ErrorBody(
    val error: String,
    val additionalData: JsonObject? = null,
)
