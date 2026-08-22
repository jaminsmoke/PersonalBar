package com.jaminsmoke.personalbar.lan

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra/descifra el bearer de sesión con AES-GCM usando una clave del
 * **Android Keystore** (alias `personalbar_token`, generada una vez por
 * dispositivo). Las claves Keystore nunca viajan en backups ni transferencias:
 * un `tokenCifrado` en otro dispositivo (o tras factory reset) no se puede
 * descifrar → [descifrar] devuelve null y la app fuerza re-login.
 */
object TokenCifrador {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "personalbar_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** Cifra [texto] → Base64(IV + ciphertext). Lanza si el Keystore falla. */
    fun cifrar(texto: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, clave())
        val ciphertext = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        // IV primero (12 bytes de GCM), ciphertext después — ambos necesarios para descifrar
        val iv = cipher.iv
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Descifra un valor de [cifrar]. Devuelve null si la clave no existe
     * (otro dispositivo / factory reset) o el formato no es válido — nunca lanza.
     */
    fun descifrar(cifrado: String): String? = runCatching {
        val datos = Base64.decode(cifrado, Base64.NO_WRAP)
        if (datos.size < 12) return null
        val iv = datos.copyOfRange(0, 12)
        val cuerpo = datos.copyOfRange(12, datos.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(cuerpo), Charsets.UTF_8)
    }.getOrNull()

    /** Clave AES-GCM del Keystore; la crea la primera vez. */
    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }
}
