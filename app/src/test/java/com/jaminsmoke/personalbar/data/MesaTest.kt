package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MesaTest {

    private fun mesa(id: String, salaId: String, indice: Int, alias: String? = null) =
        Mesa(id = id, salaId = salaId, indiceZona = indice, alias = alias)

    @Test
    fun idZonaDerivaDeSalaEIndice() {
        assertEquals("T3", mesa("m1", "sala-terraza", 3).idZona("Terraza"))
        assertEquals("B1", mesa("m2", "sala-barra", 1).idZona("Barra"))
        assertEquals("M2", mesa("m3", "sala-x", 2).idZona(""))
    }

    @Test
    fun nombreVisibleUsaAliasSiExiste() {
        assertEquals("Ventana", mesa("m1", "sala-terraza", 1, alias = "Ventana").nombreVisible("Terraza"))
        assertEquals("T1", mesa("m1", "sala-terraza", 1).nombreVisible("Terraza"))
    }

    @Test
    fun formaDerivaModulos() {
        assertEquals(1, mesaModulos(MesaForma.REDONDA))
        assertEquals(1, mesaModulos(MesaForma.CUADRADA))
        assertEquals(2, mesaModulos(MesaForma.RECTANGULAR))
        assertEquals(3, mesaModulos(MesaForma.RECTANGULAR_XL))
    }

    @Test
    fun mesaUuidPorDefectoVacioParaCompatibilidad() {
        // Mesas construidas sin mesaUuid (p. ej. fixtures antiguos) arrancan vacías:
        // el backfill de RoomBarRepository las rellenará una sola vez. El campo es
        // aditivo: clientes LAN antiguos pueden ignorarlo.
        assertEquals("", mesa("m1", "sala-1", 1).mesaUuid)
    }

    @Test
    fun mesaUuidEsInmutableYNoSeReutilizaAlCrear() {
        // Crear dos mesas produce UUID distintos; ninguna es vacía.
        val repo = InMemoryBarRepository()
        repo.crearSala("Barra")
        assertTrue(repo.crearMesa("sala-1", MesaForma.CUADRADA, 4, null))
        assertTrue(repo.crearMesa("sala-1", MesaForma.CUADRADA, 4, null))
        val mesas = repo.mesas.value
        assertEquals(2, mesas.size)
        val u1 = mesas[0].mesaUuid
        val u2 = mesas[1].mesaUuid
        assertTrue("UUID 1 no vacío", u1.isNotEmpty())
        assertTrue("UUID 2 no vacío", u2.isNotEmpty())
        assertNotEquals("UUIDs no se reutilizan", u1, u2)
    }

    @Test
    fun borrarYRecrearNoReutilizaUuid() {
        val repo = InMemoryBarRepository()
        repo.crearSala("Barra")
        repo.crearMesa("sala-1", MesaForma.CUADRADA, 4, null)
        val antes = repo.mesas.value.first()
        repo.borrarMesa(antes.id)
        repo.crearMesa("sala-1", MesaForma.CUADRADA, 4, null)
        val despues = repo.mesas.value.first()
        assertNotEquals("UUID nuevo tras borrar+recrear", antes.mesaUuid, despues.mesaUuid)
    }
}
