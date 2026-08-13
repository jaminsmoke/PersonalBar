package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapaRepositoryTest {

    private fun repo() = InMemoryBarRepository(
        salasIniciales = listOf(Sala("sala-1", "Terraza", 1)),
        mesasIniciales = listOf(
            Mesa(id = "mesa-1", salaId = "sala-1", indiceZona = 1, numero = 1, posX = 40f, posY = 40f),
            Mesa(id = "mesa-2", salaId = "sala-1", indiceZona = 2, numero = 2, posX = 200f, posY = 40f),
        ),
        catalogoInicial = listOf(
            Producto("cana", "Caña", "Bebida"),
            Producto("croquetas", "Croquetas", "Comida"),
        ),
    )

    // ── CRUD mesas ───────────────────────────────────────────────────────────

    @Test
    fun crearMesaAutoPosicionaYNumera() {
        val r = repo()
        assertTrue(r.crearMesa("sala-1", MesaForma.CUADRADA, 4, null))
        assertEquals(3, r.mesas.value.size)
        val nueva = r.mesas.value.first { it.id != "mesa-1" && it.id != "mesa-2" }
        assertEquals(3, nueva.indiceZona)
        assertEquals(3, nueva.numero)
        assertTrue(nueva.posX >= CELL_F && nueva.posY >= CELL_F)
    }

    @Test
    fun crearMesaEnSalaInexistenteFalla() {
        assertFalse(repo().crearMesa("no-existe", MesaForma.CUADRADA, 4, null))
    }

    @Test
    fun editarMesaCambiaConfig() {
        val r = repo()
        assertTrue(r.editarMesa("mesa-1", "Ventana", 6, MesaForma.RECTANGULAR))
        val m = r.mesas.value.first { it.id == "mesa-1" }
        assertEquals("Ventana", m.alias)
        assertEquals(6, m.capacidad)
        assertEquals(MesaForma.RECTANGULAR, m.forma)
    }

    @Test
    fun borrarMesaReenumeraIndices() {
        val r = repo()
        assertTrue(r.borrarMesa("mesa-1"))
        assertEquals(1, r.mesas.value.size)
        assertEquals(1, r.mesas.value.first { it.id == "mesa-2" }.indiceZona)
    }

    @Test
    fun borrarMesaConReservaFalla() {
        val r = repo()
        assertTrue(r.reservar("mesa-1", "Ana", null))
        assertFalse(r.borrarMesa("mesa-1"))
    }

    @Test
    fun moverMesaActualizaPosicion() {
        val r = repo()
        assertTrue(r.moverMesa("mesa-1", 320f, 320f))
        assertEquals(320f, r.mesas.value.first { it.id == "mesa-1" }.posX, 0.001f)
    }

    @Test
    fun girarMesaRectangularCambiaDims() {
        val r = repo()
        r.crearMesa("sala-1", MesaForma.RECTANGULAR, 8, null)
        val m = r.mesas.value.first { it.forma == MesaForma.RECTANGULAR }
        assertTrue(r.girarMesa(m.id))
        assertTrue(r.mesas.value.first { it.id == m.id }.girada)
    }

    // ── Reservas / bloqueos ───────────────────────────────────────────────────

    @Test
    fun reservarYCancelar() {
        val r = repo()
        assertTrue(r.reservar("mesa-1", "Ana", null))
        val mesa = r.mesas.value.first { it.id == "mesa-1" }
        assertNotNull(mesa.reservaActivaId)
        assertTrue(r.cancelarReserva("mesa-1"))
        assertNull(r.mesas.value.first { it.id == "mesa-1" }.reservaActivaId)
    }

    @Test
    fun reservarMesaYaReservadaFalla() {
        val r = repo()
        assertTrue(r.reservar("mesa-1", "Ana", null))
        assertFalse(r.reservar("mesa-1", "Bea", null))
    }

    @Test
    fun bloquearYDesbloquear() {
        val r = repo()
        assertTrue(r.bloquearMesa("mesa-1"))
        assertTrue(r.mesas.value.first { it.id == "mesa-1" }.bloqueada)
        assertTrue(r.desbloquearMesa("mesa-1"))
        assertFalse(r.mesas.value.first { it.id == "mesa-1" }.bloqueada)
    }

    @Test
    fun bloquearMesaConReservaCancelaReserva() {
        val r = repo()
        assertTrue(r.reservar("mesa-1", "Ana", null))
        assertTrue(r.bloquearMesa("mesa-1"))
        val mesa = r.mesas.value.first { it.id == "mesa-1" }
        assertTrue(mesa.bloqueada)
        assertNull(mesa.reservaActivaId)
    }

    // ── Derivación de estado ──────────────────────────────────────────────────

    @Test
    fun derivarEstadoMesas_cocinaYOcupada() {
        val r = repo()
        r.crearRonda(
            Ronda("r1", "T1", 1, lineas = listOf(Linea("cana", "Caña", 1), Linea("croquetas", "Croquetas", 1))),
        )
        r.crearRonda(Ronda("r2", "T2", 1, lineas = listOf(Linea("cana", "Caña", 1))))
        val estados = derivarEstadoMesas(
            r.mesas.value, r.salas.value, r.rondas.value,
            r.bebidaQueue.value, r.comidaQueue.value, r.reservas.value,
        )
        // T1 tiene ticket COCINA pendiente → EN_COCINA; T2 solo bebida → OCUPADA
        assertEquals(MesaVisualStatus.EN_COCINA, estados["mesa-1"])
        assertEquals(MesaVisualStatus.OCUPADA, estados["mesa-2"])
    }

    @Test
    fun derivarEstadoMesas_libreSinTickets() {
        val r = repo()
        val estados = derivarEstadoMesas(
            r.mesas.value, r.salas.value, r.rondas.value,
            r.bebidaQueue.value, r.comidaQueue.value, r.reservas.value,
        )
        assertEquals(MesaVisualStatus.LIBRE, estados["mesa-1"])
    }
}
