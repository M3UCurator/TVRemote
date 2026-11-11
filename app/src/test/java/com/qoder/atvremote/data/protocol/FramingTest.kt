package com.qoder.atvremote.data.protocol

import com.google.polo.wire.protobuf.PoloProto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

class FramingTest {

    private fun message(clientName: String): PoloProto.OuterMessage =
        PoloProto.OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
            .setPairingRequest(
                PoloProto.PairingRequest.newBuilder()
                    .setServiceName("atvremote")
                    .setClientName(clientName),
            )
            .build()

    @Test
    fun `small message uses a single byte length prefix`() {
        val message = message("ATV Remote")
        val output = ByteArrayOutputStream()
        output.writeDelimited(message)

        val bytes = output.toByteArray()
        assertEquals(message.serializedSize, bytes[0].toInt() and 0x7F)

        val decoded = ByteArrayInputStream(bytes).readDelimited()
        assertArrayEquals(message.toByteArray(), decoded)
    }

    @Test
    fun `multi byte varint prefix round trips`() {
        val message = message("x".repeat(300))
        assertTrue(message.serializedSize > 127)

        val output = ByteArrayOutputStream()
        output.writeDelimited(message)

        val bytes = output.toByteArray()
        assertTrue("length prefix should need two bytes", bytes[0].toInt() and 0x80 != 0)

        val decoded = ByteArrayInputStream(bytes).readDelimited()
        assertArrayEquals(message.toByteArray(), decoded)
    }

    @Test
    fun `streamed messages are read back in order`() {
        val first = message("first")
        val second = message("second")

        val output = ByteArrayOutputStream()
        output.writeDelimited(first)
        output.writeDelimited(second)

        val input = ByteArrayInputStream(output.toByteArray())
        assertArrayEquals(first.toByteArray(), input.readDelimited())
        assertArrayEquals(second.toByteArray(), input.readDelimited())
        assertNull(input.readDelimited())
    }

    @Test(expected = EOFException::class)
    fun `truncated length prefix is rejected`() {
        ByteArrayInputStream(byteArrayOf(0x80.toByte())).readDelimited()
    }

    @Test(expected = EOFException::class)
    fun `truncated payload is rejected`() {
        ByteArrayInputStream(byteArrayOf(0x05, 0x01, 0x02)).readDelimited()
    }

    @Test(expected = IOException::class)
    fun `oversized message is rejected before reading the payload`() {
        // varint(4 MiB + 1)
        ByteArrayInputStream(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x80.toByte(), 0x02))
            .readDelimited()
    }

    @Test
    fun `empty stream signals end of stream`() {
        assertNull(ByteArrayInputStream(ByteArray(0)).readDelimited())
    }
}
