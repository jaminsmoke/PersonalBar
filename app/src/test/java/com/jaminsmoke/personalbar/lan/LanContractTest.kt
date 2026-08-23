package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.SalaEvent
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.TicketEstado
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Congela el contrato LAN (`docs/contrato/fixtures/`) contra los modelos actuales:
 * si un payload de red cambia (campo, enum, esquema), la fixture deja de decodificar
 * y este test falla — el productor debe regenerar el corpus. Es el espejo Kotlin del
 * chequeo estructural de `scripts/check_lan_contract.py`.
 */
class LanContractTest {

    private fun fixture(nombre: String): String {
        val candidatas = listOf(
            Paths.get("docs/contrato/fixtures", nombre),
            Paths.get("docs/contrato", nombre),
            Paths.get("../docs/contrato/fixtures", nombre),
            Paths.get("../docs/contrato", nombre),
        )
        val fichero = candidatas.firstOrNull { Files.exists(it) }
            ?: error("Fixture no encontrada: $nombre (cwd=${Path.of(".").toAbsolutePath()})")
        return Files.readString(fichero)
    }

    @Test
    fun healthFixtureTieneLosCamposDelContrato() {
        val json = LanJson.decodeFromString<JsonObject>(fixture("health.json"))
        assertEquals(true, json["ok"]?.let { it.toString() == "true" })
        assertEquals("bar", json["role"]?.let { it.toString().trim('"') })
        assertEquals("La Terraza", json["establecimiento"]?.let { it.toString().trim('"') })
        assertEquals("La Terraza", json["sala"]?.let { it.toString().trim('"') })
        assertEquals("0.1", json["version"]?.let { it.toString().trim('"') })
        assertTrue(json.containsKey("establecimiento_id"))
    }

    @Test
    fun sesionFixtureDecodifica() {
        val sesion = LanJson.decodeFromString<SesionResponse>(fixture("sesion.json"))
        assertEquals(true, sesion.admitido)
        assertEquals("11111111-1111-4111-8111-111111111111", sesion.camareroId)
        assertEquals("luciaTest", sesion.nombre)
    }

    @Test
    fun sesionIniciarFixtureDecodifica() {
        val resp = LanJson.decodeFromString<SesionIniciarResponse>(fixture("sesion-iniciar.json"))
        assertEquals(true, resp.sesionActiva)
        // v0.2: la respuesta de iniciar lleva el token de sesión (campo aditivo).
        assertEquals("phbar1.bHVjaWEtMSMxNzUwMDAwMDAwMDAw.firmaEjemplo", resp.token)
    }

    @Test
    fun sesionIniciarSinTokenSigueDecodificando() {
        // Nodo 0.1 (sin auth): el campo token es opcional → Commander no se rompe.
        val resp = LanJson.decodeFromString<SesionIniciarResponse>("""{"sesionActiva":true}""")
        assertEquals(true, resp.sesionActiva)
        assertEquals(null, resp.token)
    }

    @Test
    fun rondaFixtureDecodificaComoCommanderLaEnvia() {
        val ronda = LanJson.decodeFromString<Ronda>(fixture("ronda.json"))
        assertEquals("p42-t1730000000000", ronda.id)
        assertEquals("T3", ronda.mesaId)
        assertEquals(1, ronda.numero)
        assertEquals("luciaTest", ronda.camarero)
        assertEquals(2, ronda.lineas.size)
        val conNota = ronda.lineas[1]
        assertEquals("sin cebolla", conNota.nota)
        assertEquals("Al punto", conNota.modificadores.single().opcion)
    }

    @Test
    fun ticketsFixtureDecodificaLaRespuestaDeRondas() {
        val tickets = LanJson.decodeFromString<List<Ticket>>(fixture("tickets.json"))
        assertEquals(2, tickets.size)
        val barra = tickets.first { it.destino == Destino.BARRA }
        assertEquals("r1-barra", barra.id)
        assertEquals(1, barra.numeroCola)
        assertEquals(TicketEstado.PENDIENTE, barra.estado)
        assertEquals(2, barra.lineas.single().cantidad)
    }

