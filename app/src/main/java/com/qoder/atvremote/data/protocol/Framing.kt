package com.qoder.atvremote.data.protocol

import com.google.protobuf.MessageLite
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Both pairing and command streams use protobuf's varint length-delimited framing:
 * a varint payload size followed by the serialized message.
 */
private const val MAX_MESSAGE_SIZE = 4 * 1024 * 1024

fun OutputStream.writeDelimited(message: MessageLite) {
    val payload = message.toByteArray()
    var size = payload.size
    while ((size and 0x7F.inv()) != 0) {
        write((size and 0x7F) or 0x80)
        size = size ushr 7
    }
    write(size)
    write(payload)
    flush()
}

fun InputStream.readDelimited(): ByteArray? {
    val first = read()
    if (first == -1) return null

    var length = first and 0x7F
    var shift = 7
    var current = first
    while ((current and 0x80) != 0) {
        current = read()
        if (current == -1) throw EOFException("Stream ended inside a message length prefix")
        if (shift > 28) throw IOException("Message length prefix is too long")
        length = length or ((current and 0x7F) shl shift)
        shift += 7
    }
    if (length > MAX_MESSAGE_SIZE) throw IOException("Message length $length exceeds the limit")
    return readExact(length)
}

private fun InputStream.readExact(length: Int): ByteArray {
    val buffer = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(buffer, offset, length - offset)
        if (read == -1) throw EOFException("Stream ended after $offset of $length bytes")
        offset += read
    }
    return buffer
}
