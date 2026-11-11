package com.qoder.atvremote.data.security

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists one client identity for the lifetime of the installation. The TV remembers this
 * certificate, so rotating it would force the user to pair again.
 */
@Singleton
class CertificateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cached: ClientIdentity? = null

    fun get(): ClientIdentity =
        cached ?: synchronized(LOCK) {
            cached ?: loadOrCreate().also { cached = it }
        }

    private fun loadOrCreate(): ClientIdentity {
        val file = File(context.filesDir, STORE_FILE)
        if (file.exists()) {
            val existing = runCatching { ClientCertificateFactory.load(file.readBytes()) }
                .onFailure { Log.w(TAG, "Discarding unreadable client identity", it) }
                .getOrNull()
            if (existing != null) return existing
        }

        val identity = ClientCertificateFactory.create(CLIENT_NAME)
        writeAtomically(file, ClientCertificateFactory.toPkcs12(identity))
        return identity
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File.createTempFile("client-identity", ".tmp", context.filesDir)
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
            }
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val TAG = "CertificateStore"
        const val STORE_FILE = "client-identity.p12"
        const val CLIENT_NAME = "ATV Remote"
        val LOCK = Any()
    }
}
