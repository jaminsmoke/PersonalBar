package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MesaTest {

    private fun mesa(id: String, salaId: String, indice: Int, alias: String? = null) =
        Mesa(id = id, salaId = salaId, indiceZona = indice, alias = alias)

    @Test
    fun idZonaDerivaDeSalaEIndice() {
        assertEquals("T3", mesa("m1", "sala-terraza", 3).idZona("Terraza"))
        assertEquals("B1", mesa("m2", "sala-barra", 1).idZona("Barra"))
        assertEquals("M2", mesa("m3", "sala-x", 2).idZona(""))
    }

    @Test
    fun nombreVisibleUsaAliasSiExiste() {
        assertEquals("Ventana", mesa("m1", "sala-terraza", 1, alias = "Ventana").nombreVisible("Terraza"))
        assertEquals("T1", mesa("m1", "sala-terraza", 1).nombreVisible("Terraza"))
    }

    @Test
    fun formaDerivaModulos() {
        assertEquals(1, mesaModulos(MesaForma.REDONDA))
        assertEquals(1, mesaModulos(MesaForma.CUADRADA))
        assertEquals(2, mesaModulos(MesaForma.RECTANGULAR))
        assertEquals(3, mesaModulos(MesaForma.RECTANGULAR_XL))
    }
}
