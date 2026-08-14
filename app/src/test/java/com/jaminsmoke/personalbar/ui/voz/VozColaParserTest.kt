package com.jaminsmoke.personalbar.ui.voz

import com.jaminsmoke.personalbar.data.Destino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VozColaParserTest {

    @Test
    fun preparado_con_nombre_y_destino_bebida() {
        val orden = VozColaParser.parsear("Lucia, Cola 1 Bebida preparado")
        assertTrue(orden is OrdenColaVoz.Preparado)
        orden as OrdenColaVoz.Preparado
        assertEquals("lucia", orden.nombre)
        assertEquals(1, orden.numeroCola)
        assertEquals(Destino.BARRA, orden.destino)
    }

    @Test
    fun preparado_numero_en_letra() {
        val orden = VozColaParser.parsear("cola uno bebida preparado")
        assertTrue(orden is OrdenColaVoz.Preparado)
        orden as OrdenColaVoz.Preparado
        assertEquals(null, orden.nombre)
        assertEquals(1, orden.numeroCola)
        assertEquals(Destino.BARRA, orden.destino)
    }

    @Test
    fun recogido_con_nombre_opcional_y_comida() {
        val orden = VozColaParser.parsear("Manolo Cola 2 Comida recogido")
        assertTrue(orden is OrdenColaVoz.Recogido)
        orden as OrdenColaVoz.Recogido
        assertEquals(2, orden.numeroCola)
        assertEquals(Destino.COCINA, orden.destino)
    }

    @Test
    fun recogido_sin_nombre() {
        val orden = VozColaParser.parsear("Cola 2 comida recogido")
        assertTrue(orden is OrdenColaVoz.Recogido)
    }

    @Test
    fun relleno_entre_numero_y_destino() {
        val orden = VozColaParser.parsear("cola 1 de bebida preparado")
        assertTrue(orden is OrdenColaVoz.Preparado)
        orden as OrdenColaVoz.Preparado
        assertEquals(1, orden.numeroCola)
        assertEquals(Destino.BARRA, orden.destino)
    }

    @Test
    fun numero_compuesto_en_letra() {
        val orden = VozColaParser.parsear("Cola treinta y cinco comida recogido")
        assertTrue(orden is OrdenColaVoz.Recogido)
        orden as OrdenColaVoz.Recogido
        assertEquals(35, orden.numeroCola)
    }

    @Test
    fun numero_dieciocho_en_letra() {
        val orden = VozColaParser.parsear("Cola dieciocho bebida preparado")
        assertTrue(orden is OrdenColaVoz.Preparado)
        orden as OrdenColaVoz.Preparado
        assertEquals(18, orden.numeroCola)
    }

    @Test
    fun cocina_no_es_destino_valido() {
        val orden = VozColaParser.parsear("Cola 1 cocina recogido")
        assertTrue(orden is OrdenColaVoz.NoEntendido)
    }

    @Test
    fun sin_accion_no_entendido() {
        assertTrue(VozColaParser.parsear("Cola 1 Bebida") is OrdenColaVoz.NoEntendido)
    }

    @Test
    fun vacio_no_entendido() {
        assertTrue(VozColaParser.parsear("") is OrdenColaVoz.NoEntendido)
    }

    @Test
    fun numero_cero_invalido() {
        assertTrue(VozColaParser.parsear("Cola 0 bebida preparado") is OrdenColaVoz.NoEntendido)
    }

    @Test
    fun normalizar_quita_tildes_y_puntuacion() {
        assertEquals("lucia cola 1 bebida preparado", VozColaParser.normalizar("Lucía, ¡Cola 1 Bebida preparado!"))
    }
}
