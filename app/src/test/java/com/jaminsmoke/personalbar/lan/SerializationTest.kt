package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.SalaEvent
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
        val evento = SalaEvent.listo("r1-barra")
        val json = LanJson.encodeToString(evento)
        assertEquals(evento, LanJson.decodeFromString<SalaEvent>(json))
        assertEquals(SalaEvent.TIPO_LISTO, evento.tipo)
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
            mesas = listOf(Mesa(salaId = "sala-terraza", indiceZona = 3)),
        )
        val json = LanJson.encodeToString(estado)
        assertEquals(estado, LanJson.decodeFromString<EstadoResponse>(json))
    }
}
