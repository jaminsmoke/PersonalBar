package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandadoComandasTest {

    private fun camarero(
        id: String,
        nombre: String?,
        estado: CamareroEstado = CamareroEstado.ACTIVA,
        sesionActiva: Boolean = false,
    ) = Camarero(id = id, nombre = nombre, estado = estado, sesionActiva = sesionActiva)

    private fun ronda(camarero: String?) = Ronda(
        id = "r1",
        mesaId = "T3",
        numero = 1,
        camarero = camarero,
        lineas = listOf(Linea("cana", "Caña", 1)),
    )

    @Test
    fun rondaSinCamareroAdmitida() {
        assertTrue(CandadoComandas.admitida(ronda(null), emptyList()))
    }

    @Test
    fun camareroFueraDeListaAdmitido() {
        // La admisión de membresía la gestiona POST /v1/sesion; aquí no se bloquea.
        assertTrue(CandadoComandas.admitida(ronda("Lucía García"), listOf(camarero("c-1", "Ana"))))
    }

    @Test
    fun contratadoConSesionActivaAdmitido() {
        val camareros = listOf(camarero("c-1", "Lucía García", sesionActiva = true))
        assertTrue(CandadoComandas.admitida(ronda("Lucía García"), camareros))
    }

    @Test
    fun contratadoSinSesionActivaRechazado() {
        val camareros = listOf(camarero("c-1", "Lucía García", sesionActiva = false))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), camareros))
    }

    @Test
    fun revocadoRechazadoAunqueTengaSesion() {
        val camareros = listOf(
            camarero("c-1", "Lucía García", estado = CamareroEstado.REVOCADA, sesionActiva = true),
        )
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), camareros))
    }

    @Test
    fun emparejaPorNombreNormalizado() {
        val camareros = listOf(camarero("c-1", "LUCÍA García", sesionActiva = true))
        assertTrue(CandadoComandas.admitida(ronda("lucia garcía"), camareros))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(camarero("c-1", "Lucía García"))))
    }

    @Test
    fun camareroSinNombreNoBloquea() {
        // Nombre null tras sincronizarMiembros: no hay emparejamiento posible → no bloquea aquí.
        val camareros = listOf(camarero("c-1", null))
        assertTrue(CandadoComandas.admitida(ronda("Lucía García"), camareros))
    }

    @Test
    fun unoDeVariosActivoAdmite() {
        val camareros = listOf(
            camarero("c-1", "Lucía García", estado = CamareroEstado.REVOCADA),
            camarero("c-2", "Lucía García", sesionActiva = true),
        )
        assertTrue(CandadoComandas.admitida(ronda("Lucía García"), camareros))
    }
}
