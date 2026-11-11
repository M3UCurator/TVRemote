package com.qoder.atvremote.data.security

import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The Android TV box serves a self-signed certificate and authenticates clients by the
 * certificate they present, so server certificate verification is intentionally disabled while
 * our generated identity is supplied as the client chain.
 */
@Singleton
class TlsClientFactory @Inject constructor(
    private val certificateStore: CertificateStore,
) {
    @Volatile
    private var sslContext: SSLContext? = null

    fun connect(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMillis: Int = HANDSHAKE_TIMEOUT_MS,
    ): SSLSocket {
        val rawSocket = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(host, port), connectTimeoutMillis)
        }

        val socket = try {
            context().socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
        } catch (t: Throwable) {
            rawSocket.close()
            throw t
        }

        return try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            socket.startHandshake()
            socket.soTimeout = readTimeoutMillis
            socket
        } catch (t: Throwable) {
            socket.close()
            throw t
        }
    }

    private fun context(): SSLContext =
        sslContext ?: synchronized(LOCK) {
            sslContext ?: buildContext().also { sslContext = it }
        }

    private fun buildContext(): SSLContext {
        val identity = certificateStore.get()
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(identity.keyStore, identity.storePassword)

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, arrayOf(TrustAllManager), SecureRandom())
        }
    }

    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val HANDSHAKE_TIMEOUT_MS = 15_000
        val LOCK = Any()
    }
}
