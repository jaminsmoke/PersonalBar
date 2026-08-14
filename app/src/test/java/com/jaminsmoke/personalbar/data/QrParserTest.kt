package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrParserTest {

    private val camareroId = "11111111-1111-4111-8111-111111111111"
    private val credencialId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun parseaPhid1Valido() {
        val phid = QrParser.parsear("phid1:$camareroId:$credencialId:abc123")
        assertNotNull(phid)
        assertEquals(camareroId, phid!!.camareroId)
        assertEquals(credencialId, phid.credencialId)
        assertEquals("abc123", phid.firma)
    }

    @Test
    fun rechazaPrefijoIncorrecto() {
        assertNull(QrParser.parsear("otro:$camareroId:$credencialId:abc123"))
    }

    @Test
    fun rechazaNumeroDePartesIncorrecto() {
        assertNull(QrParser.parsear("phid1:$camareroId:$credencialId"))
        assertNull(QrParser.parsear("phid1:$camareroId:$credencialId:abc123:extra"))
    }

    @Test
    fun rechazaUuidInvalido() {
        assertNull(QrParser.parsear("phid1:no-uuid:$credencialId:abc123"))
        assertNull(QrParser.parsear("phid1:$camareroId:no-uuid:abc123"))
    }

    @Test
    fun rechazaFirmaVacia() {
        assertNull(QrParser.parsear("phid1:$camareroId:$credencialId:"))
    }
}
