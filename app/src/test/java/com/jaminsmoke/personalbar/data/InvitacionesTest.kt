package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitacionesTest {

    // ── Mapeo estado desde Identity ──────────────────────────────────────────

    @Test
    fun invitacionEstadoDesdeApi_mapeaLosCincoEstados() {
        assertEquals(InvitacionEstado.PENDIENTE, invitacionEstadoDesdeApi("pendiente"))
        assertEquals(InvitacionEstado.ACEPTADA, invitacionEstadoDesdeApi("aceptada"))
        assertEquals(InvitacionEstado.REVOCADA, invitacionEstadoDesdeApi("revocada"))
        assertEquals(InvitacionEstado.RECHAZADA, invitacionEstadoDesdeApi("rechazada"))
        assertEquals(InvitacionEstado.EXPIRADA, invitacionEstadoDesdeApi("expirada"))
    }

    @Test
    fun invitacionEstadoDesdeApi_desconocidoDevuelveNull() {
        assertNull(invitacionEstadoDesdeApi("futuro_estado"))
        assertNull(invitacionEstadoDesdeApi(null))
        assertNull(invitacionEstadoDesdeApi(""))
    }

    // ── Espejo de invitaciones ───────────────────────────────────────────────

    @Test
    fun sincronizarInvitacionesReemplazaElEspejo() {
        val r = InMemoryBarRepository(
            invitacionesIniciales = listOf(
                Invitacion("i-1", "a@test.com", estado = InvitacionEstado.PENDIENTE),
            ),
        )
        r.sincronizarInvitaciones(
            listOf(
                Invitacion("i-2", "b@test.com", estado = InvitacionEstado.EXPIRADA, expiraEn = "2026-01-01T00:00:00Z"),
                Invitacion("i-3", "c@test.com", estado = InvitacionEstado.ACEPTADA),
            ),
        )
        assertEquals(listOf("i-2", "i-3"), r.invitaciones.value.map { it.id })
        assertEquals(InvitacionEstado.EXPIRADA, r.invitaciones.value.first { it.id == "i-2" }.estado)
    }

    @Test
    fun revocarInvitacionLocalMarcaRevocada() {
        val r = InMemoryBarRepository(
            invitacionesIniciales = listOf(
                Invitacion("i-1", "a@test.com", estado = InvitacionEstado.PENDIENTE),
            ),
        )
        assertTrue(r.revocarInvitacionLocal("i-1"))
        assertEquals(InvitacionEstado.REVOCADA, r.invitaciones.value.first { it.id == "i-1" }.estado)
    }
}
