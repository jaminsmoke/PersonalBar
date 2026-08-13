package com.jaminsmoke.personalbar.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthPayloadTest {

    @Test
    fun defaultJson_containsRequiredFields() {
        val json = HealthPayload.json()
        assertTrue(json.contains("\"ok\":true"))
        assertTrue(json.contains("\"role\":\"bar\""))
        assertTrue(json.contains("\"establecimiento\":\"local-1\""))
        assertTrue(json.contains("\"sala\":\"local-1\""))
        assertTrue(json.contains("\"version\":\"0.1\""))
    }

    @Test
    fun defaultJson_exactShape() {
        assertEquals(
            """{"ok":true,"role":"bar","establecimiento":"local-1","sala":"local-1","version":"0.1"}""",
            HealthPayload.json(),
        )
    }

    @Test
    fun establecimientoSeReflejaEnSalaDeprecado() {
        val json = HealthPayload.json(establecimiento = "La Terraza")
        assertTrue(json.contains("\"establecimiento\":\"La Terraza\""))
        assertTrue(json.contains("\"sala\":\"La Terraza\""))
    }

    @Test
    fun customOkFalse() {
        val json = HealthPayload.json(ok = false)
        assertTrue(json.startsWith("{\"ok\":false"))
    }
}
