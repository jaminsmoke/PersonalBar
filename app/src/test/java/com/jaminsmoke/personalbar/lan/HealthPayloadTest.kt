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
        assertTrue(json.contains("\"sala\":\"vacia\""))
        assertTrue(json.contains("\"version\":\"0.1\""))
    }

    @Test
    fun defaultJson_exactShape() {
        assertEquals(
            """{"ok":true,"role":"bar","sala":"vacia","version":"0.1"}""",
            HealthPayload.json(),
        )
    }

    @Test
    fun customOkFalse() {
        val json = HealthPayload.json(ok = false)
        assertTrue(json.startsWith("{\"ok\":false"))
    }
}
