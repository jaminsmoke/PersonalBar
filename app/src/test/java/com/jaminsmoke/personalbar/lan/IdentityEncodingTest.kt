package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Ronda
import java.io.ByteArrayInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityEncodingTest {

    @Test
    fun readBodyUtf8DecodificaAcentos() {
        val texto = "García — caña × 2"
        val bytes = texto.toByteArray(Charsets.UTF_8)
        assertEquals(texto, ByteArrayInputStream(bytes).readBodyUtf8())
    }

    @Test
    fun rondaConCamareroAcentuadoRoundTrip() {
        val ronda = Ronda("r1", "T3", 1, "Lucía García", lineas = listOf(Linea("cana", "Caña", 2)))
        val json = LanJson.encodeToString(ronda)
        assertEquals(ronda, LanJson.decodeFromString<Ronda>(json))
    }

    @Test
    fun identityCamareroConAcentosDecodifica() {
        val json = """{"id":"c-1","nombre":"Lucía","apellidos":"García","email":"lucia@example.com"}"""
        val camarero = LanJson.decodeFromString<IdentityCamarero>(json)
        assertEquals("Lucía", camarero.nombre)
        assertEquals("García", camarero.apellidos)
    }
}
