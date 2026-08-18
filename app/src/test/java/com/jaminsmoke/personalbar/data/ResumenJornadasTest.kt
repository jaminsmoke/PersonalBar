package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de [InMemoryBarRepository.resumenJornadas]: horas, mesas distintas y filtro por periodo. */
class ResumenJornadasTest {

    private fun repoConDatos(): InMemoryBarRepository {
        val ahora = 10_000_000L
        return InMemoryBarRepository(
            establecimientoInicial = Establecimiento("local-test", "Bar Test"),
            salasIniciales = listOf(Sala(id = "s1", nombre = "Barra", orden = 1)),
            mesasIniciales = listOf(
                Mesa(id = "m1", salaId = "s1", indiceZona = 1),
                Mesa(id = "m2", salaId = "s1", indiceZona = 2),
            ),
            rondasIniciales = listOf(
                Ronda(id = "r1", mesaId = "B1", numero = 1, camarero = "c1", creadoEn = ahora - 5_000, lineas = emptyList()),
                Ronda(id = "r2", mesaId = "B2", numero = 1, camarero = "c2", creadoEn = ahora - 4_000, lineas = emptyList()),
                Ronda(id = "r3", mesaId = "B1", numero = 2, camarero = "c1", creadoEn = ahora - 3_000, lineas = emptyList()),
            ),
            jornadasIniciales = listOf(
                JornadaLocal(id = "j1", camareroId = "c1", inicio = ahora - 3_600_000, fin = ahora - 1_000),
                JornadaLocal(id = "j2", camareroId = "c2", inicio = ahora - 2_000, fin = null),
            ),
            servidosIniciales = listOf(
                Ticket(id = "t1", rondaId = "r1", destino = Destino.BARRA, estado = TicketEstado.RECOGIDO, lineas = emptyList()),
                Ticket(id = "t2", rondaId = "r2", destino = Destino.BARRA, estado = TicketEstado.RECOGIDO, lineas = emptyList()),
                Ticket(id = "t3", rondaId = "r3", destino = Destino.BARRA, estado = TicketEstado.RECOGIDO, lineas = emptyList()),
                Ticket(id = "t4", rondaId = "r1", destino = Destino.COCINA, estado = TicketEstado.PENDIENTE, lineas = emptyList()),
            ),
        )
    }

    @Test
    fun `mesas servidas cuenta mesas distintas con ronda recogida`() {
        val repo = repoConDatos()
        val resumen = repo.resumenJornadas(desde = null, hasta = null)
        // Atribución por camarero: c1 pidió r1 y r3 (ambas de B1) → 1 mesa distinta;
        // c2 pidió r2 (B2) → 1 mesa. El conteo es por camarero, no global.
        assertEquals(1, resumen.porCamarero.first { it.camareroId == "c1" }.mesasDistintas)
        assertEquals(1, resumen.porCamarero.first { it.camareroId == "c2" }.mesasDistintas)
        // La hora de c1 es el intervalo j1 cerrado (3.599.000 ms).
        assertEquals(3_599_000L, resumen.porCamarero.first { it.camareroId == "c1" }.horasMs)
        // c2 tiene jornada abierta (fin null) → horas ≥ 0.
        assertEquals(2, resumen.intervalos.size)
    }

    @Test
    fun `periodo filtra mesas por creadoEn de la ronda`() {
        val repo = repoConDatos()
        // El periodo empieza en el inicio de la jornada de c1 (-3.600.000): entra la
        // jornada, y de las rondas servidas solo las creadas dentro del periodo.
        val desde = 10_000_000L - 3_600_000
        val resumen = repo.resumenJornadas(desde = desde, hasta = null)
        // Ambas jornadas entran en el periodo (sus inicios son >= desde).
        assertEquals(2, resumen.porCamarero.size)
        val c1 = resumen.porCamarero.first { it.camareroId == "c1" }
        // De las mesas servidas por c1: r3 (creadoEn -3000 >= desde) es de B1 → 1 mesa
        // distinta (r1, -5000, queda fuera del periodo).
        assertEquals(1, c1.mesasDistintas)
        // c2: r2 (creadoEn -4000 >= desde) es de B2 → 1 mesa.
        assertEquals(1, resumen.porCamarero.first { it.camareroId == "c2" }.mesasDistintas)
    }
}
