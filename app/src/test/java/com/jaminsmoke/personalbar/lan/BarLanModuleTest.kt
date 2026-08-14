package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.InMemoryBarRepository
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.TicketEstado
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarLanModuleTest {

    private fun repo() = InMemoryBarRepository(
        catalogoInicial = listOf(
            Producto("cana", "Caña", "Bebida"),
            Producto("croquetas", "Croquetas", "Comida"),
        )
    )

    private fun ronda() = Ronda(
        "r1", "T3", 1, "Lucía",
        lineas = listOf(Linea("cana", "Caña", 2), Linea("croquetas", "Croquetas", 1)),
    )

    @Test
    fun healthRespondeOk() = testApplication {
        val repository = repo()
        application { barModule(repository) }
        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun healthIncluyeEstablecimientoYMantieneSala() = testApplication {
        val repository = repo()
        application { barModule(repository) }
        val body = client.get("/health").bodyAsText()
        assertTrue(body.contains("\"establecimiento\":\"Mi local\""))
        assertTrue(body.contains("\"sala\":\"Mi local\""))
    }

    @Test
    fun postRondaCreaDosTickets() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val tickets = LanJson.decodeFromString<List<Ticket>>(resp.bodyAsText())
        assertEquals(2, tickets.size)
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(1, repository.comidaQueue.value.size)
    }

    @Test
    fun postRondaDuplicadaEsIdempotente() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val resp2 = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.OK, resp2.status)
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(1, repository.comidaQueue.value.size)
    }

    @Test
    fun estadoDevuelveColas() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val resp = client.get("/v1/estado")
        assertEquals(HttpStatusCode.OK, resp.status)
        val estado = LanJson.decodeFromString<EstadoResponse>(resp.bodyAsText())
        assertEquals("local-1", estado.establecimiento.idEstable)
        assertEquals(1, estado.bebida.size)
        assertEquals(1, estado.comida.size)
        assertTrue(estado.salas.isEmpty())
    }

    @Test
    fun preparadoMarcaTicketConPreparador() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        val resp = client.post("/v1/tickets/$ticketId/preparado") {
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Ana"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(TicketEstado.PREPARADO, repository.bebidaQueue.value[0].estado)
        assertEquals("Ana", repository.bebidaQueue.value[0].preparadoPor)
    }

    @Test
    fun preparadoSinPreparadorDevuelveBadRequest() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        val resp = client.post("/v1/tickets/$ticketId/preparado")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertEquals(TicketEstado.PENDIENTE, repository.bebidaQueue.value[0].estado)
    }

    @Test
    fun recogidoSacaDeLaCola() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        client.post("/v1/tickets/$ticketId/preparado") {
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Ana"}""")
        }
        val resp = client.post("/v1/tickets/$ticketId/recogido")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
        assertEquals(1, repository.servidos.value.size)
        assertEquals(TicketEstado.RECOGIDO, repository.servidos.value[0].estado)
    }
}
