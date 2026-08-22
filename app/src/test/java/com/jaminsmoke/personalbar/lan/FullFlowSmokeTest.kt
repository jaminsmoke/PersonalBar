package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.InMemoryBarRepository
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.TicketEstado
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke E2E de la familia: valida el flujo completo de negocio LAN
 * (sesión → ronda → tickets → corte) como escenario secuencial.
 *
 * Mockea Identity (QR se verifica contra la lista blanca local).
 * Usa el contrato HTTP real de Bar (las mismas rutas que Commander llama).
 * Auth v0.2: `iniciar` emite el token y las privadas lo exigen (Bearer / ?token=).
 *
 * Cobertura:
 * - POST /v1/sesion (consulta lista blanca)
 * - POST /v1/sesion/iniciar (concesión de jornada + token)
 * - POST /v1/rondas (envío de ronda con camarero autenticado)
 * - POST /v1/tickets/{id}/preparado (preparación de ticket)
 * - POST /v1/tickets/{id}/recogido (recogida de ticket)
 * - GET /v1/estado (polling de estado final)
 * - POST /v1/sesion/cortar (fin de jornada, con token)
 */
class FullFlowSmokeTest {

    private val CAMARERO_ID = "11111111-1111-4111-8111-111111111111"
    private val CREDENCIAL_ID = "22222222-2222-4222-8222-222222222222"
    private val QR = "phid1:$CAMARERO_ID:$CREDENCIAL_ID:firmaTest"
    private val SECRETO = "secretoSmokeE2E"

    private fun repo() = InMemoryBarRepository(
        catalogoInicial = listOf(
            Producto("cana", "Caña", "Bebida", precio = 1.20),
            Producto("croquetas", "Croquetas", "Comida", precio = 5.00),
        ),
        camarerosIniciales = listOf(
            Camarero(id = CAMARERO_ID, nombre = "Lucía"),
        ),
    )

