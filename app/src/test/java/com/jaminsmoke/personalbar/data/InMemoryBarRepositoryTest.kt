package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryBarRepositoryTest {

    private val catalogo = listOf(
        Producto("cana", "Caña", "Bebida"),
        Producto("croquetas", "Croquetas", "Comida"),
    )

    private fun repo() = InMemoryBarRepository(catalogoInicial = catalogo)

    @Test
    fun crearRondaPueblaAmbasColas() {
        val repo = repo()
        repo.crearRonda(
            Ronda(
                id = "r1",
                mesaId = "T3",
                numero = 1,
                lineas = listOf(
                    Linea("cana", "Caña", 2),
                    Linea("croquetas", "Croquetas", 1),
                ),
            )
        )
        assertEquals(1, repo.bebidaQueue.value.size)
        assertEquals(1, repo.comidaQueue.value.size)
        assertEquals(Destino.BARRA, repo.bebidaQueue.value[0].destino)
        assertEquals(Destino.COCINA, repo.comidaQueue.value[0].destino)
    }

    @Test
    fun marcarListoCambiaEstadoSinSacarDeLaCola() {
        val repo = repo()
        repo.crearRonda(
            Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1)))
        )
        val ticketId = repo.bebidaQueue.value[0].id
        repo.marcarListo(ticketId)
        assertEquals(TicketEstado.LISTO, repo.bebidaQueue.value[0].estado)
        assertEquals(1, repo.bebidaQueue.value.size)
    }

    @Test
    fun marcarServidoSacaDeLaColaYAcumulaEnServidos() {
        val repo = repo()
        repo.crearRonda(
            Ronda("r1", "T3", 1, lineas = listOf(Linea("cana", "Caña", 1)))
        )
        val ticketId = repo.bebidaQueue.value[0].id
        repo.marcarServido(ticketId)
        assertTrue(repo.bebidaQueue.value.isEmpty())
        assertEquals(1, repo.servidos.value.size)
        assertEquals(TicketEstado.SERVIDO, repo.servidos.value[0].estado)
    }
}
