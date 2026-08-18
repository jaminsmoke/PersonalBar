package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del lado productor del libro de oficio: historial de jornada local y
 * proyección de «ronda servida» (cola persistente acreditable a Identity).
 */
class OficioProductorTest {

    private val catalogo = listOf(
        Producto("cana", "Caña", "Bebida"),
        Producto("croquetas", "Croquetas", "Comida"),
    )

    private fun activa(id: String, nombre: String?) = Camarero(id = id, nombre = nombre, estado = CamareroEstado.ACTIVA)

    private fun rondaSola(id: String = "r1", camarero: String? = null, mesa: String = "T3"): Ronda =
        Ronda(id = id, mesaId = mesa, numero = 1, camarero = camarero, lineas = listOf(Linea("cana", "Caña", 1)))

    private fun recoger(repo: InMemoryBarRepository, ticketId: String) {
        assertTrue(repo.marcarPreparado(ticketId, "Ana"))
        assertTrue(repo.marcarRecogido(ticketId))
    }

    // ── Detección de «ronda servida» ─────────────────────────────────────────

    @Test
    fun rondaCompletaConUnTicketEmiteRondaServida() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García")),
        )
        repo.crearRonda(rondaSola(camarero = "Lucía García"))

        assertTrue(repo.serviciosPendientes.value.isEmpty())
        recoger(repo, repo.bebidaQueue.value[0].id)

        val pendiente = repo.serviciosPendientes.value.single()
        assertEquals("servicio:r1", pendiente.eventoId)
        assertEquals("ronda_servida", pendiente.tipo)
        assertEquals(1, pendiente.cantidad)
        assertEquals("c-1", pendiente.camareroId)
    }

    @Test
    fun rondaDeDosTicketsSoloEmiteAlCompletarse() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García")),
        )
        repo.crearRonda(
            Ronda(
                "r1", "T3", 1, "Lucía García",
                lineas = listOf(Linea("cana", "Caña", 2), Linea("croquetas", "Croquetas", 1)),
            )
        )
        val bebida = repo.bebidaQueue.value[0].id
        val comida = repo.comidaQueue.value[0].id

        // Recoger solo la bebida: la comida sigue en cola → la ronda no está servida.
        recoger(repo, bebida)
        assertTrue(repo.serviciosPendientes.value.isEmpty())

        // Recoger la comida: la ronda queda completa → se emite.
        recoger(repo, comida)
        val pendiente = repo.serviciosPendientes.value.single()
        assertEquals("servicio:r1", pendiente.eventoId)
        assertEquals("c-1", pendiente.camareroId)
    }

    @Test
    fun rondaSinCamareroNoEmite() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García")),
        )
        repo.crearRonda(rondaSola(camarero = null))
        recoger(repo, repo.bebidaQueue.value[0].id)
        assertTrue(repo.serviciosPendientes.value.isEmpty())
    }

    @Test
    fun atribucionEstrictaPorNombreNormalizado() {
        // «lucía garcia» vs «Lucia Garcia»: normalizado coincide.
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García")),
        )
        repo.crearRonda(rondaSola(camarero = "lucia garcia"))
        recoger(repo, repo.bebidaQueue.value[0].id)
        assertEquals("c-1", repo.serviciosPendientes.value.single().camareroId)
    }

    @Test
    fun atribucionNoResuelveSiNoEstaEnLaListaBlanca() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Ana López")),
        )
        repo.crearRonda(rondaSola(camarero = "Lucía García"))
        recoger(repo, repo.bebidaQueue.value[0].id)
        assertTrue(repo.serviciosPendientes.value.isEmpty())
    }

    @Test
    fun atribucionNoResuelveSiHayVariosActivosConElMismoNombre() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García"), activa("c-2", "Lucia Garcia")),
        )
        repo.crearRonda(rondaSola(camarero = "Lucía García"))
        recoger(repo, repo.bebidaQueue.value[0].id)
        // No se adivina: dos ACTIVA con el mismo nombre normalizado → no se emite.
        assertTrue(repo.serviciosPendientes.value.isEmpty())
    }

    @Test
    fun revocadoNoEsCandidatoParaLaAtribucion() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(
                activa("c-1", "Lucía García"),
                Camarero(id = "c-2", nombre = "Lucia Garcia", estado = CamareroEstado.REVOCADA),
            ),
        )
        repo.crearRonda(rondaSola(camarero = "Lucía García"))
        recoger(repo, repo.bebidaQueue.value[0].id)
        assertEquals("c-1", repo.serviciosPendientes.value.single().camareroId)
    }

    // ── Cola persistente ─────────────────────────────────────────────────────

    @Test
    fun registrarServicioPendienteEsIdempotentePorEventoId() {
        val repo = InMemoryBarRepository(catalogoInicial = catalogo)
        repo.registrarServicioPendiente(
            ServicioPendiente(eventoId = "servicio:r1", camareroId = "c-1", tipo = "ronda_servida")
        )
        repo.registrarServicioPendiente(
            ServicioPendiente(eventoId = "servicio:r1", camareroId = "c-1", tipo = "ronda_servida")
        )
        assertEquals(1, repo.serviciosPendientes.value.size)
        repo.eliminarServicioPendiente("servicio:r1")
        assertTrue(repo.serviciosPendientes.value.isEmpty())
        // eliminar uno inexistente no rompe
        repo.eliminarServicioPendiente("servicio:no-existe")
        assertTrue(repo.serviciosPendientes.value.isEmpty())
    }

    @Test
    fun recogerDosRondasEncolaDosEventos() {
        val repo = InMemoryBarRepository(
            catalogoInicial = catalogo,
            camarerosIniciales = listOf(activa("c-1", "Lucía García")),
        )
        repo.crearRonda(rondaSola("r1", "Lucía García"))
        repo.crearRonda(rondaSola("r2", "Lucía García", mesa = "T4"))
        recoger(repo, repo.bebidaQueue.value.first { it.rondaId == "r1" }.id)
        recoger(repo, repo.bebidaQueue.value.first { it.rondaId == "r2" }.id)
        assertEquals(2, repo.serviciosPendientes.value.size)
        assertEquals(setOf("servicio:r1", "servicio:r2"), repo.serviciosPendientes.value.map { it.eventoId }.toSet())
    }

    // ── Historial de jornada ─────────────────────────────────────────────────

    @Test
    fun iniciarSesionAbreJornadaYCortarLaCierra() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        repo.iniciarSesion("c-1")
        val abierta = repo.jornadas.value.single()
        assertEquals("c-1", abierta.camareroId)
        assertNull(abierta.fin)

        repo.cortarSesion("c-1")
        val cerrada = repo.jornadas.value.single()
        assertTrue(cerrada.fin != null && cerrada.fin >= cerrada.inicio)
    }

    @Test
    fun iniciarSesionNoDuplicaJornadaAbierta() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        repo.iniciarSesion("c-1")
        repo.iniciarSesion("c-1")
        assertEquals(1, repo.jornadas.value.size)
    }

    @Test
    fun nuevaSesionTrasCortarAbreOtraJornada() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        repo.iniciarSesion("c-1")
        repo.cortarSesion("c-1")
        repo.iniciarSesion("c-1")
        assertEquals(2, repo.jornadas.value.size)
        assertEquals(1, repo.jornadas.value.count { it.fin == null })
    }

    @Test
    fun timeoutCierraLaJornadaAbierta() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        repo.iniciarSesion("c-1")
        assertEquals(1, repo.cortarSesionesVencidas(-1))
        assertTrue(repo.jornadas.value.single().fin != null)
    }

    @Test
    fun revocarCamareroCierraSuJornada() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        repo.iniciarSesion("c-1")
        repo.revocarCamarero("c-1")
        assertTrue(repo.jornadas.value.single().fin != null)
    }

    @Test
    fun iniciarSesionFallidaNoAbreJornada() {
        val repo = InMemoryBarRepository()
        assertFalse(repo.iniciarSesion("no-existe"))
        assertTrue(repo.jornadas.value.isEmpty())
    }

    @Test
    fun cortarSesionSinJornadaNoCreaIntervalos() {
        val repo = InMemoryBarRepository(camarerosIniciales = listOf(activa("c-1", "Lucía García")))
        assertFalse(repo.cortarSesion("c-1"))
        assertTrue(repo.jornadas.value.isEmpty())
    }
}
