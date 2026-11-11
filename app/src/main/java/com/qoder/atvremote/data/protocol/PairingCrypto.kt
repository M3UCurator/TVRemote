package com.qoder.atvremote.data.protocol

import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class ProtocolException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PairingRejectedException(message: String) : IOException(message)

class PinMismatchException : IOException("The pairing code does not match the TV's certificate")

/**
 * The TV derives the pairing secret as:
 *
 * SHA-256(clientModulus ‖ clientExponent ‖ serverModulus ‖ serverExponent ‖ pin[2..6])
 *
 * where each RSA value is the minimal unsigned big-endian encoding, and the first byte of the
 * digest must equal the first byte encoded by the first two PIN characters.
 */
object PairingCrypto {

    fun isValidPin(pin: String): Boolean =
        pin.length == 6 && pin.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }

    fun computeSecret(pin: String, clientKey: RSAPublicKey, serverKey: RSAPublicKey): ByteArray {
        require(isValidPin(pin)) { "Pairing code must be six hexadecimal characters" }
        val normalized = pin.uppercase()

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsignedBytes(clientKey.modulus))
        digest.update(unsignedBytes(clientKey.publicExponent))
        digest.update(unsignedBytes(serverKey.modulus))
        digest.update(unsignedBytes(serverKey.publicExponent))
        digest.update(
            byteArrayOf(
                normalized.substring(2, 4).toInt(16).toByte(),
                normalized.substring(4, 6).toInt(16).toByte(),
            ),
        )
        val hash = digest.digest()

        val expectedPrefix = normalized.substring(0, 2).toInt(16)
        if ((hash[0].toInt() and 0xFF) != expectedPrefix) {
            throw PinMismatchException()
        }
        return hash
    }

    /** BigInteger.toByteArray() is two's complement; the protocol wants minimal unsigned bytes. */
    private fun unsignedBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
    }
}
