package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Establecimiento
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
