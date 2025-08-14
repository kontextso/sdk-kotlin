package com.kontext.ads.domain

public data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val createdAt: String,
)
