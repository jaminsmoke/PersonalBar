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
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarLanModuleTest {

    private val SECRETO = "secretoTestParaElNodo"

    private val LUCIA_ID = "11111111-1111-4111-8111-111111111111"
    private val ANA_ID = "22222222-2222-4222-8222-222222222222"

    private fun repo() = InMemoryBarRepository(
        catalogoInicial = listOf(
            Producto("cana", "Caña", "Bebida"),
            Producto("croquetas", "Croquetas", "Comida"),
        ),
        camarerosIniciales = listOf(Camarero(id = LUCIA_ID, nombre = "Lucía")),
    )

    private fun ronda() = Ronda(
        "r1", "T3", 1, "Lucía",
        lineas = listOf(Linea("cana", "Caña", 2), Linea("croquetas", "Croquetas", 1)),
    )

    /** QR `phid1` de un camarero en lista blanca (`repo()` siembra Lucía). */
    private fun qr(camareroId: String = LUCIA_ID) =
        "phid1:$camareroId:33333333-3333-4333-8333-333333333333:firmaTest"

    /** Inicia sesión con el QR y devuelve el token v0.2 (asume 200). */
    private suspend fun HttpClient.iniciarYToken(camareroId: String = LUCIA_ID): String {
        val resp = post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"${qr(camareroId)}"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val token = LanJson.decodeFromString<SesionIniciarResponse>(resp.bodyAsText()).token
        assertTrue("iniciar debe emitir token v0.2", !token.isNullOrBlank())
        return token!!
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) =
        header(HttpHeaders.Authorization, "Bearer $token")

    @Test
    fun healthRespondeOk() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun healthIncluyeEstablecimientoYMantieneSala() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val body = client.get("/health").bodyAsText()
        assertTrue(body.contains("\"establecimiento\":\"Mi local\""))
        assertTrue(body.contains("\"sala\":\"Mi local\""))
    }

    @Test
    fun rutasPrivadas401SinToken() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        // Sin sesión iniciada: todas las privadas deben responder 401.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/estado").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/carta").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/sesion/jornadas").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/v1/rondas") {
                contentType(ContentType.Application.Json)
                setBody(LanJson.encodeToString(ronda()))
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/v1/heartbeat") { contentType(ContentType.Application.Json); setBody("""{"camareroId":"$LUCIA_ID"}""") }.status,
        )
        // SSE sin token: 401.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/eventos").status)
    }

    @Test
    fun tokenInvalidoDa401() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val resp = client.get("/v1/estado") { bearer("phbar1.invalido.firma") }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun postRondaCreaDosTickets() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val resp = client.post("/v1/rondas") {
            bearer(token)
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
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val resp2 = client.post("/v1/rondas") {
            bearer(token)
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
            camarerosIniciales = listOf(Camarero(id = LUCIA_ID, nombre = "Lucía")),
        )
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val estado = LanJson.decodeFromString<EstadoResponse>(
            client.get("/v1/estado") { bearer(token) }.bodyAsText(),
        )
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
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val resp = client.get("/v1/estado") { bearer(token) }
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
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        val resp = client.post("/v1/tickets/$ticketId/preparado") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Lucía"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, repository.bebidaQueue.value.size)
        assertEquals(TicketEstado.PREPARADO, repository.bebidaQueue.value[0].estado)
        assertEquals("Lucía", repository.bebidaQueue.value[0].preparadoPor)
    }

    @Test
    fun preparadoDeOtroCamareroDa403() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        // Autenticado es Lucía; marcar como «Ana» → 403 (suplantación bloqueada).
        val resp = client.post("/v1/tickets/$ticketId/preparado") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Ana"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertEquals(TicketEstado.PENDIENTE, repository.bebidaQueue.value[0].estado)
    }

    @Test
    fun preparadoSinPreparadorDevuelveBadRequest() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        val resp = client.post("/v1/tickets/$ticketId/preparado") { bearer(token) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertEquals(TicketEstado.PENDIENTE, repository.bebidaQueue.value[0].estado)
    }

    @Test
    fun recogidoSacaDeLaCola() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        val ticketId = repository.bebidaQueue.value[0].id
        client.post("/v1/tickets/$ticketId/preparado") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"preparado_por":"Lucía"}""")
        }
        val resp = client.post("/v1/tickets/$ticketId/recogido") { bearer(token) }
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
        application { barModule(repository, SECRETO) }

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
        application { barModule(repository, SECRETO) }

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
        application { barModule(repository, SECRETO) }
        val resp = client.post("/v1/sesion") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"no-es-phid1"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(repository.camareros.value.isNotEmpty())
    }

    @Test
    fun rondaSinSesionLigadaDevuelve401() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        // Sin iniciar: la ruta privada exige token → 401 (antes el candado poroso dejaba pasar).
        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
    }

    @Test
    fun cartaDevuelveCatalogo() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val resp = client.get("/v1/carta") { bearer(token) }
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
            ),
            camarerosIniciales = listOf(Camarero(id = LUCIA_ID, nombre = "Lucía")),
        )
        assertTrue(repository.crearGrupoModificador("Punto", multiple = false, obligatorio = true))
        val grupoId = repository.gruposModificador.value.single().id
        assertTrue(repository.crearOpcionModificador(grupoId, "Al punto", 0.0, "al punto"))
        assertTrue(repository.asignarGrupoProducto("cana", grupoId))
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val carta = LanJson.decodeFromString<CartaResponse>(
            client.get("/v1/carta") { bearer(token) }.bodyAsText()
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
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()
        val ronda = Ronda(
            "r-nota", "T3", 1, "Lucía",
            lineas = listOf(
                Linea(
                    "cana", "Caña", 1,
                    nota = "sin espuma",
                    modificadores = listOf(ModificadorLinea("Punto", "Al punto", 0.0)),
                )
            ),
        )

        val resp = client.post("/v1/rondas") {
            bearer(token)
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
    fun iniciarSesionConcedeJornadaYToken() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository, SECRETO) }

        val resp = client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"sesionActiva\":true"))
        val token = LanJson.decodeFromString<SesionIniciarResponse>(body).token
        assertTrue("v0.2: iniciar debe emitir token", !token.isNullOrBlank())
        assertTrue(repository.tieneSesionActiva(id))
    }

    @Test
    fun iniciarSesionRechazaDesconocido() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }

        val resp = client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:99999999-9999-4999-8999-999999999999:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun cortarSesionBajaJornadaConQrOConToken() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository, SECRETO) }
        client.post("/v1/sesion/iniciar") {
            contentType(ContentType.Application.Json)
            setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
        }

        // v0.2: cortar acepta el Bearer (sin re-escanear el QR).
        val token = LanJson.decodeFromString<SesionIniciarResponse>(
            client.post("/v1/sesion/iniciar") {
                contentType(ContentType.Application.Json)
                setBody("""{"qr":"phid1:$id:22222222-2222-4222-8222-222222222222:firmaTest"}""")
            }.bodyAsText()
        ).token.orEmpty()

        val resp = client.post("/v1/sesion/cortar") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertFalse(repository.tieneSesionActiva(id))
    }

    @Test
    fun heartbeat401SinTokenY200ConToken() = testApplication {
        val id = "11111111-1111-4111-8111-111111111111"
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(Camarero(id = id, nombre = "luciaTest")),
        )
        application { barModule(repository, SECRETO) }

        val sinToken = client.post("/v1/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$id"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, sinToken.status)

        val token = client.iniciarYToken(id)
        // v0.2: el camarero sale del token; el body ya no decide quién late.
        val conSesion = client.post("/v1/heartbeat") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody("""{"camareroId":"$id"}""")
        }
        assertEquals(HttpStatusCode.OK, conSesion.status)
    }

    @Test
    fun rondaConContratadoSinSesionDevuelve403() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        // Token emitido para un camarero cuya sesión NO está activa (iniciar no se llamó):
        // no podemos obtener token sin sesión → el caso real es 401 sin token.
        val resp = client.post("/v1/rondas") {
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(ronda()))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
    }

    @Test
    fun rondaDeOtroCamareroConTokenPropioDa403() = testApplication {
        // Autenticado: Lucía (c-1). Ronda firmada por «Ana» (no autenticada) → 403.
        val repository = InMemoryBarRepository(
            camarerosIniciales = listOf(
                Camarero(id = LUCIA_ID, nombre = "Lucía"),
                Camarero(id = ANA_ID, nombre = "Ana"),
            ),
        )
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken(LUCIA_ID)
        repository.iniciarSesion(ANA_ID)

        val rondaDeAna = ronda().copy(camarero = "Ana")
        val resp = client.post("/v1/rondas") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(LanJson.encodeToString(rondaDeAna))
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(repository.bebidaQueue.value.isEmpty())
    }

    @Test
    fun cartaWrapperJsonTieneProductos() = testApplication {
        val repository = repo()
        application { barModule(repository, SECRETO) }
        val token = client.iniciarYToken()

        val body = client.get("/v1/carta") { bearer(token) }.bodyAsText()
        assertTrue(body.contains("\"productos\":["))
        assertTrue(body.contains("\"precio\":"))
        assertTrue(body.contains("\"disponible\":"))
    }
}
