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
}
