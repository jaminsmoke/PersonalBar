package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutExportTest {

    private fun mesa(id: String, x: Float, y: Float, forma: MesaForma = MesaForma.CUADRADA): Mesa =
        Mesa(
            id = id,
            salaId = "sala-1",
            indiceZona = 1,
            numero = 1,
            forma = forma,
            capacidad = forma.capacidadDefecto,
            posX = x,
            posY = y,
        )

    @Test
    fun bloqueVacioDevuelveVacio() {
        assertTrue(convertirLayout(emptyList()).isEmpty())
    }

    @Test
    fun todasDentroDeLosLimitesDelCanvasDeCommander() {
        val mesas = listOf(
            mesa("m1", CELL_F, CELL_F),
            mesa("m2", ZONA_ANCHO - CARD_W, CELL_F),
            mesa("m3", CELL_F, ZONA_ALTO - CARD_W),
            mesa("m4", ZONA_ANCHO - CARD_W, ZONA_ALTO - CARD_W),
        )
        val convertidas = convertirLayout(mesas)
        convertidas.values.forEach { (x, y) ->
            assertTrue("x=$x fuera de [0, ${ZONA_ANCHO_COMANDER}]", x in 0f..ZONA_ANCHO_COMANDER)
            assertTrue("y=$y fuera de [0, ${ZONA_ALTO_COMANDER}]", y in 0f..ZONA_ALTO_COMANDER)
        }
        // La mesa en la esquina Bar (2600, 2000) no puede quedar fuera del canvas Commander.
        val esquina = convertidas["m4"]!!
        assertTrue("esquina x=${esquina.first}", esquina.first <= ZONA_ANCHO_COMANDER)
        assertTrue("esquina y=${esquina.second}", esquina.second <= ZONA_ALTO_COMANDER)
    }

    @Test
    fun escalaUniformePreservaProporciones() {
        val mesas = listOf(
            mesa("a", 100f, 100f),
            mesa("b", 900f, 300f),
        )
        val convertidas = convertirLayout(mesas)
        val a = convertidas["a"]!!
        val b = convertidas["b"]!!
        val dxBar = 900f - 100f
        val dyBar = 300f - 100f
        val dxCmd = b.first - a.first
        val dyCmd = b.second - a.second
        val escala = minOf(ZONA_ANCHO_COMANDER / ZONA_ANCHO, ZONA_ALTO_COMANDER / ZONA_ALTO)
        assertEquals(dxBar * escala, dxCmd, 0.01f)
        assertEquals(dyBar * escala, dyCmd, 0.01f)
    }

    @Test
    fun ordenDeIdsPreservado() {
        val mesas = listOf(
            mesa("c", 100f, 100f),
            mesa("a", 200f, 100f),
            mesa("b", 100f, 300f),
        )
        val convertidas = convertirLayout(mesas)
        assertEquals(listOf("c", "a", "b"), convertidas.keys.toList())
    }

    @Test
    fun bloqueRepartidoNoColisionaTrasConversion() {
        // Espaciado mínimo del grid en Bar (CELL_F entre bordes). Las posiciones escalan
        // (0,769) pero el tamaño de mesa es fijo (CARD_W) en Commander: el gap mínimo
        // resultante es (CELL_F+CARD_W)·escala − CARD_W ≈ 3dp > 0 → sin solape.
        val mesas = listOf(
            mesa("m1", 120f, 120f),
            mesa("m2", 120f + CELL_F + CARD_W, 120f),
        )
        val convertidas = convertirLayout(mesas)
        val a = convertidas["m1"]!!
        val b = convertidas["m2"]!!
        val gap = (b.first - a.first) - CARD_W
        assertTrue("gap=$gap debe ser > 0 (sin solape)", gap > 0f)
        // Con espaciado realista (p. ej. el seed, 480dp) el gap es holgado.
        val mesasSeed = listOf(mesa("s1", 120f, 120f), mesa("s2", 600f, 120f))
        val seedConv = convertirLayout(mesasSeed)
        val gapSeed = (seedConv["s2"]!!.first - seedConv["s1"]!!.first) - CARD_W
        assertTrue("gap seed=$gapSeed debe ser holgado", gapSeed > 100f)
    }
}
