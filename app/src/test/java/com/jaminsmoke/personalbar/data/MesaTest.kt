package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MesaTest {

    @Test
    fun idZonaDerivaDeSalaEIndice() {
        assertEquals("T3", Mesa(salaId = "sala-terraza", indiceZona = 3).idZona("Terraza"))
        assertEquals("B1", Mesa(salaId = "sala-barra", indiceZona = 1).idZona("Barra"))
        assertEquals("M2", Mesa(salaId = "sala-x", indiceZona = 2).idZona(""))
    }

    @Test
    fun nombreVisibleUsaAliasSiExiste() {
        assertEquals(
            "Ventana",
            Mesa(salaId = "sala-terraza", indiceZona = 1, alias = "Ventana").nombreVisible("Terraza"),
        )
        assertEquals("T1", Mesa(salaId = "sala-terraza", indiceZona = 1).nombreVisible("Terraza"))
    }
}
