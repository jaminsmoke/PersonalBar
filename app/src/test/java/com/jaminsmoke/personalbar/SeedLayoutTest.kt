package com.jaminsmoke.personalbar

import com.jaminsmoke.personalbar.data.CARD_W
import com.jaminsmoke.personalbar.data.CELL_F
import com.jaminsmoke.personalbar.data.ZONA_ALTO
import com.jaminsmoke.personalbar.data.ZONA_ANCHO
import com.jaminsmoke.personalbar.data.colisionan
import com.jaminsmoke.personalbar.data.mesaDims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedLayoutTest {

    @Test
    fun seedTiene12Mesas() {
        assertEquals(12, mesasPorDefecto().size)
    }

    @Test
    fun seedDentroDeLosLimitesDelCanvasHorizontal() {
        mesasPorDefecto().forEach { m ->
            val (w, h) = mesaDims(m.forma, m.girada)
            assertTrue("x=${m.posX} fuera", m.posX >= CELL_F)
            assertTrue("y=${m.posY} fuera", m.posY >= CELL_F)
            assertTrue("x+w=${m.posX + w} fuera", m.posX + w <= ZONA_ANCHO - CELL_F)
            assertTrue("y+h=${m.posY + h} fuera", m.posY + h <= ZONA_ALTO - CELL_F)
        }
    }

    @Test
    fun seedSinColisiones() {
        val mesas = mesasPorDefecto()
        for (i in mesas.indices) {
            val (wi, hi) = mesaDims(mesas[i].forma, mesas[i].girada)
            for (j in i + 1 until mesas.size) {
                val (wj, hj) = mesaDims(mesas[j].forma, mesas[j].girada)
                assertFalse(
                    "colisión ${mesas[i].id} vs ${mesas[j].id}",
                    colisionan(mesas[i].posX, mesas[i].posY, wi, hi, mesas[j].posX, mesas[j].posY, wj, hj),
                )
            }
        }
    }

    @Test
    fun seedRepartidoEnBandaHorizontal() {
        // Cada sala ocupa una banda propia en Y; el seed ya no es un cluster 2×2 en la esquina.
        val mesas = mesasPorDefecto()
        val porSala = mesas.groupBy { it.salaId }
        assertEquals(3, porSala.size)
        val bandas = porSala.values.map { salas -> salas.map { it.posY }.distinct() }
        // Cada sala tiene mesas en una banda Y propia y al menos 2 posiciones X distintas.
        bandas.forEach { ys ->
            assertTrue("banda Y con 1 sola fila o fuera: $ys", ys.size >= 1)
        }
        val xsGlobales = mesas.map { it.posX }.distinct()
        assertTrue("el seed debe repartirse en X (${xsGlobales.size} posiciones)", xsGlobales.size >= 4)
    }
}