    private fun ronda() = Ronda(
        id = "r-smoke-1",
        mesaId = "T3",
        numero = 1,
        camarero = "Lucía",
        lineas = listOf(
            Linea("cana", "Caña", 2),
            Linea("croquetas", "Croquetas", 1),
        ),
    )

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) =
        header(HttpHeaders.Authorization, "Bearer $token")

    /** Inicia sesión con [QR] y devuelve el token v0.2. */
    private suspend fun HttpClient.iniciarYToken(): String {
        val resp = post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"$QR"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        return LanJson.decodeFromString<SesionIniciarResponse>(resp.bodyAsText()).token.orEmpty()
    }


    /**
     * Flujo completo: sesión → ronda → preparado → recogido → estado → corte.
     * Este es el escenario que Bar + Commander ejecutan en un local real.
     */
    @Test
    fun flujoCompletoSesionRondaTicketsCorte() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }

        // ── 1. Consulta de lista blanca (Commander verifica si el QR es aceptado) ──
        val sesionCheck = client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"$QR"}""")
        }
        assertEquals(HttpStatusCode.OK, sesionCheck.status)
        val sesionResp = LanJson.decodeFromString<SesionResponse>(sesionCheck.bodyAsText())
        assertTrue("Camarero debe ser admitido", sesionResp.admitido)
        assertEquals(CAMARERO_ID, sesionResp.camareroId)
        assertEquals("Lucía", sesionResp.nombre)

        // ── 2. Iniciar sesión (Commander pide jornada al nodo y recibe el token) ──
        val token = client.iniciarYToken()
        assertTrue("v0.2: iniciar debe emitir token", token.isNotBlank())
        assertTrue(repository.tieneSesionActiva(CAMARERO_ID))

        // ── 3. Enviar ronda (Commander crea ronda y la envía autenticada) ──
        val rondaResp = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Created, rondaResp.status)
        val tickets = LanJson.decodeFromString<List<com.jaminsmoke.personalbar.data.Ticket>>(
            rondaResp.bodyAsText()
        )
        assertEquals("Debe haber 2 tickets (bebida + comida)", 2, tickets.size)

        // Verificar colas: 1 en bebida, 1 en comida
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(1, repository.comidaQueue.value.size)

        // ── 4. Marcar ticket de bebida como preparado ──
        val ticketBebida = repository.bebidaQueue.value.first()
        val preparadoResp = client.post("/v1/tickets/${ticketBebida.id}/preparado") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Lucía"}""")
        }
        assertEquals(HttpStatusCode.OK, preparadoResp.status)
        assertEquals(TicketEstado.PREPARADO, repository.bebidaQueue.value.first().estado)
        assertEquals("Lucía", repository.bebidaQueue.value.first().preparadoPor)

        // ── 5. Marcar ticket de bebida como recogido ──
        val recogidoResp = client.post("/v1/tickets/${ticketBebida.id}/recogido") { bearer(token) }
        assertEquals(HttpStatusCode.OK, recogidoResp.status)
        assertTrue("Cola de bebida debe estar vacía tras recoger", repository.bebidaQueue.value.isEmpty())
        assertEquals(1, repository.servidos.value.size)
        assertEquals(TicketEstado.RECOGIDO, repository.servidos.value.first().estado)

        // ── 6. Verificar estado final (Commander hace polling) ──
        val estadoResp = client.get("/v1/estado") { bearer(token) }
        assertEquals(HttpStatusCode.OK, estadoResp.status)
        val estado = LanJson.decodeFromString<EstadoResponse>(estadoResp.bodyAsText())
        assertTrue("Cola de bebida vacía", estado.bebida.isEmpty())
        assertEquals("1 ticket servido", 1, estado.servidos.size)
        assertEquals("Cola de comida aún pendiente", 1, estado.comida.size)

        // ── 7. Cortar sesión (Commander fin de jornada, con token) ──
        val cortarResp = client.post("/v1/sesion/cortar") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, cortarResp.status)
        assertFalse("Sesión debe estar cortada", repository.tieneSesionActiva(CAMARERO_ID))
    }

    /**
     * Ronda con modificadores y nota: valida que el flujo preserva
     * la información completa desde Commander hasta el ticket.
     */
    @Test
    fun flujoConModificadoresYNota() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        // Ronda con nota y modificadores
        val ronda = Ronda(
            id = "r-mod-1",
            mesaId = "T5",
            numero = 1,
            camarero = "Lucía",
            lineas = listOf(
                Linea(
                    "cana", "Caña", 1,
                    nota = "sin espuma",
                    modificadores = listOf(
                        com.jaminsmoke.personalbar.data.ModificadorLinea(
                            grupo = "Punto",
                            opcion = "Al punto",
                            delta = 0.0,
                        )
                    ),
                )
            ),
        )

        val rondaResp = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda))
        }
        assertEquals(HttpStatusCode.Created, rondaResp.status)

        // Verificar que la nota y modificadores persisten en el ticket
        val ticket = repository.bebidaQueue.value.first()
        assertEquals("sin espuma", ticket.lineas.first().nota)
        assertEquals("Al punto", ticket.lineas.first().modificadores.first().opcion)

        // Preparar y recoger
        client.post("/v1/tickets/${ticket.id}/preparado") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Lucía"}""")
        }
        client.post("/v1/tickets/${ticket.id}/recogido") { bearer(token) }

        assertEquals(1, repository.servidos.value.size)
        assertEquals("sin espuma", repository.servidos.value.first().lineas.first().nota)
    }

    /**
     * Ronda duplicada es idempotente: el flujo completo tolera reenvíos.
     */
    @Test
    fun rondaDuplicadaEnFlujoNoDuplicaTickets() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val r = ronda()

        // Primera vez → Created
        val resp1 = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(r))
        }
        assertEquals(HttpStatusCode.Created, resp1.status)

        // Segunda vez → OK (idempotente), sin duplicar
        val resp2 = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(r))
        }
        assertEquals(HttpStatusCode.OK, resp2.status)
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(1, repository.comidaQueue.value.size)
    }

    /**
     * Heartbeat mantiene la sesión viva durante el flujo.
     */
    @Test
    fun heartbeatMantieneSesionVivaDuranteFlujo() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        // Heartbeat → OK (el camarero sale del token)
        val hb1 = client.post("/v1/heartbeat") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$CAMARERO_ID"}""")
        }
        assertEquals(HttpStatusCode.OK, hb1.status)
        assertTrue(repository.tieneSesionActiva(CAMARERO_ID))

        // Enviar ronda tras heartbeat
        val rondaResp = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Created, rondaResp.status)

        // Cortar
        client.post("/v1/sesion/cortar") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        // Heartbeat tras corte → 403 (sesión ya no activa aunque el token firme)
        val hb2 = client.post("/v1/heartbeat") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$CAMARERO_ID"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, hb2.status)
    }

    /**
     * Ronda sin sesión activa es rechazada (camarero en lista blanca sin jornada).
     */
    @Test
    fun rondaSinSesionActivaEsRechazada() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }

        // Solo consulta de sesión (no iniciar)
        client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"$QR"}""")
        }

        // Intentar ronda sin iniciar (sin token) → 401
        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
    }
}
