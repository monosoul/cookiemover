package dev.monosoul.cookiemover

import java.time.Instant

private const val MICROSECONDS_IN_ONE_SECOND = 1000000L
private val MICROSECONDS_IN_ONE_SECOND_BD = MICROSECONDS_IN_ONE_SECOND.toBigDecimal()
private const val CHROME_EPOCH_DIFF = 11644473600L
private val CHROME_EPOCH_DIFF_BD = CHROME_EPOCH_DIFF.toBigDecimal()

fun Long.toChromeEpochSeconds() = (this + CHROME_EPOCH_DIFF) * MICROSECONDS_IN_ONE_SECOND
fun Long.toUnixEpochSeconds() = toBigDecimal().divide(MICROSECONDS_IN_ONE_SECOND_BD) - CHROME_EPOCH_DIFF_BD

val Instant.chromeEpochSeconds: Long
    get() = epochSecond.toChromeEpochSeconds()