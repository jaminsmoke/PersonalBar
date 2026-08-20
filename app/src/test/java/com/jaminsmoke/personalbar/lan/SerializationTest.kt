package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Invitacion
import com.jaminsmoke.personalbar.data.InvitacionEstado
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.RolCamarero
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.SalaEvent
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.TicketEstado
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    @Test
    fun rondaRoundTrip() {
        val ronda = Ronda("r1", "T3", 1, "Lucía", lineas = listOf(Linea("cana", "Caña", 2)))
        val json = LanJson.encodeToString(ronda)
        assertEquals(ronda, LanJson.decodeFromString<Ronda>(json))
    }

    @Test
    fun salaEventRoundTrip() {
        val ticket = Ticket(
            id = "r1-barra",
            rondaId = "r1",
            destino = Destino.BARRA,
            estado = TicketEstado.PREPARADO,
            preparadoPor = "Ana",
            numeroCola = 1,
            lineas = listOf(Linea("cana", "Caña", 2)),
        )
        val evento = SalaEvent.preparado(ticket, "T3", "Lucía")
        val json = LanJson.encodeToString(evento)
        assertEquals(evento, LanJson.decodeFromString<SalaEvent>(json))
        assertEquals(SalaEvent.TIPO_PREPARADO, evento.tipo)
        assertEquals("Ana", evento.preparadoPor)
        assertEquals("T3", evento.mesaId)
        assertEquals("Lucía", evento.camarero)
        assertEquals("2× Caña", evento.resumen)
        assertEquals(ticket, evento.ticket)
    }

    @Test
    fun salaEventLegacySinCamposNuevosDecodifica() {
        // Un evento emitido por una versión anterior (solo tipo/ticketId/preparadoPor)
        // debe decodificar con los campos nuevos en sus defaults (backward-compatible).
        val json = """{"tipo":"ticket.preparado","ticketId":"r1-barra","preparadoPor":"Ana"}"""
        val evento = LanJson.decodeFromString<SalaEvent>(json)
        assertEquals(SalaEvent.TIPO_PREPARADO, evento.tipo)
        assertEquals("r1-barra", evento.ticketId)
        assertEquals("Ana", evento.preparadoPor)
        assertNull(evento.mesaId)
        assertNull(evento.camarero)
        assertNull(evento.ticket)
        assertEquals("", evento.resumen)
        assertEquals(SalaEvent.VERSION, evento.version)
    }

    @Test
    fun ticketConPreparadorRoundTrip() {
        val ticket = Ticket(
            id = "t1",
            rondaId = "r1",
            destino = Destino.BARRA,
            estado = TicketEstado.PREPARADO,
            preparadoPor = "Ana",
            lineas = listOf(Linea("cana", "Caña", 2)),
        )
        val json = LanJson.encodeToString(ticket)
        assertEquals(ticket, LanJson.decodeFromString<Ticket>(json))
    }

    @Test
    fun invitacionRoundTrip() {
        val invitacion = Invitacion(
            id = "inv-1",
            email = "ana@example.com",
            rol = "staff",
            estado = InvitacionEstado.PENDIENTE,
            expiraEn = "2026-08-17T12:00:00Z",
        )
        val json = LanJson.encodeToString(invitacion)
        assertEquals(invitacion, LanJson.decodeFromString<Invitacion>(json))
    }

    @Test
    fun identityInvitacionRoundTrip() {
        val invitacion = IdentityInvitacion(
            id = "11111111-1111-4111-8111-111111111111",
            email = "ana@example.com",
            rol = "staff",
            estado = "pendiente",
            expiraEn = "2026-08-17T12:00:00Z",
        )
        val json = LanJson.encodeToString(invitacion)
        // El campo del server es snake_case: expira_en
        assertTrue(json.contains("\"expira_en\""))
        assertEquals(invitacion, LanJson.decodeFromString<IdentityInvitacion>(json))
    }

    @Test
    fun identityLoginResponseCapturaPerfilCuenta() {
        val json = """
            {"token":"tok-1","cuenta":{"id":"c-1","email":"negocio@example.com","nombre_mostrar":"La Terraza"}}
        """.trimIndent()
        val resp = LanJson.decodeFromString<IdentityLoginResponse>(json)
        assertEquals("tok-1", resp.token)
        assertEquals("La Terraza", resp.cuenta.nombreMostrar)
        assertEquals("negocio@example.com", resp.cuenta.email)
    }

    @Test
    fun registroNegocioRequestSerializaSnakeCase() {
        val req = RegistroNegocioRequest(
            nombreMostrar = "La Terraza",
            email = "negocio@example.com",
            password = "secret123",
            tipoEstablecimiento = "bar",
        )
        val json = LanJson.encodeToString(req)
        assertTrue(json.contains("\"nombre_mostrar\":\"La Terraza\""))
        assertTrue(json.contains("\"tipo_establecimiento\":\"bar\""))
        val dec = LanJson.decodeFromString<RegistroNegocioRequest>(json)
        assertEquals("La Terraza", dec.nombreMostrar)
    }

    @Test
    fun identityCuentaNegocioCapturaTipoLogo() {
        val json = """
            {"token":"tok-1","cuenta":{"id":"c-1","email":"negocio@example.com","nombre_mostrar":"La Terraza","tipo_establecimiento":"bar","logo_url":"/v1/auth/negocio/me/logo"}}
        """.trimIndent()
        val resp = LanJson.decodeFromString<IdentityLoginResponse>(json)
        assertEquals("bar", resp.cuenta.tipoEstablecimiento)
        assertEquals("/v1/auth/negocio/me/logo", resp.cuenta.logoUrl)
    }

    @Test
    fun identityMembresiaRoundTrip() {
        val membresia = IdentityMembresia(
            id = "m-1",
            establecimientoId = "e-1",
            camareroId = "c-1",
            rol = "staff",
            estado = "activa",
        )
        val json = LanJson.encodeToString(membresia)
        assertTrue(json.contains("\"camarero_id\""))
        assertEquals(membresia, LanJson.decodeFromString<IdentityMembresia>(json))
    }

    @Test
    fun camareroRoundTrip() {
        val camarero = Camarero(
            id = "11111111-1111-4111-8111-111111111111",
            nombre = "Ana",
            email = "ana@example.com",
            rol = RolCamarero.DUENO,
            credencialId = "22222222-2222-4222-8222-222222222222",
        )
        val json = LanJson.encodeToString(camarero)
        assertEquals(camarero, LanJson.decodeFromString<Camarero>(json))
    }

    @Test
    fun fondoUpdateBodyAsignaCatalogo() {
        val json = fondoUpdateBody(FondoSeccion.HORARIO, "estate-horario-1")
        assertEquals(
            """{"horario": {"fuente": "catalogo", "id": "estate-horario-1"}}""",
            json,
        )
    }

    @Test
    fun fondoUpdateBodyConNullVuelveAlDefault() {
        val json = fondoUpdateBody(FondoSeccion.CARTA, null)
        assertEquals("""{"carta": null}""", json)
    }

    @Test
    fun fondosAsignadosDecodificaContrato() {
        // Respuesta real del contrato Identity #140 (snake_case no aplica: los
        // slots son claves planas; `fuente`/`id`/`url` tal cual).
        val json = """
            {
              "inicio": {"fuente": "catalogo", "id": "estate-inicio-1", "url": "https://web.example/stubs/fondos/estate-inicio-1.webp"},
              "horario": {"fuente": "upload", "id": "img-1", "url": "/v1/establecimientos/e1/fondos/horario"},
              "carta": {"fuente": "catalogo", "id": "estate-carta-2", "url": "https://web.example/stubs/fondos/estate-carta-2.webp"},
              "equipo": {"fuente": "catalogo", "id": "estate-equipo-1", "url": "https://web.example/stubs/fondos/estate-equipo-1.webp"},
              "contacto": {"fuente": "catalogo", "id": "estate-contacto-1", "url": "https://web.example/stubs/fondos/estate-contacto-1.webp"}
            }
        """.trimIndent()
        val asignados = LanJson.decodeFromString<FondosAsignadosResponse>(json)
        assertEquals("catalogo", asignados.inicio.fuente)
        assertEquals("estate-inicio-1", asignados.inicio.id)
        assertEquals("upload", asignados.horario.fuente)
        assertEquals("estate-carta-2", asignados.carta.id)
        assertEquals("estate-carta-2", asignados.de(FondoSeccion.CARTA).id)
        assertNull(asignados.contacto.id?.takeIf { it.isEmpty() })
    }

    @Test
    fun catalogoFondosDecodificaMiniaturas() {
        val json = """
            [
              {"id": "estate-inicio-1", "seccion": "inicio", "url": "https://web.example/stubs/fondos/estate-inicio-1.webp"},
              {"id": "estate-inicio-2", "seccion": "inicio", "url": "https://web.example/stubs/fondos/estate-inicio-2.webp"}
            ]
        """.trimIndent()
        val catalogo = LanJson.decodeFromString<List<CatalogoFondoItem>>(json)
        assertEquals(2, catalogo.size)
        assertEquals("inicio", catalogo.first().seccion)
        assertEquals("estate-inicio-1", catalogo.first().id)
    }

    @Test
    fun estadoResponseRoundTrip() {
        val estado = EstadoResponse(
            version = "0.1",
            establecimiento = Establecimiento("local-1", "La Terraza"),
            salas = listOf(Sala("sala-terraza", "Terraza", 1)),
            bebida = emptyList(),
            comida = emptyList(),
            servidos = emptyList(),
            mesas = listOf(Mesa(id = "mesa-1", salaId = "sala-terraza", indiceZona = 3)),
        )
        val json = LanJson.encodeToString(estado)
        assertEquals(estado, LanJson.decodeFromString<EstadoResponse>(json))
    }
}
