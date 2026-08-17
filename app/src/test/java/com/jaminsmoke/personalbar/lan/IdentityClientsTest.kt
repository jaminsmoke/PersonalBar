package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Sala
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityClientsTest {

    @Test
    fun negocioApuntaAlServicioNegocio() {
        assertTrue(IdentityNegocioClient.DEFAULT_BASE_URL.endsWith(":8082"))
    }

    @Test
    fun camareroApuntaAlServicioCamareros() {
        assertTrue(IdentityCamareroClient.DEFAULT_BASE_URL.endsWith(":8080"))
    }

    @Test
    fun basesSonDistintas() {
        assertNotEquals(IdentityNegocioClient.DEFAULT_BASE_URL, IdentityCamareroClient.DEFAULT_BASE_URL)
    }

    @Test
    fun qrPublicKeyDecodifica() {
        val json = """{"algorithm":"Ed25519","key_id":"ed25519-v1","public_key":"abc","qr_prefix":"phid1","format":"phid1:<camarero_id>:<credencial_id>:<firma>"}"""
        val key = LanJson.decodeFromString<IdentityQrPublicKey>(json)
        assertEquals("Ed25519", key.algorithm)
        assertEquals("ed25519-v1", key.keyId)
        assertEquals("abc", key.publicKey)
        assertEquals("phid1", key.qrPrefix)
    }

    @Test
    fun invitacionesListaDecodificaConExpiradaYCamposNuevos() {
        val json = """
            [
              {"id":"i-1","email":"a@test.com","rol":"staff","estado":"expirada","establecimiento_id":"e-1","expira_en":"2026-01-01T00:00:00Z","creada_en":"2026-01-01T00:00:00Z"},
              {"id":"i-2","email":"b@test.com","rol":"staff","estado":"pendiente","expira_en":"2026-12-31T00:00:00Z"}
            ]
        """.trimIndent()
        val lista = LanJson.decodeFromString<List<IdentityInvitacion>>(json)
        assertEquals(2, lista.size)
        assertEquals("expirada", lista[0].estado)
        assertEquals("e-1", lista[0].establecimientoId)
        assertEquals("2026-01-01T00:00:00Z", lista[0].creadaEn)
        assertEquals("pendiente", lista[1].estado)
    }

    @Test
    fun layoutEntitiesRoundTrip() {
        val salas = listOf(Sala("sala-barra", "Barra", 1))
        val mesas = listOf(
            Mesa(id = "mesa-1", salaId = "sala-barra", indiceZona = 1, numero = 1, forma = MesaForma.REDONDA, capacidad = 2, posX = 40f, posY = 40f)
        )
        val salasJson = LanJson.encodeToString(salas)
        val mesasJson = LanJson.encodeToString(mesas)
        assertEquals(salas, LanJson.decodeFromString<List<Sala>>(salasJson))
        assertEquals(mesas, LanJson.decodeFromString<List<Mesa>>(mesasJson))
    }
}
