package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapaMathTest {

    @Test
    fun findNearestFreeCell_snapsToCell() {
        val (x, y) = findNearestFreeCell(45f, 45f, 120f, 120f, emptyList())
        assertEquals(40f, x, 0.001f)
        assertEquals(40f, y, 0.001f)
    }

    @Test
    fun findNearestFreeCell_evitaOcupada() {
        val occupied = listOf(listOf(40f, 40f, 120f, 120f))
        val (x, y) = findNearestFreeCell(45f, 45f, 120f, 120f, occupied)
        // La celda (40,40) está ocupada (bloque 120×120) → la espiral devuelve (40,160)
        assertEquals(40f, x, 0.001f)
        assertEquals(160f, y, 0.001f)
    }

    @Test
    fun colisionDetectaSolapeYDisjuntos() {
        assertTrue(colisionan(40f, 40f, 120f, 120f, 100f, 100f, 120f, 120f))
        assertFalse(colisionan(40f, 40f, 120f, 120f, 200f, 200f, 120f, 120f))
    }

    @Test
    fun clampAlBordeRecortaDentroDelGrid() {
        val (x, y) = clampAlBorde(5000f, 5000f, 120f, 120f)
        val maxX = kotlin.math.floor((ZONA_ANCHO - 120f - CELL_F) / CELL_F) * CELL_F
        val maxY = kotlin.math.floor((ZONA_ALTO - 120f - CELL_F) / CELL_F) * CELL_F
        assertEquals(maxX, x, 0.001f)
        assertEquals(maxY, y, 0.001f)
    }

    @Test
    fun calcularEscalaAjuste_encajaAlMenor() {
        val s = calcularEscalaAjuste(1000f, 1000f, 2000f, 2600f)
        assertEquals(1000f / 2600f, s, 0.001f)
    }

    @Test
    fun limitarPan_limitaCuandoContentMayorQueViewport() {
        assertEquals(-1500f, limitarPan(-2000f, 500f, 2000f), 0.001f)
        assertEquals(0f, limitarPan(100f, 500f, 2000f), 0.001f)
    }

    @Test
    fun limitarPan_centraCuandoContentMenorQueViewport() {
        assertEquals(150f, limitarPan(0f, 500f, 200f), 0.001f)
    }

    @Test
    fun mesaDims_respetaGiroEnRectangulares() {
        assertEquals(Pair(120f, 120f), mesaDims(MesaForma.CUADRADA, girada = false))
        assertEquals(Pair(240f, 120f), mesaDims(MesaForma.RECTANGULAR, girada = false))
        assertEquals(Pair(120f, 240f), mesaDims(MesaForma.RECTANGULAR, girada = true))
        assertEquals(Pair(120f, 360f), mesaDims(MesaForma.RECTANGULAR_XL, girada = true))
    }
}