    @Test
    fun estadoFixtureDecodifica() {
        val estado = LanJson.decodeFromString<EstadoResponse>(fixture("estado.json"))
        assertEquals("0.1", estado.version)
        assertEquals("La Terraza", estado.establecimiento.nombre)
        assertEquals(1, estado.salas.size)
        assertEquals(1, estado.bebida.size)
        assertEquals(TicketEstado.PREPARADO, estado.bebida.single().estado)
        assertEquals("anaTest", estado.bebida.single().preparadoPor)
        assertEquals(3, estado.mesas.single().indiceZona)
        assertEquals(1, estado.zonas.size)
        assertEquals("zona-1", estado.zonas.single().id)
        assertEquals("VERDE", estado.zonas.single().color.name)
    }

    @Test
    fun cartaFixtureDecodificaConSchemaYModificadores() {
        val carta = LanJson.decodeFromString<CartaResponse>(fixture("carta.json"))
        assertEquals(2, carta.schema)
        val producto = carta.productos.single()
        assertEquals("Caña", producto.nombre)
        assertEquals("Zero", producto.subfamilia)
        assertTrue(producto.permiteNota)
        assertEquals(1, producto.grupos.size)
        val grupo = carta.gruposModificador.single()
        assertEquals("Punto", grupo.nombre)
        assertTrue(grupo.obligatorio)
        assertEquals("Al punto", grupo.opciones.single().nombre)
        assertEquals("al punto", grupo.opciones.single().alias)
    }

    @Test
    fun salaEventFixtureDecodifica() {
        val evento = LanJson.decodeFromString<SalaEvent>(fixture("sala-event-preparado.json"))
        assertEquals(SalaEvent.TIPO_PREPARADO, evento.tipo)
        assertEquals("r1-barra", evento.ticketId)
        assertEquals("anaTest", evento.preparadoPor)
        assertEquals("T3", evento.mesaId)
        assertEquals("luciaTest", evento.camarero)
        assertEquals("2× Caña", evento.resumen)
        assertNotNull(evento.ticket)
        assertEquals(Destino.BARRA, evento.ticket?.destino)
        assertEquals(TicketEstado.PREPARADO, evento.ticket?.estado)
    }

    @Test
    fun jornadasFixtureDecodifica() {
        val resp = LanJson.decodeFromString<JornadasResponse>(fixture("jornadas.json"))
        val resumen = resp.resumen
        assertEquals(1, resumen.intervalos.size)
        assertEquals(1, resumen.porCamarero.size)
        assertEquals(3_600_000L, resumen.porCamarero.single().horasMs)
        assertEquals(3, resumen.porCamarero.single().mesasDistintas)
    }

    /**
     * Fixtures vs spec: cada fixture dorada debe contener todos los campos `required`
     * del schema correspondiente del OpenAPI. Si el spec exige un campo nuevo o el
     * fixture pierde uno, este test falla — el productor regenera el corpus.
     */
    @Test
    fun fixturesCumplenLosCamposRequiredDelSpec() {
        val spec = LanJson.decodeFromString<JsonObject>(fixture("openapi-lan.json"))
        val schemas = spec["components"]!!.jsonObject["schemas"]!!.jsonObject

        // (fixture, schema del spec). El array se resuelve contra el item.
        val pares = listOf(
            "health.json" to "HealthPayload",
            "sesion.json" to "SesionResponse",
            "sesion-iniciar.json" to "SesionIniciarResponse",
            "ronda.json" to "Ronda",
            "estado.json" to "EstadoResponse",
            "carta.json" to "CartaResponse",
            "sala-event-preparado.json" to "SalaEvent",
            "jornadas.json" to "JornadasResponse",
        )

        for ((nombre, schemaNombre) in pares) {
            val payload = LanJson.decodeFromString<JsonObject>(fixture(nombre))
            val schema = schemas[schemaNombre]!!.jsonObject
            val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val ausentes = required.filter { payload[it] == null }
            assertTrue(
                "Fixture $nombre no cumple los required de $schemaNombre: faltan $ausentes",
                ausentes.isEmpty(),
            )
        }

        // tickets.json es un array de Ticket: validar cada elemento.
        val tickets = LanJson.decodeFromString<kotlinx.serialization.json.JsonArray>(fixture("tickets.json"))
        val ticketSchema = schemas["Ticket"]!!.jsonObject
        val ticketRequired = ticketSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        for (t in tickets) {
            val ausentes = ticketRequired.filter { t.jsonObject[it] == null }
            assertTrue("tickets.json no cumple los required de Ticket: faltan $ausentes", ausentes.isEmpty())
        }
    }

