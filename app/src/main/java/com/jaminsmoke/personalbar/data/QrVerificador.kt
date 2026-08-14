package com.jaminsmoke.personalbar.data

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * Verificación criptográfica Ed25519 del QR `phid1` contra la clave pública de
 * Identity (cacheada). El QR lleva la firma estática de Identity sobre
 * `phid1:<camarero_id>:<credencial_id>`; verificarla localmente permite el alta
 * offline sin pasar por el server (misma cripto que usa Identity: Ed25519,
 * base64url sin padding).
 */
object QrVerificador {

    private const val PUBLIC_KEY_LEN = 32
    private const val SIGNATURE_LEN = 64

    /** @return true si la firma del [payload] es válida contra [publicKeyBase64Url]. */
    fun verificar(payload: String, publicKeyBase64Url: String): Boolean {
        val phid = QrParser.parsear(payload) ?: return false
        val clave = decodificar(publicKeyBase64Url) ?: return false
        if (clave.size != PUBLIC_KEY_LEN) return false
        val firma = decodificar(phid.firma) ?: return false
        if (firma.size != SIGNATURE_LEN) return false
        // Mensaje firmado por Identity: el prefijo + id + credencial, sin la firma.
        val mensaje = "${QrParser.PREFIX}:${phid.camareroId}:${phid.credencialId}".encodeToByteArray()
        return try {
            Ed25519Verify(clave).verify(firma, mensaje)
            true
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /** Base64url sin padding (lo que devuelve Identity). Null si está mal formado. */
    private fun decodificar(valor: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(valor)
    } catch (_: IllegalArgumentException) {
        null
    }
}
