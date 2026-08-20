package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartaModificadoresTest {

    private val punto = GrupoModificador("g-punto", "PuntoTest", multiple = false, obligatorio = true)
    private val extras = GrupoModificador("g-extras", "ExtrasTest", multiple = true, obligatorio = false)
    private val alPunto = OpcionModificador("o-al-punto", "g-punto", "Al punto", 0.0, "")
    private val muyHecho = OpcionModificador("o-muy-hecho", "g-punto", "Muy hecho", 0.0, "muy hecha|bien hecho")
    private val bacon = OpcionModificador("o-bacon", "g-extras", "Bacon", 1.5, "extra bacon")

    @Test
    fun precioUnitarioSumaDeltas() {
        val elegidos = listOf(
            ModificadorLinea("PuntoTest", "Al punto", 0.0, "g-punto", "o-al-punto"),
            ModificadorLinea("ExtrasTest", "Bacon", 1.5, "g-extras", "o-bacon"),
        )
        assertEquals(10.0, CartaModificadores.precioUnitario(8.5, elegidos), 0.0)
        assertEquals(8.5, CartaModificadores.precioUnitario(8.5, emptyList()), 0.0)
    }

    @Test
    fun textoLineaUneOpcionesYNota() {
        val elegidos = listOf(
            ModificadorLinea("PuntoTest", "Al punto", 0.0),
            ModificadorLinea("ExtrasTest", "Bacon", 1.5),
        )
        assertEquals("Al punto · Bacon · sin cebolla", CartaModificadores.textoLinea(elegidos, "sin cebolla"))
        assertEquals("", CartaModificadores.textoLinea(emptyList(), "   "))
    }

    @Test
    fun gruposDeProductoFiltraAsignaciones() {
        val grupos = listOf(punto, extras)
        val opciones = listOf(alPunto, muyHecho, bacon)
        val asignaciones = listOf(ProductoGrupo("hamburguesaTest", "g-punto"))
        val resultado = CartaModificadores.gruposDeProducto("hamburguesaTest", grupos, opciones, asignaciones)
        assertEquals(1, resultado.size)
        assertEquals("PuntoTest", resultado.single().grupo.nombre)
        assertEquals(listOf("Al punto", "Muy hecho"), resultado.single().opciones.map { it.nombre })
    }

    @Test
    fun faltanObligatoriosDetectaGrupoSinElegir() {
        val grupos = listOf(GrupoConOpciones(punto, listOf(alPunto)), GrupoConOpciones(extras, listOf(bacon)))
        assertTrue(CartaModificadores.faltanObligatorios(grupos, emptyList()))
        val elegido = ModificadorLinea("PuntoTest", "Al punto", 0.0, "g-punto", "o-al-punto")
        assertFalse(CartaModificadores.faltanObligatorios(grupos, listOf(elegido)))
    }

    @Test
    fun tokensOpcionIncluyeNombreYAlias() {
        assertEquals(
            listOf(listOf("muy", "hecho"), listOf("muy", "hecha"), listOf("bien", "hecho")),
            CartaModificadores.tokensOpcion(muyHecho),
        )
    }

    @Test
    fun snapshotOrdenadoOrdenaPorGrupoYopcion() {
        val a = ModificadorLinea("ExtrasTest", "Bacon", 1.5, "g-extras", "o-bacon")
        val b = ModificadorLinea("PuntoTest", "Al punto", 0.0, "g-punto", "o-al-punto")
        assertEquals(listOf(a, b), CartaModificadores.snapshotOrdenado(listOf(b, a)))
    }

    @Test
    fun agruparPorSubfamiliaAgrupaConsecutivos() {
        val productos = listOf(
            Producto("p1", "Coca-ColaTest", "Bebida", subfamilia = "Zero"),
            Producto("p2", "Coca-ColaTest", "Bebida", subfamilia = "Zero"),
            Producto("p3", "AguaTest", "Bebida"),
        )
        val grupos = CartaModificadores.agruparPorSubfamilia(productos)
        assertEquals(2, grupos.size)
        assertEquals("Zero", grupos[0].first)
        assertEquals(2, grupos[0].second.size)
        assertEquals(null, grupos[1].first)
    }

    @Test
    fun formatoDeltaUsaMonedaLocal() {
        assertTrue(CartaModificadores.formatoDelta(0.5).isNotBlank())
    }
}
