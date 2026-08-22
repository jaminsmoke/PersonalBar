package com.jaminsmoke.personalbar.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests instrumentados: el Android Keystore solo existe en dispositivo/emulador. */
class TokenCifradorTest {

    @Test
    fun roundTripCifraYDescifra() {
        val token = "eyJhbGciOiJIUzI1NiJ9.secreto-muy-largo"
        val cifrado = TokenCifrador.cifrar(token)
        assertNotNull(cifrado)
        assertEquals(token, TokenCifrador.descifrar(cifrado))
    }

    @Test
    fun ciphertextEsDistintoDelClaro() {
        val token = "jwt-en-claro"
        val cifrado = TokenCifrador.cifrar(token)
        assertNotEquals(token, cifrado)
        // Base64 del IV+ciphertext: no contiene el token
        assertFalse(cifrado.contains(token))
    }

    @Test
    fun cifradosDelMismoTokenSonDistintos() {
        // GCM usa IV aleatorio por operación: nunca se repite el ciphertext
        val a = TokenCifrador.cifrar("token-1")
        val b = TokenCifrador.cifrar("token-1")
        assertNotEquals(a, b)
        assertEquals("token-1", TokenCifrador.descifrar(a))
        assertEquals("token-1", TokenCifrador.descifrar(b))
    }

    @Test
    fun entradaInvalidaDevuelveNull() {
        assertNull(TokenCifrador.descifrar(""))
        assertNull(TokenCifrador.descifrar("no-es-base64!!!"))
        assertNull(TokenCifrador.descifrar("corto"))
    }
}
