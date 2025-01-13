package dev.monosoul.cookiemover

import kotlinx.serialization.Serializable

@Serializable
data class Input(
    val url: String,
    val chromeDataDirPath: String? = null,
    val appDataDirPath: String? = null,
    val chromeExecPath: String? = null,
)