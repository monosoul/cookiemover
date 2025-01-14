package dev.monosoul.cookiemover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object ExtensionIOHandler {
    val JSON = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun readInput(inputStream: InputStream): Input {
        /**
         * Firefox sends messages prefixed with a 4-byte unsigned int to identify length
         * so we can't read it as a regular signed int, instead we'll read it as long and then
         * try to convert it to a signed int throwing an exception in case of overflow
         */
        val inputLength = ByteBuffer
            .wrap(
                inputStream
                    .readNBytes(Int.SIZE_BYTES)
                    .reversedArray()
                    .copyInto(ByteArray(Long.SIZE_BYTES) { 0 }, Int.SIZE_BYTES)
            )
            .getLong()
            .let(Math::toIntExact)

        return JSON.decodeFromString<Input>(inputStream.readNBytes(inputLength).decodeToString())
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun writeOutput(outputStream: OutputStream, output: Output) {
        val jsonOutputArray = ByteArrayOutputStream().use { os ->
            JSON.encodeToStream(output, os)
            os.flush()
            os.toByteArray()
        }

        val sizePrefix = ByteBuffer
            .allocate(Int.SIZE_BYTES)
            .putInt(jsonOutputArray.size)
            .array()
            .reversedArray()

        outputStream.write(sizePrefix + jsonOutputArray)
        outputStream.flush()
    }
}