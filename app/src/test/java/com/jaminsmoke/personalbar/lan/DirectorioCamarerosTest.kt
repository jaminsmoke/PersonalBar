package com.jaminsmoke.personalbar.lan

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectorioCamarerosTest {

    @Test
    fun directorioDecodificaConLibreYFoto() {
        val json = """
            {"id":"c-1","nombre":"Ana","apellidos":"García","nick":"Anita",
             "foto_url":"/v1/camareros/ficha/foto/c-1","libre":true,"visibilidad":"siempre"}
        """.trimIndent()
        val entry = LanJson.decodeFromString<IdentityCamareroDirectorio>(json)
        assertEquals("c-1", entry.id)
        assertEquals("Ana", entry.nombre)
        assertEquals("García", entry.apellidos)
        assertEquals("Anita", entry.nick)
        assertEquals("/v1/camareros/ficha/foto/c-1", entry.fotoUrl)
        assertEquals(true, entry.libre)
        assertEquals("siempre", entry.visibilidad)
    }

    @Test
    fun directorioDecodificaSinFotoYConOcupado() {
        val json = """
            [{"id":"c-2","nombre":"Luis","apellidos":"Pérez","libre":false,"visibilidad":"solo_libre"}]
        """.trimIndent()
        val lista = LanJson.decodeFromString<List<IdentityCamareroDirectorio>>(json)
        assertEquals(1, lista.size)
        assertEquals(null, lista[0].fotoUrl)
        assertEquals(null, lista[0].nick)
        assertEquals(false, lista[0].libre)
        assertEquals("solo_libre", lista[0].visibilidad)
    }

    @Test
    fun nombreCompletoEIniciales() {
        val conNick = IdentityCamareroDirectorio(id = "c-1", nombre = "Ana", apellidos = "García", nick = "Anita")
        assertEquals("Ana García", conNick.nombreCompleto)
        assertEquals("AG", conNick.iniciales)

        val sinNick = IdentityCamareroDirectorio(id = "c-2", nombre = "Luis", apellidos = "Pérez")
        assertEquals("Luis Pérez", sinNick.nombreCompleto)
        assertEquals("LP", sinNick.iniciales)
    }
}
