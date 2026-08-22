package com.jaminsmoke.personalbar.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodoSesionTest {

    private val SECRETO = "secretoDeNodoParaPruebas"
    private val AHORA = 1_750_000_000_000L

    @Test
    fun emitirYVerificarRoundTrip() {
        val token = NodoSesion.emitir("cam-1", SECRETO, ahoraMs = AHORA)
        assertTrue(token.startsWith("phbar1."))
        assertEquals("cam-1", NodoSesion.verificar(token, SECRETO, ahoraMs = AHORA))
    }

    @Test
    fun secretoEquivocadoDevuelveNull() {
        val token = NodoSesion.emitir("cam-1", SECRETO, ahoraMs = AHORA)
        // Con otro secreto, la firma no coincide → null (el id codificado es cam-1).
        assertNull(NodoSesion.verificar(token, "otroSecreto", ahoraMs = AHORA))
    }

    @Test
    fun tokenManipuladoDevuelveNull() {
        val token = NodoSesion.emitir("cam-1", SECRETO, ahoraMs = AHORA)
        // Cambiar el payload (cam-1 → cam-9) sin re-firmar.
        val partes = token.split('.')
        val payload = String(java.util.Base64.getUrlDecoder().decode(partes[1]), Charsets.UTF_8)
        val payloadNuevo = payload.replace("cam-1", "cam-9")
        val manipulado = partes[0] + "." +
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadNuevo.toByteArray()) +
            "." + partes[2]
        assertNull(NodoSesion.verificar(manipulado, SECRETO, ahoraMs = AHORA))
    }

    @Test
    fun tokenExpiradoDevuelveNull() {
        val token = NodoSesion.emitir("cam-1", SECRETO, ahoraMs = AHORA, ttlMs = 60_000L)
        // 61 s después → expirado.
        assertNull(NodoSesion.verificar(token, SECRETO, ahoraMs = AHORA + 61_000L))
        // Justo antes de expirar → válido.
        assertEquals("cam-1", NodoSesion.verificar(token, SECRETO, ahoraMs = AHORA + 59_000L))
    }

    @Test
    fun formatoInvalidoDevuelveNull() {
        assertNull(NodoSesion.verificar("", SECRETO, ahoraMs = AHORA))
        assertNull(NodoSesion.verificar("phbar1.soloDosPartes", SECRETO, ahoraMs = AHORA))
        assertNull(NodoSesion.verificar("otroPrefijo.a.b", SECRETO, ahoraMs = AHORA))
        assertNull(NodoSesion.verificar("phbar1.@@@.@@@", SECRETO, ahoraMs = AHORA))
    }

    @Test
    fun secretoVacioNoEmiteNiVerifica() {
        // Fail-closed: sin secreto no hay credencial ni sesión válida.
        assertEquals("", NodoSesion.emitir("cam-1", "", ahoraMs = AHORA))
        assertNull(NodoSesion.verificar("phbar1.a.b", "", ahoraMs = AHORA))
        assertNull(NodoSesion.verificar("phbar1.a.b", "  ", ahoraMs = AHORA))
    }

    @Test
    fun secretosDistintosEmitenTokensDistintos() {
        val a = NodoSesion.emitir("cam-1", "secretoA", ahoraMs = AHORA)
        val b = NodoSesion.emitir("cam-1", "secretoB", ahoraMs = AHORA)
        assertNotEquals(a, b)
    }

    @Test
    fun generarSecretoDevuelveBase64Largo() {
        val secreto = NodoSesion.generarSecreto()
        assertTrue(secreto.length >= 40) // 32 bytes en Base64 ≈ 44 chars
        assertNotEquals(secreto, NodoSesion.generarSecreto())
    }
}