    /**
     * Auth v0.2 en el spec: el esquema de sesión bearer existe y el token es
     * aditivo/nullable (un nodo 0.1 no lo emite, Commander no se rompe).
     */
    @Test
    fun specDeclaraAuthBearerConTokenAditivo() {
        val spec = LanJson.decodeFromString<JsonObject>(fixture("openapi-lan.json"))
        val schemes = spec["components"]!!.jsonObject["securitySchemes"]!!.jsonObject
        val sesionLan = schemes["sesionLan"]!!.jsonObject
        assertEquals("http", sesionLan["type"]?.jsonPrimitive?.content)
        assertEquals("bearer", sesionLan["scheme"]?.jsonPrimitive?.content)

        val iniciar = spec["components"]!!.jsonObject["schemas"]!!.jsonObject["SesionIniciarResponse"]!!.jsonObject
        val token = iniciar["properties"]!!.jsonObject["token"]!!.jsonObject
        assertEquals("string", token["type"]?.jsonArray?.first()?.jsonPrimitive?.content)
        assertTrue(token["type"]!!.jsonArray.any { it.jsonPrimitive.content == "null" })
    }

    /**
     * Auth v0.2 en el spec: toda ruta privada declara 401 y las públicas lo anulan.
     */
    @Test
    fun specExige401EnLasRutasPrivadas() {
        val spec = LanJson.decodeFromString<JsonObject>(fixture("openapi-lan.json"))
        val paths = spec["paths"]!!.jsonObject
        val publicas = setOf("/health", "/v1/sesion", "/v1/sesion/iniciar")
        val cortar = "/v1/sesion/cortar"

        for ((ruta, item) in paths) {
            val op = item.jsonObject.values.first { it.jsonObject["responses"] != null }.jsonObject
            if (ruta in publicas) {
                assertEquals("$ruta debe ser pública (security: [])", emptyList<Any>(), op["security"]?.jsonArray?.toList())
            } else if (ruta != cortar) {
                assertTrue("$ruta es privada y debe declarar 401", op["responses"]!!.jsonObject["401"] != null)
            } else {
                // cortar acepta QR o Bearer: la lista de security contiene una opción vacía.
                val sec = op["security"]!!.jsonArray
                assertTrue("cortar debe admitir opción sin token (QR)", sec.any { it.jsonObject.isEmpty() })
            }
        }
        // El SSE exige el query param token.
        val eventos = paths["/v1/eventos"]!!.jsonObject.values.first().jsonObject
        val tokenParam = eventos["parameters"]!!.jsonArray.first { it.jsonObject["name"]?.jsonPrimitive?.content == "token" }.jsonObject
        assertEquals(true, tokenParam["required"]?.jsonPrimitive?.content?.toBoolean())
    }

    /**
     * Caso negativo de auth: la respuesta de `iniciar` con `sesionActiva=false`
     * (sin token) sigue siendo un payload válido del contrato.
     */
    @Test
    fun iniciarSesionRechazadaSinTokenEsPayloadValido() {
        val resp = LanJson.decodeFromString<SesionIniciarResponse>("""{"sesionActiva":false}""")
        assertEquals(false, resp.sesionActiva)
        assertEquals(null, resp.token)
    }
}
