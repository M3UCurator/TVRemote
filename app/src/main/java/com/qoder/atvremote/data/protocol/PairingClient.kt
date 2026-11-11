package com.qoder.atvremote.data.protocol

import com.google.polo.wire.protobuf.PoloProto
import com.google.protobuf.ByteString
import com.qoder.atvremote.data.security.CertificateStore
import com.qoder.atvremote.data.security.TlsClientFactory
import com.qoder.atvremote.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.interfaces.RSAPublicKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * Android TV Remote v2 pairing (service `_androidtvremote2._tcp`, port 6467).
 *
 * Handshake:
 *   client PairingRequest  ->  server PairingRequestAck
 *   client Options         ->  server Options
 *   client Configuration   ->  server ConfigurationAck (TV now shows the PIN)
 *   client Secret          ->  server SecretAck
 */
class PairingClient(
    private val device: TvDevice,
    private val tlsClientFactory: TlsClientFactory,
    private val certificateStore: CertificateStore,
    private val clientName: String = CLIENT_NAME,
) {
    private val mutex = Mutex()
    @Volatile
    private var socket: SSLSocket? = null

    /** Connects and advances the handshake until the TV displays its pairing code. */
    suspend fun start() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (socket != null) return@withLock

            val session = tlsClientFactory.connect(
                host = device.host,
                port = device.pairingPort,
                readTimeoutMillis = IO_TIMEOUT_MS,
            )
            socket = session
            try {
                session.send(
                    message {
                        pairingRequest = PoloProto.PairingRequest.newBuilder()
                            .setServiceName(SERVICE_NAME)
                            .setClientName(clientName)
                            .build()
                    },
                )
                awaitPinPrompt(session)
            } catch (t: Throwable) {
                closeQuietly()
                throw t.normalized()
            }
        }
    }

    /** Sends the derived secret for the code shown on the TV and waits for confirmation. */
    suspend fun submitPin(pin: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val session = socket ?: throw ProtocolException("The pairing session is no longer open.")
            val normalized = pin.trim().uppercase()
            require(PairingCrypto.isValidPin(normalized)) {
                "The pairing code must be six hexadecimal characters"
            }

            try {
                val identity = certificateStore.get()
                val clientKey = identity.certificate.publicKey as? RSAPublicKey
                    ?: throw ProtocolException("The client certificate does not contain an RSA key.")
                val serverCertificate = session.session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: throw ProtocolException("The TV did not present a certificate.")
                val serverKey = serverCertificate.publicKey as? RSAPublicKey
                    ?: throw ProtocolException("The TV certificate does not contain an RSA key.")

                val secret = PairingCrypto.computeSecret(normalized, clientKey, serverKey)
                session.send(
                    message {
                        this.secret = PoloProto.Secret.newBuilder()
                            .setSecret(ByteString.copyFrom(secret))
                            .build()
                    },
                )
                awaitSecretAck(session)
            } catch (t: Throwable) {
                throw t.normalized()
            } finally {
                closeQuietly()
            }
        }
    }

    fun close() = closeQuietly()

    private fun awaitPinPrompt(session: SSLSocket) {
        while (true) {
            val incoming = session.receive()
            if (incoming.status != PoloProto.OuterMessage.Status.STATUS_OK) {
                throw PairingRejectedException("The TV rejected the pairing request (${incoming.status}).")
            }
            when {
                incoming.hasPairingRequestAck() -> session.send(optionsMessage())
                incoming.hasOptions() -> session.send(configurationMessage())
                incoming.hasConfigurationAck() -> return
                incoming.hasSecretAck() -> return
                else -> Unit
            }
        }
    }

    private fun awaitSecretAck(session: SSLSocket) {
        while (true) {
            val incoming = session.receive()
            if (incoming.status != PoloProto.OuterMessage.Status.STATUS_OK) {
                throw PairingRejectedException("The TV rejected the pairing code (${incoming.status}).")
            }
            if (incoming.hasSecretAck()) return
        }
    }

    private fun optionsMessage(): PoloProto.OuterMessage = message {
        options = PoloProto.Options.newBuilder()
            .setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
            .addInputEncodings(encoding())
            .build()
    }

    private fun configurationMessage(): PoloProto.OuterMessage = message {
        configuration = PoloProto.Configuration.newBuilder()
            .setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
            .setEncoding(encoding())
            .build()
    }

    private fun encoding(): PoloProto.Options.Encoding = PoloProto.Options.Encoding.newBuilder()
        .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
        .setSymbolLength(PIN_LENGTH)
        .build()

    private fun message(block: PoloProto.OuterMessage.Builder.() -> Unit): PoloProto.OuterMessage =
        PoloProto.OuterMessage.newBuilder()
            .setProtocolVersion(PROTOCOL_VERSION)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
            .apply(block)
            .build()

    private fun SSLSocket.send(message: PoloProto.OuterMessage) {
        outputStream.writeDelimited(message)
    }

    private fun SSLSocket.receive(): PoloProto.OuterMessage {
        val payload = inputStream.readDelimited()
            ?: throw ProtocolException("The TV closed the pairing connection.")
        return PoloProto.OuterMessage.parseFrom(payload)
    }

    private fun closeQuietly() {
        val session = socket
        socket = null
        runCatching { session?.close() }
    }

    private fun Throwable.normalized(): Throwable = when (this) {
        is PinMismatchException, is PairingRejectedException, is ProtocolException -> this
        is SocketTimeoutException -> ProtocolException("The TV did not respond while pairing.", this)
        is SSLException -> ProtocolException("The secure pairing connection failed.", this)
        is IOException -> ProtocolException("The pairing connection was interrupted.", this)
        else -> this
    }

    private companion object {
        const val PROTOCOL_VERSION = 2
        const val PIN_LENGTH = 6
        const val SERVICE_NAME = "atvremote"
        const val CLIENT_NAME = "ATV Remote"
        const val IO_TIMEOUT_MS = 20_000
    }
}
