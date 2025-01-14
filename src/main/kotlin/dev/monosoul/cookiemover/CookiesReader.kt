package dev.monosoul.cookiemover

import org.jetbrains.exposed.sql.Database
import org.sqlite.SQLiteDataSource
import java.io.File
import java.net.URI
import java.time.Clock

class CookiesReader(
    private val cookieDAO: CookieDAO,
    private val clock: Clock,
) {
    constructor(cookieStorePath: File, clock: Clock) : this(
        cookieDAO = CookieDAO(
            db = Database.connect(
                datasource = SQLiteDataSource().apply {
                    this.url = "jdbc:sqlite:${cookieStorePath.absolutePath}"
                    this.databaseName = "main"
                }
            ),
            cookieDecryptor = ChromeCookieDecryptor()
        ),
        clock = clock,
    )

    fun readCookies(url: URI): List<Cookie> {
        require(
            cookieDAO.countAllForHostAndExpirationDateGreaterThanOrEq(url.host, clock.instant()) > 0
        ) {
            "Should have cookies expiring in the future"
        }

        return cookieDAO.getAllForHost(url.host)
    }
}