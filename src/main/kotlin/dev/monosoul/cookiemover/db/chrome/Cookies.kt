package dev.monosoul.cookiemover.db.chrome

import org.jetbrains.exposed.sql.Table

object Cookies : Table("cookies") {
    val creationUtc = long("creation_utc")
    val hostKey = text("host_key")
    val topFrameSiteKey = text("top_frame_site_key")
    val name = text("name")
    val value = text("value")
    val encryptedValue = blob("encrypted_value")
    val path = text("path")
    val expiresUtc = long("expires_utc")
    val isSecure = integer("is_secure")
    val isHttponly = integer("is_httponly")
    val lastAccessUtc = long("last_access_utc")
    val hasExpires = integer("has_expires")
    val isPersistent = integer("is_persistent")
    val priority = integer("priority")
    val sameSite = integer("samesite")
    val sourceScheme = integer("source_scheme")
    val sourcePort = integer("source_port")
    val lastUpdateUtc = long("last_update_utc")
    val sourceType = integer("source_type")
    val hasCrossSiteAncestor = integer("has_cross_site_ancestor")
}