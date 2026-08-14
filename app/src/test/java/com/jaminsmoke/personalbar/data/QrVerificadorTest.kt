package com.jaminsmoke.personalbar.data

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

class QrVerificadorTest {

    private class QrDePrueba(val payload: String, val publicKeyB64: String, val mensaje: ByteArray)

    /** Genera un QR `phid1` firmado con una clave Ed25519 recién creada (como Identity). */
    private fun qrValido(): QrDePrueba {
        val keyPair = Ed25519Sign.KeyPair.newKeyPair()
        val signer = Ed25519Sign(keyPair.privateKey)
        val id = UUID.randomUUID().toString()
        val credencial = UUID.randomUUID().toString()
        val mensaje = "${
            QrParser.PREFIX
        }:${id}:${credencial}".encodeToByteArray()
        val firma = signer.sign(mensaje)
        val payload = "phid1:$id:$credencial:" + Base64.getUrlEncoder().withoutPadding().encodeToString(firma)
        val publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.publicKey)
        return QrDePrueba(payload, publicKey, mensaje)
    }

    @Test
    fun firmaValidaVerifica() {
        val qr = qrValido()
        assertTrue(QrVerificador.verificar(qr.payload, qr.publicKeyB64))
    }

    @Test
    fun firmaManipuladaSeRechaza() {
        val qr = qrValido()
        // Alterar el id dentro del payload (la firma ya no casa con el mensaje).
        val manipulado = qr.payload.replaceFirst(qr.payload.substringAfter("phid1:").substringBefore(":"), UUID.randomUUID().toString())
        assertFalse(QrVerificador.verificar(manipulado, qr.publicKeyB64))
    }

    @Test
    fun claveEquivocadaSeRechaza() {
        val qr = qrValido()
        val otraClave = Ed25519Sign.KeyPair.newKeyPair()
        val otraClaveB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(otraClave.publicKey)
        assertFalse(QrVerificador.verificar(qr.payload, otraClaveB64))
    }

    @Test
    fun payloadSinFormaPhid1SeRechaza() {
        assertFalse(QrVerificador.verificar("no-es-un-qr", ""))
    }

    @Test
    fun claveMalformadaSeRechaza() {
        val qr = qrValido()
        assertFalse(QrVerificador.verificar(qr.payload, "!!!no-base64!!!"))
    }
}
