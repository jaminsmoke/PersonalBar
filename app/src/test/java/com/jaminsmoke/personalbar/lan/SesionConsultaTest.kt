package com.jaminsmoke.personalbar.lan

import com.google.crypto.tink.subtle.Ed25519Sign
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.QrKey
import com.jaminsmoke.personalbar.data.QrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

class SesionConsultaTest {

    private val camareroId = "11111111-1111-4111-8111-111111111111"
    private val credencialId = "22222222-2222-4222-8222-222222222222"
    private val qr = "phid1:$camareroId:$credencialId:firmaTest"

    private fun activa() = Camarero(
        id = camareroId,
        nombre = "luciaTest",
        credencialId = credencialId,
        estado = CamareroEstado.ACTIVA,
    )

    @Test
    fun qrInvalido() {
        assertEquals(SesionConsulta.Resultado.QrInvalido, SesionConsulta.evaluar("no-qr", emptyList(), null))
        assertEquals(SesionConsulta.Resultado.QrInvalido, SesionConsulta.evaluar("", emptyList(), null))
    }

    @Test
    fun activaSinClaveAdmitida() {
        val r = SesionConsulta.evaluar(qr, listOf(activa()), null) as SesionConsulta.Resultado.Ok
        assertTrue(r.respuesta.admitido)
        assertEquals(camareroId, r.respuesta.camareroId)
        assertEquals("luciaTest", r.respuesta.nombre)
    }

    @Test
    fun revocadaNoAdmitida() {
        val revocada = activa().copy(estado = CamareroEstado.REVOCADA)
        val r = SesionConsulta.evaluar(qr, listOf(revocada), null) as SesionConsulta.Resultado.Ok
        assertFalse(r.respuesta.admitido)
        assertEquals(camareroId, r.respuesta.camareroId)
        assertEquals("luciaTest", r.respuesta.nombre)
    }

    @Test
    fun desconocidoNoAdmitidoNiAlta() {
        val r = SesionConsulta.evaluar(qr, emptyList(), null) as SesionConsulta.Resultado.Ok
        assertFalse(r.respuesta.admitido)
        assertEquals(camareroId, r.respuesta.camareroId)
        assertNull(r.respuesta.nombre)
    }

    @Test
    fun firmaInvalidaConClaveRechazaAunqueEsteActiva() {
        val clave = Ed25519Sign.KeyPair.newKeyPair()
        val publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(clave.publicKey)
        val qrKey = QrKey(publicKey = publicKey)
        val r = SesionConsulta.evaluar(qr, listOf(activa()), qrKey) as SesionConsulta.Resultado.Ok
        assertFalse(r.respuesta.admitido)
        assertEquals(camareroId, r.respuesta.camareroId)
        assertNull(r.respuesta.nombre)
    }

    @Test
    fun firmaValidaConClaveYListaAdmitida() {
        val keyPair = Ed25519Sign.KeyPair.newKeyPair()
        val id = UUID.randomUUID().toString()
        val credencial = UUID.randomUUID().toString()
        val mensaje = "${QrParser.PREFIX}:$id:$credencial".encodeToByteArray()
        val firma = Ed25519Sign(keyPair.privateKey).sign(mensaje)
        val payload = "phid1:$id:$credencial:" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(firma)
        val qrKey = QrKey(
            publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.publicKey),
        )
        val camarero = Camarero(id = id, nombre = "anaTest", credencialId = credencial)
        val r = SesionConsulta.evaluar(payload, listOf(camarero), qrKey) as SesionConsulta.Resultado.Ok
        assertTrue(r.respuesta.admitido)
        assertEquals(id, r.respuesta.camareroId)
        assertEquals("anaTest", r.respuesta.nombre)
    }
}
