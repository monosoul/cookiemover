package dev.monosoul.cookiemover

import pt.davidafsilva.apple.OSXKeychain
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val SALT = "saltysalt".encodeToByteArray()
private const val KEY_LENGTH = 16
private const val ITERATIONS = 1003
private const val PREFIX = 32
private const val V10_PREFIX = "v10"
private val IV = " ".repeat(KEY_LENGTH).encodeToByteArray()

class ChromeCookieDecryptor(
    private val cipher: Cipher = prepareCipher(),
) {
    fun decrypt(encryptedValue: ByteArray): ByteArray = encryptedValue
        .copyOfRange(V10_PREFIX.length, encryptedValue.size)
        .let(cipher::doFinal)
        .let { decrypted ->
            decrypted.copyOfRange(PREFIX, decrypted.size)
        }
}

private fun prepareCipher(): Cipher {
    val chromePassword = OSXKeychain.getInstance()
        .findGenericPassword("Chrome Safe Storage", "Chrome")
        .get()

    val key = SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA1")
        .generateSecret(
            PBEKeySpec(chromePassword.toCharArray(), SALT, ITERATIONS, KEY_LENGTH * 8)
        )
        .let {
            SecretKeySpec(it.encoded, "AES")
        }

    return Cipher.getInstance("AES/CBC/PKCS5Padding").also { cipher ->
        cipher.init(DECRYPT_MODE, key, IvParameterSpec(IV))
    }
}