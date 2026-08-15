package com.jaminsmoke.personalbar.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SesionCicloTest {

    private fun repoCon(camarero: Camarero): InMemoryBarRepository =
        InMemoryBarRepository(camarerosIniciales = listOf(camarero))

    private fun activa(id: String = "c-1", nombre: String = "Lucía García") =
        Camarero(id = id, nombre = nombre, estado = CamareroEstado.ACTIVA)

    @Test
    fun iniciarSesionConcedeJornada() {
        val repo = repoCon(activa())
        assertTrue(repo.iniciarSesion("c-1"))
        assertTrue(repo.tieneSesionActiva("c-1"))
        assertTrue(repo.camareros.value.single().sesionActiva)
    }

    @Test
    fun iniciarSesionRechazaDesconocidoORevocado() {
        val repo = repoCon(activa().copy(estado = CamareroEstado.REVOCADA))
        assertFalse(repo.iniciarSesion("c-1"))
        assertFalse(repo.iniciarSesion("no-existe"))
        assertFalse(repo.tieneSesionActiva("c-1"))
    }

    @Test
    fun cortarSesionBajaJornadaYEmiteEvento() = runBlocking {
        val repo = repoCon(activa())
        repo.iniciarSesion("c-1")

        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { if (it.tipo == SalaEvent.TIPO_SESION_CORTADA) deferred.complete(it) }
        }

        assertTrue(repo.cortarSesion("c-1"))
        assertFalse(repo.tieneSesionActiva("c-1"))

        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("sesion.cortada no emitido")
        assertEquals(SalaEvent.TIPO_SESION_CORTADA, evento.tipo)
        assertEquals("c-1", evento.camareroId)
        assertEquals("", evento.ticketId)
        job.cancel()
    }

    @Test
    fun cortarSesionSinJornadaDevuelveFalse() {
        val repo = repoCon(activa())
        assertFalse(repo.cortarSesion("c-1"))
        assertFalse(repo.cortarSesion("no-existe"))
    }

    @Test
    fun heartbeatRefrescaSoloConSesionActiva() {
        val repo = repoCon(activa())
        assertFalse(repo.registrarHeartbeat("c-1")) // sin sesión → 403
        repo.iniciarSesion("c-1")
        assertTrue(repo.registrarHeartbeat("c-1"))
        assertFalse(repo.registrarHeartbeat("no-existe"))
    }

    @Test
    fun revocarCortaSesionYEmiteEvento() = runBlocking {
        val repo = repoCon(activa())
        repo.iniciarSesion("c-1")

        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { if (it.tipo == SalaEvent.TIPO_SESION_CORTADA) deferred.complete(it) }
        }

        assertTrue(repo.revocarCamarero("c-1"))
        assertFalse(repo.tieneSesionActiva("c-1"))
        assertEquals(CamareroEstado.REVOCADA, repo.camareros.value.single().estado)

        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("sesion.cortada no emitido")
        assertEquals("c-1", evento.camareroId)
        job.cancel()
    }

    @Test
    fun timeoutCortaSesionesVencidas() {
        val repo = repoCon(activa())
        repo.iniciarSesion("c-1")

        // Con timeout enorme no corta nada (lastSeen reciente).
        assertEquals(0, repo.cortarSesionesVencidas(Long.MAX_VALUE))
        assertTrue(repo.tieneSesionActiva("c-1"))

        // Con timeout negativo cualquier lastSeen queda vencido → auto-inactivación.
        assertEquals(1, repo.cortarSesionesVencidas(-1))
        assertFalse(repo.tieneSesionActiva("c-1"))
    }

    @Test
    fun timeoutNoTocaSesionesInactivas() {
        val repo = repoCon(activa())
        assertEquals(0, repo.cortarSesionesVencidas(-1))
        assertFalse(repo.tieneSesionActiva("c-1"))
    }
}
