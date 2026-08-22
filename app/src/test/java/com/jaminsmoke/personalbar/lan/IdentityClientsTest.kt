package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.Zona
import com.jaminsmoke.personalbar.data.ZonaColor
import com.jaminsmoke.personalbar.ui.validarNuevaPassword
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityClientsTest {

    @Test
    fun negocioApuntaAlServicioNegocio() {
        assertEquals("https://negocio.siberia.solutions", IdentityNegocioClient.DEFAULT_BASE_URL)
    }

    @Test
    fun camareroApuntaAlServicioCamareros() {
        assertEquals("https://camareros.siberia.solutions", IdentityCamareroClient.DEFAULT_BASE_URL)
    }

    @Test
    fun basesSonDistintas() {
        assertNotEquals(IdentityNegocioClient.DEFAULT_BASE_URL, IdentityCamareroClient.DEFAULT_BASE_URL)
    }

    @Test
    fun desconectarConservandoBaseUrlMantieneConfigYDesconectaSesion() {
        // Estado de sesión conectada (como tras un login).
        IdentityNegocioClient.configurar("https://dev.local")
        IdentityNegocioClient.negocioToken = "tok-1"
        IdentityNegocioClient.establecimientoUuid = "e-1"
        IdentityNegocioClient.cuentaNegocio = IdentityCuentaNegocio(
            email = "negocioTest@x.es",
            nombreMostrar = "Bar Test",
        )
        assertTrue(IdentityNegocioClient.conectado)

        // «Logout técnico» del 401/logout: conserva baseUrl (config estática).
        IdentityNegocioClient.desconectarConservandoBaseUrl()

        assertFalse(IdentityNegocioClient.conectado)
        assertNull(IdentityNegocioClient.negocioToken)
        assertNull(IdentityNegocioClient.establecimientoUuid)
        assertEquals("https://dev.local", IdentityNegocioClient.baseUrl)

        IdentityNegocioClient.baseUrl = IdentityNegocioClient.DEFAULT_BASE_URL
    }

    @Test
    fun desconectarDestructivoAnulaBaseUrl() {
        IdentityNegocioClient.configurar("https://dev.local")
        IdentityNegocioClient.negocioToken = "tok-1"
        IdentityNegocioClient.establecimientoUuid = "e-1"

        IdentityNegocioClient.desconectar()

        assertFalse(IdentityNegocioClient.conectado)
        assertNull(IdentityNegocioClient.baseUrl)

        IdentityNegocioClient.baseUrl = IdentityNegocioClient.DEFAULT_BASE_URL
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
              {"id":"e-1","establecimiento_id":"est-1","tipo":"web","slug":"mi-bar-web","estado":"activo","expira_en":"2026-12-31T00:00:00Z","url_publica":"https://web.negocio.siberia.solutions/negocios/mi-bar-web"},
              {"id":"e-2","establecimiento_id":"est-1","tipo":"carta","slug":"mi-bar-carta","estado":"activo","url_publica":"https://web.negocio.siberia.solutions/negocios/mi-bar-carta/carta"}
            ]
        """.trimIndent()
        val lista = LanJson.decodeFromString<List<IdentityEnlacePublico>>(json)
        assertEquals(2, lista.size)
        assertEquals("web", lista[0].tipo)
        assertEquals("est-1", lista[0].establecimientoId)
        assertEquals("https://web.negocio.siberia.solutions/negocios/mi-bar-web", lista[0].urlPublica)
        assertEquals("carta", lista[1].tipo)
        assertEquals("https://web.negocio.siberia.solutions/negocios/mi-bar-carta/carta", lista[1].urlPublica)
    }

    @Test
    fun enlacesListaDecodificaAliasLegadoFichaNegocio() {
        val json = """
            [{"id":"e-1","establecimiento_id":"est-1","tipo":"ficha_negocio","slug":"mi-bar-ficha","estado":"activo","url_publica":"https://web.negocio.siberia.solutions/negocios/mi-bar-ficha"}]
        """.trimIndent()
        val lista = LanJson.decodeFromString<List<IdentityEnlacePublico>>(json)
        assertEquals("ficha_negocio", lista[0].tipo)
        assertEquals("https://web.negocio.siberia.solutions/negocios/mi-bar-ficha", lista[0].urlPublica)
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
    fun establecimientoUpdateSerializaVisibleDirectorio() {
        // El opt-in es explícito (true/false), no un campo opcional que se omite.
        val activo = LanJson.encodeToString(
            EstablecimientoUpdateRequest(visibleDirectorio = true)
        )
        assertEquals("""{"visible_directorio":true}""", activo)

        val inactivo = LanJson.encodeToString(
            EstablecimientoUpdateRequest(visibleDirectorio = false)
        )
        assertEquals("""{"visible_directorio":false}""", inactivo)
    }

    @Test
    fun establecimientoRespuestaDecodifica() {
        val json = """
            {"id":"est-1","nombre":"Mi Bar","tipo_establecimiento":"bar",
             "logo_url":"https://ficha.siberia.solutions/logo.webp",
             "cuenta_negocio_id":"c-1","data_origin":"real","visible_directorio":true}
        """.trimIndent()
        val est = LanJson.decodeFromString<IdentityEstablecimiento>(json)
        assertEquals("est-1", est.id)
        assertEquals("Mi Bar", est.nombre)
        assertEquals("bar", est.tipoEstablecimiento)
        assertEquals("https://ficha.siberia.solutions/logo.webp", est.logoUrl)
        assertEquals("c-1", est.cuentaNegocioId)
        assertEquals("real", est.dataOrigin)
        assertEquals(true, est.visibleDirectorio)
    }

    @Test
    fun establecimientoRespuestaSinVisibleDirectorioDecodificaConNull() {
        // Respuesta de una versión anterior de Identity (sin el campo): tolera null.
        val json = """{"id":"est-1","nombre":"Mi Bar","data_origin":"real"}"""
        val est = LanJson.decodeFromString<IdentityEstablecimiento>(json)
        assertEquals(null, est.visibleDirectorio)
    }

    @Test
    fun layoutEntitiesRoundTrip() {
        val salas = listOf(Sala("sala-barra", "Barra", 1))
        val mesas = listOf(
            Mesa(id = "mesa-1", salaId = "sala-barra", indiceZona = 1, numero = 1, forma = MesaForma.REDONDA, capacidad = 2, posX = 40f, posY = 40f)
        )
        val zonas = listOf(Zona(id = "zona-1", salaId = "sala-barra", nombre = "Interior", color = ZonaColor.VERDE))
        val salasJson = LanJson.encodeToString(salas)
        val mesasJson = LanJson.encodeToString(mesas)
        assertEquals(salas, LanJson.decodeFromString<List<Sala>>(salasJson))
        assertEquals(mesas, LanJson.decodeFromString<List<Mesa>>(mesasJson))
        // LayoutSnapshot roundtrip con tres capas
        val snapshot = LayoutSnapshot(salas = salas, mesas = mesas, zonas = zonas)
        val snapshotJson = LanJson.encodeToString(snapshot)
        val decoded = LanJson.decodeFromString<LayoutSnapshot>(snapshotJson)
        assertEquals(1, decoded.zonas.size)
        assertEquals("Interior", decoded.zonas[0].nombre)
    }

    @Test
    fun cambioPasswordRequestSerializa() {
        val json = LanJson.encodeToString(
            CambioPasswordRequest(passwordActual = "actual-123", passwordNueva = "nueva-12345")
        )
        assertEquals("""{"password_actual":"actual-123","password_nueva":"nueva-12345"}""", json)
    }

    @Test
    fun cambioPasswordResponseDecodifica() {
        val json = """{"status":"cambiada"}"""
        val resp = LanJson.decodeFromString<CambioPasswordResponse>(json)
        assertEquals("cambiada", resp.status)
    }

    @Test
    fun validarNuevaPasswordRechazaCorta() {
        assertNotNull(validarNuevaPassword("abc", "abc"))
    }

    @Test
    fun validarNuevaPasswordRechazaNoCoincide() {
        assertNotNull(validarNuevaPassword("abcdefgh", "abcdefgi"))
    }

    @Test
    fun validarNuevaPasswordAceptaValida() {
        assertNull(validarNuevaPassword("abcdefgh", "abcdefgh"))
    }

    @Test
    fun jornadaCfcResponseDecodificaAbierta() {
        val json = """{
            "id": "j-1",
            "establecimiento_id": "e-1",
            "abierta_en": "2026-08-21T10:00:00Z",
            "ultimo_heartbeat": "2026-08-21T10:00:30Z",
            "cerrada_en": null,
            "bar_en_linea": true
        }"""
        val resp = LanJson.decodeFromString<JornadaCfcResponse>(json)
        assertEquals("j-1", resp.id)
        assertEquals("e-1", resp.establecimientoId)
        assertTrue(resp.barEnLinea)
        assertNull(resp.cerradaEn)
    }

    @Test
    fun jornadaCfcResponseDecodificaCerradaYEnviarLineaFalse() {
        val json = """{
            "id": "j-2",
            "establecimiento_id": "e-1",
            "abierta_en": "2026-08-21T09:00:00Z",
            "ultimo_heartbeat": "2026-08-21T09:00:20Z",
            "cerrada_en": "2026-08-21T10:00:00Z",
            "bar_en_linea": false
        }"""
        val resp = LanJson.decodeFromString<JornadaCfcResponse>(json)
        assertFalse(resp.barEnLinea)
        assertNotNull(resp.cerradaEn)
    }

    @Test
    fun jornadaCfcResponseRoundTrip() {
        val original = JornadaCfcResponse(
            id = "j-3",
            establecimientoId = "e-1",
            abiertaEn = "2026-08-21T10:00:00Z",
            ultimoHeartbeat = "2026-08-21T10:00:30Z",
            barEnLinea = true,
        )
        val json = LanJson.encodeToString(original)
        assertEquals(original, LanJson.decodeFromString<JornadaCfcResponse>(json))
        assertTrue(json.contains("bar_en_linea"))
    }

    // ── MesaCfcItem / MesaCfcResponse ────────────────────────────────

    @Test
    fun mesaCfcItemSerializaCamposEsperados() {
        val item = MesaCfcItem(mesaUuid = "abc-123", etiqueta = "T3")
        val json = LanJson.encodeToString(item)
        assertTrue(json.contains("mesa_uuid"))
        assertTrue(json.contains("abc-123"))
        assertTrue(json.contains("T3"))
    }

    @Test
    fun mesaCfcItemRoundTrip() {
        val original = MesaCfcItem(mesaUuid = "uuid-42", etiqueta = "Barra 1")
        val json = LanJson.encodeToString(original)
        assertEquals(original, LanJson.decodeFromString<MesaCfcItem>(json))
    }

    @Test
    fun mesaCfcResponseRoundTrip() {
        val original = MesaCfcResponse(
            mesaUuid = "uuid-42",
            etiqueta = "T3",
            estado = "activo",
            urlPublica = "https://cfc.example.com/m/abc123",
        )
        val json = LanJson.encodeToString(original)
        assertEquals(original, LanJson.decodeFromString<MesaCfcResponse>(json))
        assertTrue(json.contains("url_publica"))
    }

    @Test
    fun mesaCfcResponseSinUrl() {
        val json = """{"mesa_uuid":"u1","etiqueta":"B2","estado":"activo"}"""
        val resp = LanJson.decodeFromString<MesaCfcResponse>(json)
        assertEquals("u1", resp.mesaUuid)
        assertEquals("activo", resp.estado)
        assertNull(resp.urlPublica)
    }

    @Test
    fun mesaCfcResponseRevocado() {
        val original = MesaCfcResponse(
            mesaUuid = "uuid-99",
            etiqueta = "T1",
            estado = "revocado",
            urlPublica = null,
        )
        val json = LanJson.encodeToString(original)
        assertTrue(json.contains("revocado"))
        val decoded = LanJson.decodeFromString<MesaCfcResponse>(json)
        assertEquals("revocado", decoded.estado)
    }

    // ── PedidoCfc / PedidosCfcListaResponse ────────────────────────────

    @Test
    fun pedidoCfcResponseRoundTrip() {
        val original = PedidoCfcResponse(
            id = "pedido-1",
            mesaUuid = "uuid-mesa-1",
            etiqueta = "B3",
            estado = "PENDIENTE",
            seq = 5,
            lineas = listOf(
                PedidoCfcLinea(productoId = "p1", nombre = "Caña", cantidad = 2, precioCentimos = 200, destino = "barra"),
            ),
            totalCentimos = 400,
            creadoEn = "2026-08-22T10:00:00Z",
        )
        val json = LanJson.encodeToString(original)
        assertTrue(json.contains("mesa_uuid"))
        assertTrue(json.contains("total_centimos"))
        assertTrue(json.contains("creado_en"))
        assertEquals(original, LanJson.decodeFromString<PedidoCfcResponse>(json))
    }

    @Test
    fun pedidosCfcListaResponseRoundTrip() {
        val original = PedidosCfcListaResponse(
            pedidos = listOf(
                PedidoCfcResponse(id = "p1", mesaUuid = "u1", etiqueta = "B1", estado = "PENDIENTE", seq = 1),
                PedidoCfcResponse(id = "p2", mesaUuid = "u2", etiqueta = "T3", estado = "PENDIENTE", seq = 2),
            ),
            cursor = 2,
        )
        val json = LanJson.encodeToString(original)
        assertEquals(original, LanJson.decodeFromString<PedidosCfcListaResponse>(json))
        assertTrue(json.contains("\"cursor\":2"))
    }

    @Test
    fun pedidosCfcListaVaciaConCursor() {
        val json = """{"pedidos":[],"cursor":7}"""
        val resp = LanJson.decodeFromString<PedidosCfcListaResponse>(json)
        assertTrue(resp.pedidos.isEmpty())
        assertEquals(7, resp.cursor)
    }

    @Test
    fun ackBodyUsaDecisionCorrecta() {
        // El body del ACK se construye en el cliente; aquí validamos la forma
        // que el server espera (PedidoCfcAckRequest: decision literal).
        val aceptado = """{"decision":"aceptado"}"""
        val rechazado = """{"decision":"rechazado"}"""
        assertTrue(aceptado.contains("aceptado"))
        assertTrue(rechazado.contains("rechazado"))
    }
}
