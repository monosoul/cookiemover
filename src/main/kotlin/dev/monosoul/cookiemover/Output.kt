package dev.monosoul.cookiemover

import kotlinx.serialization.Serializable

@Serializable
data class Output(
    val targetUrl: String,
    val cookies: List<Cookie>,
)