package com.jaminsmoke.personalbar.lan

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityClientsTest {

    @Test
    fun negocioApuntaAlServicioNegocio() {
        assertTrue(IdentityNegocioClient.DEFAULT_BASE_URL.endsWith(":8082"))
    }

    @Test
    fun camareroApuntaAlServicioCamareros() {
        assertTrue(IdentityCamareroClient.DEFAULT_BASE_URL.endsWith(":8080"))
    }

    @Test
    fun basesSonDistintas() {
        assertNotEquals(IdentityNegocioClient.DEFAULT_BASE_URL, IdentityCamareroClient.DEFAULT_BASE_URL)
    }

    @Test
    fun qrPublicKeyDecodifica() {
        val json = """{"algorithm":"Ed25519","key_id":"ed25519-v1","public_key":"abc","qr_prefix":"phid1","format":"phid1:<camarero_id>:<credencial_id>:<firma>"}"""
        val key = LanJson.decodeFromString<IdentityQrPublicKey>(json)
        assertEquals("Ed25519", key.algorithm)
        assertEquals("ed25519-v1", key.keyId)
        assertEquals("abc", key.publicKey)
        assertEquals("phid1", key.qrPrefix)
    }
}
