package com.jaminsmoke.personalbar.data

import com.jaminsmoke.personalbar.lan.PedidoCfcLinea
import com.jaminsmoke.personalbar.lan.PedidoCfcResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PedidoCfcTransformerTest {

    private val sala = Sala(id = "sala-barra", nombre = "Barra", orden = 1)

    private val mesa = Mesa(
        id = "m1",
        mesaUuid = "uuid-mesa-1",
        salaId = "sala-barra",
        indiceZona = 3,
        alias = null,
    )

    private fun pedido(
        id: String = "pedido-1",
        mesaUuid: String = "uuid-mesa-1",
        seq: Int = 1,
        lineas: List<PedidoCfcLinea> = listOf(
            PedidoCfcLinea(productoId = "p1", nombre = "Caña", cantidad = 2, destino = "barra"),
        ),
    ) = PedidoCfcResponse(
        id = id,
        mesaUuid = mesaUuid,
        etiqueta = "B3",
        estado = "PENDIENTE",
        seq = seq,
        lineas = lineas,
        totalCentimos = 400,
        creadoEn = "2026-08-22T10:00:00Z",
    )

    @Test
    fun mapeaPedidoARondaConMesaResuelta() {
        val ronda = PedidoCfcTransformer.transformar(pedido(), listOf(mesa), listOf(sala), emptyList())
        assertEquals("pedido-1", ronda?.id)
        assertEquals("B3", ronda?.mesaId) // idZona("Barra") + indice 3
        assertEquals(1, ronda?.numero)
        assertNull(ronda?.camarero) // pedido del cliente, no de camarero
        assertEquals(1, ronda?.lineas?.size)
        assertEquals("p1", ronda?.lineas?.first()?.productoId)
        assertEquals("Caña", ronda?.lineas?.first()?.nombreProducto)
        assertEquals(2, ronda?.lineas?.first()?.cantidad)
    }

    @Test
    fun mesaNoResueltaDevuelveNull() {
        val ronda = PedidoCfcTransformer.transformar(pedido(mesaUuid = "uuid-inexistente"), listOf(mesa), listOf(sala), emptyList())
        assertNull(ronda)
    }

    @Test
    fun numeroIncrementaConRondasExistentesDeLaMesa() {
        val existente = Ronda(
            id = "r-anterior",
            mesaId = "B3",
            numero = 2,
            lineas = listOf(Linea(productoId = "p1", nombreProducto = "Caña", cantidad = 1)),
        )
        val ronda = PedidoCfcTransformer.transformar(pedido(), listOf(mesa), listOf(sala), listOf(existente))
        assertEquals(3, ronda?.numero)
    }

    @Test
    fun numeroIgnoraRondasDeOtrasMesas() {
        val otraMesa = Ronda(
            id = "r-otra",
            mesaId = "T1",
            numero = 7,
            lineas = listOf(Linea(productoId = "p1", nombreProducto = "Caña", cantidad = 1)),
        )
        val ronda = PedidoCfcTransformer.transformar(pedido(), listOf(mesa), listOf(sala), listOf(otraMesa))
        assertEquals(1, ronda?.numero)
    }

    @Test
    fun mapeaVariasLineas() {
        val p = pedido(
            lineas = listOf(
                PedidoCfcLinea(productoId = "p1", nombre = "Caña", cantidad = 2, destino = "barra"),
                PedidoCfcLinea(productoId = "p2", nombre = "Pizza", cantidad = 1, destino = "cocina"),
            ),
        )
        val ronda = PedidoCfcTransformer.transformar(p, listOf(mesa), listOf(sala), emptyList())
        assertEquals(2, ronda?.lineas?.size)
        assertEquals("Pizza", ronda?.lineas?.get(1)?.nombreProducto)
    }

    @Test
    fun aliasUsadoComoIdZonaNoAfectaAlUuid() {
        val mesaAlias = mesa.copy(alias = "Ventana")
        val ronda = PedidoCfcTransformer.transformar(pedido(), listOf(mesaAlias), listOf(sala), emptyList())
        // idZona sigue derivando del índice (el alias es solo nombre visible)
        assertEquals("B3", ronda?.mesaId)
    }
}
