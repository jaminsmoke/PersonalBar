package com.jaminsmoke.personalbar.lan

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataOriginTest {

    @Test
    fun desdeApiMapeaLosTresValores() {
        assertEquals(DataOrigin.REAL, DataOrigin.desdeApi("real"))
        assertEquals(DataOrigin.TEST, DataOrigin.desdeApi("test"))
        assertEquals(DataOrigin.DEMO, DataOrigin.desdeApi("demo"))
    }

    @Test
    fun desdeApiToleraNullYValoresDesconocidos() {
        assertNull(DataOrigin.desdeApi(null))
        assertNull(DataOrigin.desdeApi(""))
        assertNull(DataOrigin.desdeApi("futuro"))
    }

    @Test
    fun perfilSinDataOriginDecodificaConNull() {
        // Respuesta de una versión anterior de Identity (sin el campo): debe
        // decodificar sin error con dataOrigin null (equivalente a real).
        val json = """
            {"token":"tok-1","cuenta":{"id":"c-1","email":"negocio@example.com","nombre_mostrar":"La Terraza"}}
        """.trimIndent()
        val resp = LanJson.decodeFromString<IdentityLoginResponse>(json)
        assertEquals("La Terraza", resp.cuenta.nombreMostrar)
        assertNull(resp.cuenta.dataOrigin)
    }

    @Test
    fun perfilConDataOriginDecodifica() {
        val json = """
            {"token":"tok-1","cuenta":{"id":"c-1","email":"n@example.com","nombre_mostrar":"T","data_origin":"test"}}
        """.trimIndent()
        val resp = LanJson.decodeFromString<IdentityLoginResponse>(json)
        assertEquals("test", resp.cuenta.dataOrigin)
    }

    @Test
    fun registroRequestOmiteDataOriginCuandoRealONull() {
        val req = RegistroNegocioRequest(
            nombreMostrar = "La Terraza",
            email = "negocio@example.com",
            password = "secret123",
            tipoEstablecimiento = "bar",
        )
        val json = LanJson.encodeToString(req)
        assertFalse(json.contains("data_origin"))
    }

    @Test
    fun registroRequestSerializaDataOriginCuandoTest() {
        val req = RegistroNegocioRequest(
            nombreMostrar = "La Terraza",
            email = "negocio@example.com",
            password = "secret123",
            dataOrigin = "test",
        )
        val json = LanJson.encodeToString(req)
        assertTrue(json.contains("\"data_origin\":\"test\""))
    }

    @Test
    fun establecimientoSinDataOriginDecodificaConNull() {
        val json = """[{"id":"e-1","nombre":"Barra"}]"""
        val lista = LanJson.decodeFromString<List<IdentityEstablecimiento>>(json)
        assertEquals(1, lista.size)
        assertEquals("e-1", lista[0].id)
        assertNull(lista[0].dataOrigin)
    }

    @Test
    fun establecimientoConDataOriginDecodifica() {
        val json = """{"id":"e-1","nombre":"Barra","data_origin":"demo"}"""
        val est = LanJson.decodeFromString<IdentityEstablecimiento>(json)
        assertEquals("demo", est.dataOrigin)
    }

    @Test
    fun registroResponseSinDataOriginDecodificaConNull() {
        val json = """{"id":"c-1"}"""
        val resp = LanJson.decodeFromString<IdentityRegistroResponse>(json)
        assertEquals("c-1", resp.id)
        assertNull(resp.dataOrigin)
    }
}
