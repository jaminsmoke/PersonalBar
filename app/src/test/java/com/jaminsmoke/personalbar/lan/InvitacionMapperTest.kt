package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.InvitacionEstado
import org.junit.Assert.assertEquals
import org.junit.Test

class InvitacionMapperTest {

    @Test
    fun toInvitacionMapeaRechazada() {
        val inv = IdentityInvitacion(
            id = "i-1",
            email = "anaTest@example.com",
            rol = "staff",
            estado = "rechazada",
            expiraEn = "2026-01-01T00:00:00Z",
        )
        val local = inv.toInvitacion()
        assertEquals("i-1", local.id)
        assertEquals("anaTest@example.com", local.email)
        assertEquals("staff", local.rol)
        assertEquals(InvitacionEstado.RECHAZADA, local.estado)
        assertEquals("2026-01-01T00:00:00Z", local.expiraEn)
    }

    @Test
    fun toInvitacionEstadoDesconocidoDegradaAPendiente() {
        val inv = IdentityInvitacion(id = "i-2", email = "bTest@example.com", estado = "futuro_estado")
        assertEquals(InvitacionEstado.PENDIENTE, inv.toInvitacion().estado)
    }
}
