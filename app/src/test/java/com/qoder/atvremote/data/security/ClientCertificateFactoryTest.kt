package com.qoder.atvremote.data.security

import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class ClientCertificateFactoryTest {

    @Test
    fun `generated identity is a valid self signed client certificate`() {
        val identity = ClientCertificateFactory.create("Test Client")
        val certificate = identity.certificate

        certificate.checkValidity()
        assertEquals("RSA", certificate.publicKey.algorithm)
        assertEquals(2048, (certificate.publicKey as RSAPublicKey).modulus.bitLength())
        assertEquals((certificate.publicKey as RSAPublicKey).modulus, (identity.privateKey as RSAPrivateKey).modulus)
        certificate.verify(certificate.publicKey)
        assertTrue(certificate.basicConstraints >= 0)
    }

    @Test
    fun `certificate carries client auth extensions and a ten year validity`() {
        val certificate = ClientCertificateFactory.create("Living Room").certificate
        val holder = JcaX509CertificateHolder(certificate)

        assertNotNull(holder.getExtension(Extension.subjectAlternativeName))
        assertNotNull(holder.getExtension(Extension.extendedKeyUsage))
        assertNotNull(holder.getExtension(Extension.keyUsage))

        val keyUsage = KeyUsage.getInstance(holder.getExtension(Extension.keyUsage).parsedValue)
        assertTrue(keyUsage.hasUsages(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))

        val basicConstraints = BasicConstraints.getInstance(
            holder.getExtension(Extension.basicConstraints).parsedValue,
        )
        assertTrue(basicConstraints.isCA)

        val validity = certificate.notAfter.time - certificate.notBefore.time
        assertTrue(validity > 3000L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `identity survives a pkcs12 round trip`() {
        val original = ClientCertificateFactory.create("Round Trip")
        val encoded = ClientCertificateFactory.toPkcs12(original)

        val restored = ClientCertificateFactory.load(encoded)

        assertNotNull(restored)
        requireNotNull(restored)
        assertEquals(original.certificate, restored.certificate)
        assertEquals(
            (original.privateKey as RSAPrivateKey).modulus,
            (restored.privateKey as RSAPrivateKey).modulus,
        )
        assertEquals(ClientCertificateFactory.ALIAS, restored.keyStore.aliases().nextElement())
    }

    @Test
    fun `corrupt keystore data is rejected`() {
        assertNull(ClientCertificateFactory.load(byteArrayOf(1, 2, 3, 4)))
    }
}
