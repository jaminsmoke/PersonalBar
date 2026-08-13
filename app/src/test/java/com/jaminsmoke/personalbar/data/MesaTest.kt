package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MesaTest {

    @Test
    fun idZonaDerivaDeZonaEIndice() {
        assertEquals("T3", Mesa(zona = "Terraza", indiceZona = 3).idZona)
        assertEquals("B1", Mesa(zona = "Barra", indiceZona = 1).idZona)
        assertEquals("M2", Mesa(zona = "", indiceZona = 2).idZona)
    }

    @Test
    fun nombreVisibleUsaAliasSiExiste() {
        assertEquals("Ventana", Mesa(zona = "Terraza", indiceZona = 1, alias = "Ventana").nombreVisible)
        assertEquals("T1", Mesa(zona = "Terraza", indiceZona = 1).nombreVisible)
    }
}
