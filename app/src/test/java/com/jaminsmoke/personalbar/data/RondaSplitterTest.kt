package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RondaSplitterTest {

    private val catalogo = mapOf(
        "cana" to Producto("cana", "Caña", "Bebida"),
        "croquetas" to Producto("croquetas", "Croquetas", "Comida"),
    )

    private fun linea(id: String, nombre: String, cantidad: Int = 1) =
        Linea(productoId = id, nombreProducto = nombre, cantidad = cantidad)

    @Test
    fun rondaMixtaParteEnDosTickets() {
        val ronda = Ronda(
            id = "r1",
            mesaId = "T3",
            numero = 1,
            lineas = listOf(linea("cana", "Caña", 2), linea("croquetas", "Croquetas")),
        )
        val tickets = RondaSplitter.split(ronda, catalogo)
        assertEquals(2, tickets.size)
        assertEquals(Destino.BARRA, tickets[0].destino)
        assertEquals(1, tickets[0].lineas.size)
        assertEquals(Destino.COCINA, tickets[1].destino)
        assertEquals(1, tickets[1].lineas.size)
    }

    @Test
    fun rondaSoloBebidaProduceUnTicket() {
        val ronda = Ronda(
            id = "r2",
            mesaId = "T7",
            numero = 2,
            lineas = listOf(linea("cana", "Caña", 3)),
        )
        val tickets = RondaSplitter.split(ronda, catalogo)
        assertEquals(1, tickets.size)
        assertEquals(Destino.BARRA, tickets[0].destino)
    }

    @Test
    fun lineaSinCatalogoVaABarra() {
        val ronda = Ronda(
            id = "r3",
            mesaId = "T1",
            numero = 1,
            lineas = listOf(linea("desconocido", "Algo", 1)),
        )
        val tickets = RondaSplitter.split(ronda, catalogo)
        assertEquals(1, tickets.size)
        assertEquals(Destino.BARRA, tickets[0].destino)
    }
}
