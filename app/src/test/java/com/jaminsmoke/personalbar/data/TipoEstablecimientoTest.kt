package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipoEstablecimientoTest {

    @Test
    fun apiValorMapeaTodoElCatalogo() {
        assertEquals("bar", TipoEstablecimiento.BAR.apiValor())
        assertEquals("restaurante", TipoEstablecimiento.RESTAURANTE.apiValor())
        assertEquals("cafeteria", TipoEstablecimiento.CAFETERIA.apiValor())
        assertEquals("pub", TipoEstablecimiento.PUB.apiValor())
        assertEquals("copas", TipoEstablecimiento.COPAS.apiValor())
    }

    @Test
    fun desdeApiMapeaCatalogoYNull() {
        assertEquals(TipoEstablecimiento.BAR, tipoDesdeApi("bar"))
        assertEquals(TipoEstablecimiento.COPAS, tipoDesdeApi("copas"))
        assertNull(tipoDesdeApi("otro"))
        assertNull(tipoDesdeApi(null))
    }
}
