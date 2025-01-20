package dev.monosoul.cookiemover

import kotlinx.serialization.Serializable

@Serializable
data class Input(
    val url: String,
    val authDomain: String = "okta.com",
    val existingCookies: List<Cookie> = emptyList(),
    val chromeDataDirPath: String = "",
    val appDataDirPath: String = "",
    val chromeExecPath: String = "",
)