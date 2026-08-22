package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepartoCamarerosTest {

    private val sala = Sala(id = "sala-barra", nombre = "Barra", orden = 1)

    private val mesaBarra = Mesa(
        id = "m1",
        mesaUuid = "uuid-1",
        salaId = "sala-barra",
        indiceZona = 1,
        posX = 100f,
        posY = 100f,
    )

    private fun camarero(id: String, deServicio: Boolean = true, estado: CamareroEstado = CamareroEstado.ACTIVA) =
        Camarero(id = id, nombre = id, estado = estado, deServicio = deServicio)

    private fun zona(id: String, camareroId: String? = null, posX: Float = 0f, posY: Float = 0f, ancho: Float = 500f, alto: Float = 500f) =
        Zona(id = id, salaId = "sala-barra", nombre = id, posX = posX, posY = posY, ancho = ancho, alto = alto, camareroId = camareroId)

    private fun ronda(id: String, camareroId: String? = null) =
        Ronda(id = id, mesaId = "B1", numero = 1, camarero = camareroId, lineas = emptyList())

    // ── Precedencia mesa directa ────────────────────────────────────────────

    @Test
    fun asignacionDirectaDeMesaGana() {
        val mesa = mesaBarra.copy(camareroId = "carmen")
        val resultado = RepartoCamareros.resolverCamarero(
            mesa, listOf(zona("z1", camareroId = "otro")), listOf(camarero("carmen"), camarero("otro")), emptyList(),
        )
        assertEquals("carmen", resultado)
    }

    @Test
    fun asignacionDirectaConCamareroNoDeServicioCaeAZona() {
        val mesa = mesaBarra.copy(camareroId = "luis")
        val resultado = RepartoCamareros.resolverCamarero(
            mesa, listOf(zona("z1", camareroId = "carmen")),
            listOf(camarero("luis", deServicio = false), camarero("carmen")), emptyList(),
        )
        assertEquals("carmen", resultado)
    }

    // ── Zona ───────────────────────────────────────────────────────────────

    @Test
    fun sinDirectaUsaZona() {
        val resultado = RepartoCamareros.resolverCamarero(
            mesaBarra, listOf(zona("z1", camareroId = "carmen")), listOf(camarero("carmen")), emptyList(),
        )
        assertEquals("carmen", resultado)
    }

    @Test
    fun zonaSinCamareroNoCuentaYCaeAMenorCarga() {
        val resultado = RepartoCamareros.resolverCamarero(
            mesaBarra, listOf(zona("z1")), listOf(camarero("carmen")), emptyList(),
        )
        // La zona sin camarero se descarta → cae a menor carga (carmen)
        assertEquals("carmen", resultado)
    }

    @Test
    fun solapeDeZonasEsDeterministaPorId() {
        val zonas = listOf(
            zona("z-b", camareroId = "luis"),
            zona("z-a", camareroId = "carmen"),
        )
        val resultado = RepartoCamareros.resolverCamarero(mesaBarra, zonas, listOf(camarero("carmen"), camarero("luis")), emptyList())
        // Primera por id (orden estable) → z-a → carmen, da igual el orden de la lista
        assertEquals("carmen", resultado)
    }

    // ── Menor carga ─────────────────────────────────────────────────────────

    @Test
    fun sinZonaUsaMenorCarga() {
        val activas = listOf(ronda("r1", camareroId = "carmen"), ronda("r2", camareroId = "carmen"))
        val resultado = RepartoCamareros.resolverCamarero(
            mesaBarra, emptyList(), listOf(camarero("carmen"), camarero("luis")), activas,
        )
        assertEquals("luis", resultado)
    }

    @Test
    fun empateDeCargaSeRompePorId() {
        val resultado = RepartoCamareros.resolverCamarero(
            mesaBarra, emptyList(), listOf(camarero("b"), camarero("a")), emptyList(),
        )
        assertEquals("a", resultado)
    }

    @Test
    fun cargaCuentaSoloRondasActivasDelCamarero() {
        val activas = listOf(ronda("r1", camareroId = "carmen"), ronda("r2", camareroId = "carmen"), ronda("r3", camareroId = "luis"))
        assertEquals(2, RepartoCamareros.cargaDe("carmen", activas))
        assertEquals(1, RepartoCamareros.cargaDe("luis", activas))
        assertEquals(0, RepartoCamareros.cargaDe("nadie", activas))
    }

    // ── Sin asignar / válidos ───────────────────────────────────────────────

    @Test
    fun sinCamarerosValidosDevuelveNull() {
        val resultado = RepartoCamareros.resolverCamarero(
            mesaBarra, emptyList(), listOf(camarero("carmen", deServicio = false)), emptyList(),
        )
        assertNull(resultado)
    }

    @Test
    fun camareroAsignadoInexistenteCaeAMenorCarga() {
        val mesa = mesaBarra.copy(camareroId = "fantasma")
        val resultado = RepartoCamareros.resolverCamarero(
            mesa, emptyList(), listOf(camarero("carmen")), emptyList(),
        )
        assertEquals("carmen", resultado)
    }

    @Test
    fun camareroRevocadoNoEsValido() {
        val mesa = mesaBarra.copy(camareroId = "carmen")
        val resultado = RepartoCamareros.resolverCamarero(
            mesa, emptyList(), listOf(camarero("carmen", estado = CamareroEstado.REVOCADA)), emptyList(),
        )
        assertNull(resultado)
    }
}
