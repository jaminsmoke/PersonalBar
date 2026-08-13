package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinoTest {

    @Test
    fun bebidaVaABarra() {
        assertEquals(Destino.BARRA, destinoDesdeCategoria("Bebida"))
        assertEquals(Destino.BARRA, destinoDesdeCategoria("Cerveza"))
        assertEquals(Destino.BARRA, destinoDesdeCategoria("Vino"))
    }

    @Test
    fun comidaVaACocina() {
        assertEquals(Destino.COCINA, destinoDesdeCategoria("Comida"))
        assertEquals(Destino.COCINA, destinoDesdeCategoria("Pizza"))
        assertEquals(Destino.COCINA, destinoDesdeCategoria("Croqueta"))
    }

    @Test
    fun categoriaVaciaODesconocidaVaABarraPorDefecto() {
        assertEquals(Destino.BARRA, destinoDesdeCategoria(""))
        assertEquals(Destino.BARRA, destinoDesdeCategoria("   "))
        assertEquals(Destino.BARRA, destinoDesdeCategoria("xyz"))
    }
}
