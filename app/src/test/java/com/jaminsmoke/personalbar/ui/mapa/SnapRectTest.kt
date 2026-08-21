package com.jaminsmoke.personalbar.ui.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.jaminsmoke.personalbar.data.CELL_F
import com.jaminsmoke.personalbar.data.ZONA_ALTO
import com.jaminsmoke.personalbar.data.ZONA_ANCHO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `snapRect` normaliza, ajusta al grid y encaja en el board un rectángulo trazado
 * en px locales del canvas (density 1 ⇒ px == dp en el test).
 */
class SnapRectTest {

    private val density = Density(density = 1f)

    private fun assertRect(expected: ZonaRect, actual: ZonaRect) {
        assertEquals("x", expected.x, actual.x, 0.001f)
        assertEquals("y", expected.y, actual.y, 0.001f)
        assertEquals("w", expected.w, actual.w, 0.001f)
        assertEquals("h", expected.h, actual.h, 0.001f)
    }

    @Test
    fun normalizaEsquinasInvertidas() {
        // se arrastra de abajo-derecha a arriba-izquierda: el rectángulo se normaliza
        val rect = snapRect(Offset(240f, 160f), Offset(40f, 40f), density)
        assertRect(ZonaRect(40f, 40f, 200f, 120f), rect)
    }

    @Test
    fun ajustaAlGrid() {
        // (45,45)→(205,165): 160×120 exacto en grid, origen redondeado a celda
        val rect = snapRect(Offset(45f, 45f), Offset(205f, 165f), density)
        assertRect(ZonaRect(40f, 40f, 160f, 120f), rect)
    }

    @Test
    fun tamanoMinimoUnaCelda() {
        // un arrastre diminuto (10×10) no crea una zona sub-celda
        val rect = snapRect(Offset(40f, 40f), Offset(50f, 50f), density)
        assertRect(ZonaRect(40f, 40f, CELL_F, CELL_F), rect)
    }

    @Test
    fun encajaDentroDelBoard() {
        // un arrastre mayor que el canvas se acota al board completo
        val rect = snapRect(Offset(0f, 0f), Offset(5000f, 5000f), density)
        assertRect(ZonaRect(0f, 0f, ZONA_ANCHO, ZONA_ALTO), rect)
    }

    @Test
    fun arrastreHaciaArribaIzquierda() {
        // 300dp de arrastre → 320 (múltiplo de celda), origen 100 → 120: ambos bordes
        // caen en líneas de grid.
        val rect = snapRect(Offset(400f, 300f), Offset(100f, 100f), density)
        assertRect(ZonaRect(120f, 120f, 320f, 200f), rect)
    }
}
