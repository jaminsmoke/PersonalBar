package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.InMemoryBarRepository
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.ModificadorLinea
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.Zona
import com.jaminsmoke.personalbar.data.ZonaColor
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
import org.junit.Assert.assertFalse
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
    fun estadoDevuelveZonasConGeometria() = testApplication {
        val repository = InMemoryBarRepository(
            zonasIniciales = listOf(
                Zona("zona-1", "sala-terraza", "Terraza Test", 40f, 80f, 400f, 240f, ZonaColor.VERDE),
            ),
        )
        application { barModule(repository) }

        val estado = LanJson.decodeFromString<EstadoResponse>(client.get("/v1/estado").bodyAsText())
        assertEquals(1, estado.zonas.size)
        assertEquals("zona-1", estado.zonas.single().id)
        assertEquals("Terraza Test", estado.zonas.single().nombre)
        assertEquals(ZonaColor.VERDE, estado.zonas.single().color)
        assertTrue(estado.zonas.single().ancho > 0f)
        assertTrue(estado.zonas.single().alto > 0f)
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

    @Test
    fun sesionActivaAdmitidaSinAlta() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository) }

        val resp = client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val sesion = LanJson.decodeFromString<SesionResponse>(resp.bodyAsText())
        assertEquals(true, sesion.admitido)
        assertEquals(id, sesion.camareroId)
        assertEquals("luciaTest", sesion.nombre)
        assertEquals(1, repository.camareros.value.size)
        assertEquals(false, repository.camareros.value[0].deServicio)
    }

    @Test
    fun sesionRevocadaNoAdmitida() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(
                Camarero(id = id, nombre = "luciaTest", estado = CamareroEstado.REVOCADA),
            ),
        )
        application { barModule(repository) }

        val resp = client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        val sesion = LanJson.decodeFromString<SesionResponse>(resp.bodyAsText())
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(false, sesion.admitido)
        assertEquals(id, sesion.camareroId)
    }

    @Test
    fun sesionQrInvalidoBadRequest() = testApplication {
        val repository = repo()
        application { barModule(repository) }
        val resp = client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"no-es-phid1"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(repository.camareros.value.isEmpty())
    }

    @Test
    fun rondaSinSesionPreviaSigueCreando() = testApplication {
        val repository = repo()
        application { barModule(repository) }
        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun cartaDevuelveCatalogo() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        val resp = client.get("/v1/carta")
        assertEquals(HttpStatusCode.OK, resp.status)
        val carta = LanJson.decodeFromString<CartaResponse>(resp.bodyAsText())
        assertEquals(CartaResponse.CARTA_SCHEMA, carta.schema)
        assertEquals(2, carta.productos.size)
        assertEquals(setOf("cana", "croquetas"), carta.productos.map { it.id }.toSet())
        // Incluye el flag `disponible` que Commander cachea para ocultar en comanda.
        assertTrue(carta.productos.all { it.disponible })
    }

    @Test
    fun cartaDevuelveGruposModificadorYSubfamilia() = testApplication {
        val repository = InMemoryBarRepository(
            catalogoInicial = listOf(
                Producto("cana", "Caña", "Bebida", subfamilia = "Zero", permiteNota = true),
            )
        )
        assertTrue(repository.crearGrupoModificador("Punto", multiple = false, obligatorio = true))
        val grupoId = repository.gruposModificador.value.single().id
        assertTrue(repository.crearOpcionModificador(grupoId, "Al punto", 0.0, "al punto"))
        assertTrue(repository.asignarGrupoProducto("cana", grupoId))
        application { barModule(repository) }

        val carta = LanJson.decodeFromString<CartaResponse>(
            client.get("/v1/carta").bodyAsText()
        )
        val producto = carta.productos.single()
        assertEquals("Zero", producto.subfamilia)
        assertTrue(producto.permiteNota)
        assertEquals(listOf(grupoId), producto.grupos)

        val grupo = carta.gruposModificador.single()
        assertEquals("Punto", grupo.nombre)
        assertTrue(grupo.obligatorio)
        assertFalse(grupo.multiple)
        assertEquals(1, grupo.opciones.size)
        assertEquals("Al punto", grupo.opciones.single().nombre)
        assertEquals("al punto", grupo.opciones.single().alias)
    }

    @Test
    fun postRondaPersisteNotaYModificadores() = testApplication {
        val repository = repo()
        application { barModule(repository) }
        val ronda = Ronda(
            "r-nota", "T3", 1,
            lineas = listOf(
                Linea(
                    "cana", "Caña", 1,
                    nota = "sin espuma",
                    modificadores = listOf(ModificadorLinea("Punto", "Al punto", 0.0)),
                )
            ),
        )

        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda))
        }
        assertEquals(HttpStatusCode.Created, resp.status)

        val persistida = repository.rondas.value.single()
        val linea = persistida.lineas.single()
        assertEquals("sin espuma", linea.nota)
        assertEquals(1, linea.modificadores.size)
        assertEquals("Punto", linea.modificadores.single().grupo)
        assertEquals("Al punto", linea.modificadores.single().opcion)
        // La línea llega al ticket BARRA conservando nota + modificadores.
        val ticket = repository.bebidaQueue.value.single()
        assertEquals("sin espuma", ticket.lineas.single().nota)
        assertEquals("Al punto", ticket.lineas.single().modificadores.single().opcion)
    }

    @Test
    fun iniciarSesionConcedeJornada() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository) }

        val resp = client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"sesionActiva\":true"))
        assertTrue(repository.tieneSesionActiva(id))
    }

    @Test
    fun iniciarSesionRechazaDesconocido() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        val resp = client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:11111111-1111-4111-8111-111111111111:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun cortarSesionBajaJornada() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository) }
        client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }

        val resp = client.post("/v1/sesion/cortar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertFalse(repository.tieneSesionActiva(id))
    }

    @Test
    fun heartbeat403SinSesionY200ConSesion() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository) }

        val sinSesion = client.post("/v1/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$id"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, sinSesion.status)

        client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        val conSesion = client.post("/v1/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$id"}""")
        }
        assertEquals(HttpStatusCode.OK, conSesion.status)
    }

    @Test
    fun rondaConContratadoSinSesionDevuelve403() = testApplication {
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = "c-1", nombre = "Lucía")),
        )
        application { barModule(repository) }

        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
    }

    @Test
    fun rondaConContratadoActivoSeCrea() = testApplication {
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = "c-1", nombre = "Lucía")),
        )
        repository.iniciarSesion("c-1")
        application { barModule(repository) }

        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        assertEquals(1, repository.bebidaQueue.value.size)
    }

    @Test
    fun cartaWrapperJsonTieneProductos() = testApplication {
        val repository = repo()
        application { barModule(repository) }

        val body = client.get("/v1/carta").bodyAsText()
        assertTrue(body.contains("\"productos\":["))
        assertTrue(body.contains("\"precio\":"))
        assertTrue(body.contains("\"disponible\":"))
    }
}
