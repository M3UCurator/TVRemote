package com.qoder.atvremote.data.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class PairingCryptoTest {

    private fun rsaKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun unsigned(value: BigInteger): ByteArray =
        value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    private fun expectedHash(
        client: RSAPublicKey,
        server: RSAPublicKey,
        firstSuffixByte: Byte,
        secondSuffixByte: Byte,
    ): ByteArray = MessageDigest.getInstance("SHA-256").run {
        update(unsigned(client.modulus))
        update(unsigned(client.publicExponent))
        update(unsigned(server.modulus))
        update(unsigned(server.publicExponent))
        update(byteArrayOf(firstSuffixByte, secondSuffixByte))
        digest()
    }

    @Test
    fun `valid pin is accepted`() {
        assertTrue(PairingCrypto.isValidPin("A1B2C3"))
        assertTrue(PairingCrypto.isValidPin("000000"))
        assertTrue(PairingCrypto.isValidPin("abcdef"))
    }

    @Test
    fun `malformed pins are rejected`() {
        assertFalse(PairingCrypto.isValidPin(""))
        assertFalse(PairingCrypto.isValidPin("A1B2C"))
        assertFalse(PairingCrypto.isValidPin("A1B2C3D"))
        assertFalse(PairingCrypto.isValidPin("A1B2CG"))
        assertFalse(PairingCrypto.isValidPin("A1 2C3"))
    }

    @Test
    fun `secret matches sha256 over both public keys and pin suffix`() {
        val client = rsaKeyPair().public as RSAPublicKey
        val server = rsaKeyPair().public as RSAPublicKey

        val suffixFirst = 0x00.toByte()
        val suffixSecond = 0x00.toByte()
        val hash = expectedHash(client, server, suffixFirst, suffixSecond)
        val pin = "%02X%02X%02X".format(hash[0].toInt() and 0xFF, 0, 0)

        val secret = PairingCrypto.computeSecret(pin, client, server)

        assertArrayEquals(hash, secret)
    }

    @Test
    fun `secret is returned in full even when the pin has leading zeros`() {
        val client = rsaKeyPair().public as RSAPublicKey
        val server = rsaKeyPair().public as RSAPublicKey

        val hash = expectedHash(client, server, 0x12, 0x34)
        val pin = "%02X1234".format(hash[0].toInt() and 0xFF)

        val secret = PairingCrypto.computeSecret(pin, client, server)

        assertArrayEquals(hash, secret)
        assertTrue(secret.size == 32)
    }

    @Test(expected = PinMismatchException::class)
    fun `mismatched prefix is rejected`() {
        val client = rsaKeyPair().public as RSAPublicKey
        val server = rsaKeyPair().public as RSAPublicKey

        val hash = expectedHash(client, server, 0x00, 0x00)
        val wrongPrefix = ((hash[0].toInt() and 0xFF) + 1) and 0xFF
        val pin = "%02X0000".format(wrongPrefix)

        PairingCrypto.computeSecret(pin, client, server)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid pin is rejected before hashing`() {
        val client = rsaKeyPair().public as RSAPublicKey
        val server = rsaKeyPair().public as RSAPublicKey

        PairingCrypto.computeSecret("XYZ", client, server)
    }
}
