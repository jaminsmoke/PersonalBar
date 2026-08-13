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

class InMemoryBarRepositoryTest {

    private val catalogo = listOf(
        Producto("cana", "Caña", "Bebida"),
        Producto("croquetas", "Croquetas", "Comida"),
    )

    private fun repo() = InMemoryBarRepository(catalogoInicial = catalogo)

    @Test
    fun crearRondaPueblaAmbasColas() {
        val repo = repo()
        assertTrue(
            repo.crearRonda(
                Ronda(
                    "r1", "T3", 1,
                    lineas = listOf(Linea("cana", "Caña", 2), Linea("croquetas", "Croquetas", 1)),
                )
            )
        )
        assertEquals(1, repo.bebidaQueue.value.size)
        assertEquals(1, repo.comidaQueue.value.size)
        assertEquals(Destino.BARRA, repo.bebidaQueue.value[0].destino)
        assertEquals(Destino.COCINA, repo.comidaQueue.value[0].destino)
    }

    @Test
    fun crearRondaDuplicadaSeIgnora() {
        val repo = repo()
        val ronda = Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1)))
        assertTrue(repo.crearRonda(ronda))
        assertFalse(repo.crearRonda(ronda))
        assertEquals(1, repo.bebidaQueue.value.size)
        assertEquals(1, repo.rondas.value.size)
    }

    @Test
    fun marcarListoCambiaEstadoSinSacarDeLaCola() {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        assertTrue(repo.marcarListo(ticketId))
        assertEquals(TicketEstado.LISTO, repo.bebidaQueue.value[0].estado)
        assertEquals(1, repo.bebidaQueue.value.size)
    }

    @Test
    fun marcarServidoSacaDeLaColaYAcumulaEnServidos() {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        assertTrue(repo.marcarServido(ticketId))
        assertTrue(repo.bebidaQueue.value.isEmpty())
        assertEquals(1, repo.servidos.value.size)
        assertEquals(TicketEstado.SERVIDO, repo.servidos.value[0].estado)
    }

    @Test
    fun marcarListoEmiteEvento() = runBlocking {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { deferred.complete(it) }
        }
        repo.marcarListo(ticketId)
        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("evento no emitido")
        assertEquals(SalaEvent.TIPO_LISTO, evento.tipo)
        assertEquals(ticketId, evento.ticketId)
        job.cancel()
    }

    @Test
    fun marcarServidoEmiteEvento() = runBlocking {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { deferred.complete(it) }
        }
        repo.marcarServido(ticketId)
        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("evento no emitido")
        assertEquals(SalaEvent.TIPO_SERVIDO, evento.tipo)
        assertEquals(ticketId, evento.ticketId)
        job.cancel()
    }

    @Test
    fun ticketInexistenteDevuelveFalse() {
        val repo = repo()
        assertFalse(repo.marcarListo("no-existe"))
        assertFalse(repo.marcarServido("no-existe"))
    }
}
