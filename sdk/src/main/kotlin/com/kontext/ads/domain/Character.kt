package com.kontext.ads.domain

public data class Character(
    val id: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val isNsfw: Boolean? = null,
    val greeting: String? = null,
    val persona: String? = null,
    val tags: List<String>? = null,
)
