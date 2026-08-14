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
    fun marcarPreparadoFijaEstadoYPreparadorSinSacarDeLaCola() {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        assertTrue(repo.marcarPreparado(ticketId, "Ana"))
        assertEquals(TicketEstado.PREPARADO, repo.bebidaQueue.value[0].estado)
        assertEquals("Ana", repo.bebidaQueue.value[0].preparadoPor)
        assertEquals(1, repo.bebidaQueue.value.size)
    }

    @Test
    fun marcarRecogidoSacaDeLaColaYAcumulaEnServidos() {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        assertTrue(repo.marcarPreparado(ticketId, "Ana"))
        assertTrue(repo.marcarRecogido(ticketId))
        assertTrue(repo.bebidaQueue.value.isEmpty())
        assertEquals(1, repo.servidos.value.size)
        assertEquals(TicketEstado.RECOGIDO, repo.servidos.value[0].estado)
        assertEquals("Ana", repo.servidos.value[0].preparadoPor)
    }

    @Test
    fun marcarRecogidoSinPrepararDevuelveFalse() {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        assertFalse(repo.marcarRecogido(ticketId))
        assertEquals(1, repo.bebidaQueue.value.size)
    }

    @Test
    fun marcarPreparadoEmiteEvento() = runBlocking {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { deferred.complete(it) }
        }
        repo.marcarPreparado(ticketId, "Ana")
        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("evento no emitido")
        assertEquals(SalaEvent.TIPO_PREPARADO, evento.tipo)
        assertEquals(ticketId, evento.ticketId)
        assertEquals("Ana", evento.preparadoPor)
        job.cancel()
    }

    @Test
    fun marcarRecogidoEmiteEvento() = runBlocking {
        val repo = repo()
        repo.crearRonda(Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val ticketId = repo.bebidaQueue.value[0].id
        repo.marcarPreparado(ticketId, "Ana")
        val deferred = CompletableDeferred<SalaEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.eventos.collect { deferred.complete(it) }
        }
        repo.marcarRecogido(ticketId)
        val evento = withTimeoutOrNull(2000) { deferred.await() } ?: error("evento no emitido")
        assertEquals(SalaEvent.TIPO_RECOGIDO, evento.tipo)
        assertEquals(ticketId, evento.ticketId)
        job.cancel()
    }

    @Test
    fun ticketInexistenteDevuelveFalse() {
        val repo = repo()
        assertFalse(repo.marcarPreparado("no-existe", "Ana"))
        assertFalse(repo.marcarRecogido("no-existe"))
    }

    @Test
    fun identityConfigSeActualiza() {
        val repo = repo()
        assertFalse(repo.identityConfig.value.conectado)
        repo.setIdentityConfig(
            IdentityConfig(
                conectado = true,
                baseUrl = "http://10.0.2.2:8080",
                establecimientoUuid = "e-1",
            )
        )
        assertTrue(repo.identityConfig.value.conectado)
        assertEquals("e-1", repo.identityConfig.value.establecimientoUuid)
    }

    @Test
    fun invitacionSeRegistraYSePuedeRevocar() {
        val repo = repo()
        repo.registrarInvitacion(
            Invitacion(id = "inv-1", email = "ana@example.com", estado = InvitacionEstado.PENDIENTE)
        )
        assertEquals(1, repo.invitaciones.value.size)
        assertTrue(repo.revocarInvitacionLocal("inv-1"))
        assertEquals(InvitacionEstado.REVOCADA, repo.invitaciones.value[0].estado)
        assertFalse(repo.revocarInvitacionLocal("no-existe"))
    }

    @Test
    fun sincronizarMiembrosAnadeALaListaBlanca() {
        val repo = repo()
        repo.sincronizarMiembros(listOf("c-1", "c-2"))
        assertEquals(2, repo.camareros.value.size)
        // idempotente: repetir no duplica
        repo.sincronizarMiembros(listOf("c-1", "c-2"))
        assertEquals(2, repo.camareros.value.size)
        assertTrue(repo.camareros.value.all { it.estado == CamareroEstado.ACTIVA })
    }

    @Test
    fun crudSalas() {
        val repo = InMemoryBarRepository(
            salasIniciales = listOf(Sala("sala-terraza", "Terraza", 1)),
            mesasIniciales = listOf(Mesa(id = "mesa-1", salaId = "sala-terraza", indiceZona = 3)),
        )
        assertTrue(repo.crearSala("Interior"))
        assertEquals(2, repo.salas.value.size)

        // duplicado (ignora mayúsculas)
        assertFalse(repo.crearSala("interior"))

        assertTrue(repo.renombrarSala("sala-terraza", "Patio"))
        assertEquals("Patio", repo.salas.value.first { it.id == "sala-terraza" }.nombre)

        // no se puede eliminar una sala con mesas
        assertFalse(repo.eliminarSala("sala-terraza"))

        // sí se puede eliminar una sala vacía
        val interior = repo.salas.value.first { it.nombre == "Interior" }
        assertTrue(repo.eliminarSala(interior.id))
        assertEquals(1, repo.salas.value.size)
    }
}
