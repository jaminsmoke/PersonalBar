package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescripcionProductoTest {

    @Test
    fun vaciaOBlancoQuedaNull() {
        assertNull(normalizarDescripcionProducto(null))
        assertNull(normalizarDescripcionProducto(""))
        assertNull(normalizarDescripcionProducto("   "))
    }

    @Test
    fun recortaEspaciosYTopeDeIdentity() {
        assertEquals("Gin, vermut y Campari", normalizarDescripcionProducto("  Gin, vermut y Campari  "))
        val largo = "x".repeat(DESCRIPCION_PRODUCTO_MAX + 40)
        val recortado = normalizarDescripcionProducto(largo)
        assertEquals(DESCRIPCION_PRODUCTO_MAX, recortado!!.length)
    }
}
