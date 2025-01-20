package dev.monosoul.cookiemover

import java.io.File
import java.net.URI
import java.time.Clock
import java.util.logging.LogManager

object App {

    init {
        LogManager.getLogManager()
            .readConfiguration(App::class.java.getResourceAsStream("/logging.properties"))
    }

    private val CLOCK = Clock.systemUTC()

    private fun dataDir() = File(System.getProperty("user.home"), "Library/Application Support/Google")

    private fun String.toFile() = takeIf { it.isNotBlank() }?.let(::File)

    @JvmStatic
    fun main(args: Array<String>) {

        val input = ExtensionIOHandler.readInput(System.`in`)

        val chromeDataDirPath = input.chromeDataDirPath.toFile() ?: File(dataDir(), "Chrome")
        val appDataDirPath = input.appDataDirPath.toFile() ?: File(dataDir(), "Cookiemover")
        val chromeExecPath =
            input.chromeExecPath.toFile() ?: File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
        val url = URI(input.url)
        val authDomain = input.authDomain
        require(authDomain.isNotBlank()) { "Auth domain cannot be blank" }

        val targetUrl = ChromeRunner(url, authDomain, chromeExecPath, chromeDataDirPath, appDataDirPath, CLOCK)
            .runChrome()
        val cookieStorePath = File(appDataDirPath, "Default/Cookies")

        val output = Output(
            targetUrl = "$targetUrl",
            cookies = CookiesReader(cookieStorePath, CLOCK).readCookies(targetUrl),
        )

        ExtensionIOHandler.writeOutput(System.out, output)
    }
}
