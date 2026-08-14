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
        val evento = SalaEvent.preparado("r1-barra", "Ana")
        val json = LanJson.encodeToString(evento)
        assertEquals(evento, LanJson.decodeFromString<SalaEvent>(json))
        assertEquals(SalaEvent.TIPO_PREPARADO, evento.tipo)
        assertEquals("Ana", evento.preparadoPor)
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
        )
        val json = LanJson.encodeToString(req)
        assertTrue(json.contains("\"nombre_mostrar\":\"La Terraza\""))
        val dec = LanJson.decodeFromString<RegistroNegocioRequest>(json)
        assertEquals("La Terraza", dec.nombreMostrar)
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
