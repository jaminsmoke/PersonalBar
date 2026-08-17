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
    fun enlacesListaDecodificaConUrlPublica() {
        val json = """
            [
              {"id":"e-1","establecimiento_id":"est-1","tipo":"ficha_negocio","slug":"mi-bar-ficha","estado":"activo","expira_en":"2026-12-31T00:00:00Z","url_publica":"https://ficha.siberia.solutions/negocio?slug=mi-bar-ficha"},
              {"id":"e-2","establecimiento_id":"est-1","tipo":"carta","slug":"mi-bar-carta","estado":"activo","url_publica":"https://carta.siberia.solutions/carta?slug=mi-bar-carta"}
            ]
        """.trimIndent()
        val lista = LanJson.decodeFromString<List<IdentityEnlacePublico>>(json)
        assertEquals(2, lista.size)
        assertEquals("ficha_negocio", lista[0].tipo)
        assertEquals("est-1", lista[0].establecimientoId)
        assertEquals("https://ficha.siberia.solutions/negocio?slug=mi-bar-ficha", lista[0].urlPublica)
        assertEquals("carta", lista[1].tipo)
        assertEquals("https://carta.siberia.solutions/carta?slug=mi-bar-carta", lista[1].urlPublica)
    }

    @Test
    fun establecimientoUpdateOmiteCamposNulos() {
        val soloNombre = LanJson.encodeToString(
            EstablecimientoUpdateRequest(nombre = "Mi Bar")
        )
        assertEquals("""{"nombre":"Mi Bar"}""", soloNombre)

        val soloTipo = LanJson.encodeToString(
            EstablecimientoUpdateRequest(tipoEstablecimiento = "pub")
        )
        assertEquals("""{"tipo_establecimiento":"pub"}""", soloTipo)

        val ambos = LanJson.encodeToString(
            EstablecimientoUpdateRequest(nombre = "Mi Bar", tipoEstablecimiento = "pub")
        )
        assertEquals("""{"nombre":"Mi Bar","tipo_establecimiento":"pub"}""", ambos)
    }

    @Test
    fun establecimientoRespuestaDecodifica() {
        val json = """
            {"id":"est-1","nombre":"Mi Bar","tipo_establecimiento":"bar",
             "logo_url":"https://ficha.siberia.solutions/logo.webp",
             "cuenta_negocio_id":"c-1","data_origin":"real"}
        """.trimIndent()
        val est = LanJson.decodeFromString<IdentityEstablecimiento>(json)
        assertEquals("est-1", est.id)
        assertEquals("Mi Bar", est.nombre)
        assertEquals("bar", est.tipoEstablecimiento)
        assertEquals("https://ficha.siberia.solutions/logo.webp", est.logoUrl)
        assertEquals("c-1", est.cuentaNegocioId)
        assertEquals("real", est.dataOrigin)
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
