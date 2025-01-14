package dev.monosoul.cookiemover

import dev.monosoul.cookiemover.db.chrome.Cookies
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class CookieDAO(
    private val db: Database,
    private val cookieDecryptor: ChromeCookieDecryptor,
) {
    fun countAllForHostAndExpirationDateGreaterThanOrEq(host: String, expirationDate: Instant): Long = transaction(db) {
        Cookies.select(Cookies.hostKey.count())
            .where(
                Cookies.hostKey.like("%$host%")
                    .and(
                        Cookies.expiresUtc.greaterEq(expirationDate.chromeEpochSeconds)
                    )
            )
            .single()[Cookies.hostKey.count()]
    }

    fun getAllForHost(host: String): List<Cookie> = transaction(db) {
        Cookies.selectAll()
            .where(Cookies.hostKey.like("%$host%"))
            .map { record ->
                val encryptedValue = record[Cookies.encryptedValue].bytes
                val value = if (encryptedValue.isNotEmpty()) {
                    cookieDecryptor.decrypt(encryptedValue).decodeToString()
                } else {
                    record[Cookies.value]
                }

                Cookie(
                    domain = record[Cookies.hostKey],
                    expirationDate = record[Cookies.expiresUtc]
                        .takeUnless { it == 0L }
                        ?.toUnixEpochSeconds(),
                    name = record[Cookies.name],
                    path = record[Cookies.path],
                    httpOnly = record[Cookies.isHttponly].toBoolean(),
                    secure = record[Cookies.isSecure].toBoolean(),
                    sameSite = when (record[Cookies.sameSite]) {
                        0 -> "no_restriction"
                        1 -> "lax"
                        2 -> "strict"
                        else -> "unspecified"
                    },
                    hostOnly = !record[Cookies.hostKey].startsWith("."),
                    storeId = "0",
                    session = !record[Cookies.isPersistent].toBoolean(),
                    value = value
                )
            }
    }

    private fun Int.toBoolean() = when (this) {
        0 -> false
        1 -> true
        else -> throw IllegalArgumentException("Integer out of range: $this")
    }
}