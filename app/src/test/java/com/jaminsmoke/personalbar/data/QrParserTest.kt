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

    // ── Forma URL (https://...?qr=phid1:...) ─────────────────────────────────

    @Test
    fun parseaUrlConQrEncoded() {
        // Formato que genera Identity: ?qr=phid1%3A... (quote URL-encoded)
        val encoded = "phid1%3A$camareroId%3A$credencialId%3Aabc123"
        val phid = QrParser.parsear("https://ficha.example/ficha?qr=$encoded")
        assertNotNull(phid)
        assertEquals(camareroId, phid!!.camareroId)
        assertEquals(credencialId, phid.credencialId)
        assertEquals("abc123", phid.firma)
    }

    @Test
    fun parseaUrlConQrCrudo() {
        // Algunos lectores podrían pasar el payload sin URL-encodear.
        val phid = QrParser.parsear("https://ficha.example/ficha?qr=phid1:$camareroId:$credencialId:abc123")
        assertNotNull(phid)
        assertEquals(camareroId, phid!!.camareroId)
    }

    @Test
    fun rechazaUrlSinParametroQr() {
        assertNull(QrParser.parsear("https://ficha.example/ficha?token=abc"))
        assertNull(QrParser.parsear("https://ficha.example/ficha"))
    }

    @Test
    fun rechazaUrlConQrNoPhid1() {
        assertNull(QrParser.parsear("https://ficha.example/ficha?qr=otro:1:2:3"))
        assertNull(QrParser.parsear("https://ficha.example/ficha?qr=phid1:no-uuid:$credencialId:abc123"))
    }
}
