package com.qoder.atvremote.data.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

class ClientIdentity(
    val keyStore: KeyStore,
    val storePassword: CharArray,
    val certificate: X509Certificate,
    val privateKey: PrivateKey,
)

/**
 * Generates the self-signed RSA identity that the TV pins during pairing. The TV reads the
 * certificate's public key during TLS and uses it, together with the PIN, to derive the secret.
 */
object ClientCertificateFactory {

    const val ALIAS = "atv-remote-client"
    val STORE_PASSWORD: CharArray = "atv-remote-identity".toCharArray()

    private val RAS_ALGORITHM = "RSA"
    private const val KEY_SIZE = 2048
    private const val VALIDITY_DAYS = 3650L

    fun create(clientName: String): ClientIdentity {
        val keyPair = KeyPairGenerator.getInstance(RAS_ALGORITHM).run {
            initialize(KEY_SIZE, SecureRandom())
            generateKeyPair()
        }

        val subject = X500Name("CN=$clientName, O=ATV Remote, C=US")
        val now = System.currentTimeMillis()
        val notBefore = Date(now - TimeUnit.DAYS.toMillis(1))
        val notAfter = Date(now + TimeUnit.DAYS.toMillis(VALIDITY_DAYS))
        val serial = BigInteger.valueOf(now).multiply(BigInteger.valueOf(1000))

        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.keyCertSign),
            )
            addExtension(
                Extension.extendedKeyUsage,
                false,
                ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth),
            )
            addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(GeneralName(GeneralName.dNSName, "atvremote")),
            )
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer))
        certificate.checkValidity()
        certificate.verify(keyPair.public)

        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        keyStore.setKeyEntry(ALIAS, keyPair.private, STORE_PASSWORD, arrayOf(certificate))

        return ClientIdentity(keyStore, STORE_PASSWORD, certificate, keyPair.private)
    }

    fun toPkcs12(identity: ClientIdentity): ByteArray =
        ByteArrayOutputStream().use { output ->
            identity.keyStore.store(output, identity.storePassword)
            output.toByteArray()
        }

    fun load(pkcs12: ByteArray): ClientIdentity? = runCatching {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(ByteArrayInputStream(pkcs12), STORE_PASSWORD)
        val privateKey = keyStore.getKey(ALIAS, STORE_PASSWORD) as? PrivateKey ?: return null
        val certificate = keyStore.getCertificate(ALIAS) as? X509Certificate ?: return null
        certificate.checkValidity()
        ClientIdentity(keyStore, STORE_PASSWORD, certificate, privateKey)
    }.getOrNull()
}
