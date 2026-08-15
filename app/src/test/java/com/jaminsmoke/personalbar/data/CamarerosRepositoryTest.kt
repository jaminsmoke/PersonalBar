package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamarerosRepositoryTest {

    private val repo = InMemoryBarRepository()
    private val camareroId = "11111111-1111-4111-8111-111111111111"

    @Test
    fun altaYRevertir() {
        assertTrue(repo.altaCamarero(camareroId, "cred-1"))
        assertEquals(1, repo.camareros.value.size)
        assertEquals(CamareroEstado.ACTIVA, repo.camareros.value.first().estado)

        // Duplicado activo → no se re-alta.
        assertFalse(repo.altaCamarero(camareroId, "cred-2"))
        assertEquals(1, repo.camareros.value.size)

        assertTrue(repo.revocarCamarero(camareroId))
        assertEquals(CamareroEstado.REVOCADA, repo.camareros.value.first().estado)

        // Re-alta tras revocar (p. ej. tras renovar QR) → vuelve a activa.
        assertTrue(repo.altaCamarero(camareroId, "cred-2"))
        assertEquals(CamareroEstado.ACTIVA, repo.camareros.value.first().estado)
        assertEquals("cred-2", repo.camareros.value.first().credencialId)
    }

    @Test
    fun revocarInexistenteFalla() {
        assertFalse(repo.revocarCamarero("no-existe"))
    }

    @Test
    fun altaConDatosRellenaNombreYEmail() {
        // Bar recoge la info de la cuenta desde Identity (no la edita).
        assertTrue(repo.altaCamarero(camareroId, "cred-1", nombre = "Lucía Test", email = "lucia@laterraza.es"))
        val alta = repo.camareros.value.first()
        assertEquals("Lucía Test", alta.nombre)
        assertEquals("lucia@laterraza.es", alta.email)

        // Re-alta sin datos conserva los ya recogidos.
        repo.revocarCamarero(camareroId)
        assertTrue(repo.altaCamarero(camareroId, "cred-2"))
        assertEquals("Lucía Test", repo.camareros.value.first().nombre)
        assertEquals("lucia@laterraza.es", repo.camareros.value.first().email)
    }

    @Test
    fun deServicioVariosALaVez() {
        val id2 = "22222222-2222-4222-8222-222222222222"
        repo.altaCamarero(camareroId, "cred-1", nombre = "Ana Test")
        repo.altaCamarero(id2, "cred-2", nombre = "Marcos Test")

        assertTrue(repo.deServicio.value.isEmpty())

        // Dos de servicio a la vez.
        assertTrue(repo.ponerDeServicio(camareroId))
        assertTrue(repo.ponerDeServicio(id2))
        assertEquals(2, repo.deServicio.value.size)

        // Poner dos veces es idempotente.
        assertTrue(repo.ponerDeServicio(camareroId))
        assertEquals(2, repo.deServicio.value.size)

        // Quitar uno no afecta al otro.
        assertTrue(repo.quitarDeServicio(camareroId))
        assertEquals(listOf(id2), repo.deServicio.value.map { it.id })
        assertFalse(repo.quitarDeServicio(camareroId))
    }

    @Test
    fun deServicioSoloCamarerosActivos() {
        repo.altaCamarero(camareroId, "cred-1", nombre = "Ana Test")
        assertTrue(repo.ponerDeServicio(camareroId))
        repo.revocarCamarero(camareroId)
        // Al revocar sale de servicio (y ya no es ACTIVA → no aparece).
        assertTrue(repo.deServicio.value.isEmpty())
        // Un camarero no dado de alta no puede ponerse de servicio.
        assertFalse(repo.ponerDeServicio("no-existe"))
    }

    @Test
    fun registrarInvitacionNoDaAltaCamarero() {
        // Inversión del alta: invitar deja un pendiente (espejo), NO mete al camarero
        // en la lista blanca. La aceptación (vía Identity) es lo que lo daría de alta.
        repo.registrarInvitacion(
            Invitacion(id = "inv-1", email = "ana@example.com", rol = "staff", estado = InvitacionEstado.PENDIENTE)
        )
        assertEquals(1, repo.invitaciones.value.size)
        assertEquals(InvitacionEstado.PENDIENTE, repo.invitaciones.value.first().estado)
        // La lista blanca sigue vacía: la invitación no es un alta.
        assertTrue(repo.camareros.value.isEmpty())
    }

    @Test
    fun revocarInvitacionNoTocaCamareros() {
        repo.registrarInvitacion(
            Invitacion(id = "inv-1", email = "ana@example.com", rol = "staff", estado = InvitacionEstado.PENDIENTE)
        )
        assertTrue(repo.revocarInvitacionLocal("inv-1"))
        assertEquals(InvitacionEstado.REVOCADA, repo.invitaciones.value.first().estado)
        assertTrue(repo.camareros.value.isEmpty())
    }
}
