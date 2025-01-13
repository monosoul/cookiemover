package dev.monosoul.cookiemover

import dev.monosoul.cookiemover.db.chrome.Cookies
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import pt.davidafsilva.apple.OSXKeychain
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.time.Clock
import java.util.logging.LogManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.minutes

val CLOCK = Clock.systemUTC()
val JSON = Json {
    explicitNulls = false
}

val SALT = "saltysalt".encodeToByteArray()
const val KEY_LENGTH = 16
const val ITERATIONS = 1003
const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
const val PREFIX = 32
const val V10_PREFIX = "v10"
val IV = " ".repeat(KEY_LENGTH).encodeToByteArray()

val MICROSECONDS_IN_ONE_SECOND = 1000000L
val MICROSECONDS_IN_ONE_SECOND_BD = MICROSECONDS_IN_ONE_SECOND.toBigDecimal()
val CHROME_EPOCH_DIFF = 11644473600L
val CHROME_EPOCH_DIFF_BD = CHROME_EPOCH_DIFF.toBigDecimal()

fun readCookies(cookieStorePath: File, url: URI): List<Cookie> {
    val dataSource = SQLiteDataSource().apply {
        this.url = "jdbc:sqlite:${cookieStorePath.absolutePath}"
        this.databaseName = "main"
    }

    val db = Database.connect(dataSource)

    require(
        transaction(db) {
            Cookies.select(Cookies.hostKey.count())
                .where(
                    Cookies.hostKey.like("%${url.host}%")
                        .and(
                            Cookies.expiresUtc.greaterEq(CLOCK.currentChromeEpochSeconds())
                        )
                )
                .single()[Cookies.hostKey.count()]
        } > 0
    ) {
        "Should have cookies expiring in the future"
    }

    val chromePassword = OSXKeychain.getInstance()
        .findGenericPassword("Chrome Safe Storage", "Chrome")
        .get()

    val key = SecretKeyFactory
        .getInstance(PBKDF2_ALGORITHM)
        .generateSecret(
            PBEKeySpec(chromePassword.toCharArray(), SALT, ITERATIONS, KEY_LENGTH * 8)
        )
        .let {
            SecretKeySpec(it.encoded, "AES")
        }

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(IV))

    return transaction(db) {
        Cookies.selectAll()
            .where(Cookies.hostKey.like("%${url.host}%"))
            .map { record ->
                val encryptedValue = record[Cookies.encryptedValue].bytes
                val value = if (encryptedValue.isNotEmpty()) {
                    encryptedValue
                        .copyOfRange(V10_PREFIX.length, encryptedValue.size)
                        .let(cipher::doFinal)
                        .let { decrypted ->
                            decrypted.copyOfRange(PREFIX, decrypted.size)
                        }
                        .decodeToString()
                } else {
                    record[Cookies.value]
                }

                Cookie(
                    domain = record[Cookies.hostKey],
                    expirationDate = record[Cookies.expiresUtc]
                        .takeUnless { it == 0L }
                        ?.toBigDecimal()
                        ?.let { it.divide(MICROSECONDS_IN_ONE_SECOND_BD) - CHROME_EPOCH_DIFF_BD },
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
}

fun runChrome(execPath: File, url: URI, defaultDataDirPath: File, appDataDirPath: File) {
    if (!appDataDirPath.exists()) {
        FileUtils.copyDirectory(defaultDataDirPath, appDataDirPath) {
            !it.name.contains("Socket")
        }
    }

    val process = ProcessBuilder(
        execPath.absolutePath,
        "--user-data-dir=${appDataDirPath.absolutePath}",
        "--disable-features=InfiniteSessionRestore",
        "--hide-crash-restore-bubble",
        "--kiosk",
        "--enable-logging=stderr",
        "--v=1",
        "$url"
    )
        .start()

    runBlocking {
        withTimeout(10.minutes) {
            runInterruptible {
                var madeFirstUrlCall = false
                var authenticated = false

                process.errorStream.bufferedReader().use {
                    var line: String?
                    while (true) {
                        line = it.readLine()
                        if (line == null) break
                        // read Chrome log lines from network_delegate.cc and device_event_log_impl.cc
                        if (line.contains(":network_delegate.cc") || line.contains(":device_event_log_impl.cc")) {
                            // if hasn't made a first call to the URL yet and there's a log event for it, then authentication should come next
                            if (!madeFirstUrlCall && line.contains("$url")) {
                                madeFirstUrlCall = true
                                continue
                            }
                            // if the lines contains okta, then authentication happened most likely
                            if (madeFirstUrlCall && line.contains(".*okta.com".toRegex())) {
                                authenticated = true
                                continue
                            }
                            // if made a second call to the URL after authentication, then we're good to proceed to reading cookies
                            if (madeFirstUrlCall && authenticated && line.contains("$url")) {
                                process.destroy()
                                break
                            }
                        }
                    }
                }
            }
        }
    }
    process.waitFor()

    // cleanup sessions to prevent Chrome from restoring tabs
    val sessions = File(appDataDirPath, "Default/Sessions")
    sessions.deleteRecursively()
    sessions.mkdirs()
}

fun dataDir() = File(System.getProperty("user.home"), "Library/Application Support/Google")


private fun Int.toBoolean() = when (this) {
    0 -> false
    1 -> true
    else -> throw IllegalArgumentException("Integer out of range: $this")
}

private fun Clock.currentChromeEpochSeconds(): Long =
    (instant().epochSecond + CHROME_EPOCH_DIFF) * MICROSECONDS_IN_ONE_SECOND

object App {

    init {
        LogManager.getLogManager()
            .readConfiguration(App::class.java.getResourceAsStream("/logging.properties"))
    }

    @JvmStatic
    @OptIn(ExperimentalSerializationApi::class)
    fun main(args: Array<String>) {
        /**
         * Firefox sends messages prefixed with a 4-byte unsigned int to identify length
         * so we can't read it as a regular signed int, instead we'll read it as long and then
         * try to convert it to a signed int throwing an exception in case of overflow
         */
        val inputLength = ByteBuffer
            .wrap(
                System.`in`
                    .readNBytes(Int.SIZE_BYTES)
                    .reversedArray()
                    .copyInto(ByteArray(Long.SIZE_BYTES) { 0 }, Int.SIZE_BYTES)
            )
            .getLong()
            .let(Math::toIntExact)

        val input = JSON.decodeFromString<Input>(System.`in`.readNBytes(inputLength).decodeToString())

        val chromeDataDirPath = input.chromeDataDirPath?.let(::File) ?: File(dataDir(), "Chrome")
        val appDataDirPath = input.appDataDirPath?.let(::File) ?: File(dataDir(), "Cookiemover")
        val chromeExecPath =
            input.chromeExecPath?.let(::File) ?: File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
        val url = URI(input.url)

        runChrome(chromeExecPath, url, chromeDataDirPath, appDataDirPath)
        val cookieStorePath = File(appDataDirPath, "Default/Cookies")

        val jsonOutputArray = ByteArrayOutputStream().use { os ->
            JSON.encodeToStream(readCookies(cookieStorePath, url), os)
            os.flush()
            os.toByteArray()
        }
        val jsonOutputLength = jsonOutputArray.size

        val finalOutput = ByteBuffer
            .allocate(Int.SIZE_BYTES)
            .putInt(jsonOutputLength)
            .array()
            .reversedArray()
            .plus(jsonOutputArray)

        System.out.writeBytes(finalOutput)
        System.out.flush()
    }
}
