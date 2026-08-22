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

    private val lucia = camarero("c-1", "Lucía García")

    @Test
    fun sinCamareroAutenticadoSiempreRechazado() {
        // v0.2: la ruta ya es privada, pero el candado nunca debe pasar sin sesión.
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(lucia.copy(sesionActiva = true)), ""))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(lucia.copy(sesionActiva = true)), "desconocido"))
    }

    @Test
    fun rondaSinCamareroRechazada() {
        // v0.2: nombre vacío ya no pasa (suplantación bloqueada).
        assertFalse(CandadoComandas.admitida(ronda(null), listOf(lucia.copy(sesionActiva = true)), "c-1"))
        assertFalse(CandadoComandas.admitida(ronda(""), listOf(lucia.copy(sesionActiva = true)), "c-1"))
    }

    @Test
    fun rondaDeOtroCamareroRechazada() {
        // Suplantación: el autenticado es Ana, la ronda es de Lucía → bloqueada.
        val ana = camarero("c-2", "Ana", sesionActiva = true)
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(ana, lucia.copy(sesionActiva = true)), "c-2"))
    }

    @Test
    fun camareroFueraDeListaRechazado() {
        // v0.2: fuera de la lista blanca ya no pasa.
        val camareros = listOf(camarero("c-1", "Ana", sesionActiva = true))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), camareros, "c-1"))
    }

    @Test
    fun contratadoConSesionActivaAdmitido() {
        val camareros = listOf(lucia.copy(sesionActiva = true))
        assertTrue(CandadoComandas.admitida(ronda("Lucía García"), camareros, "c-1"))
    }

    @Test
    fun contratadoSinSesionActivaRechazado() {
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(lucia), "c-1"))
    }

    @Test
    fun revocadoRechazadoAunqueTengaSesion() {
        val camareros = listOf(lucia.copy(estado = CamareroEstado.REVOCADA, sesionActiva = true))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), camareros, "c-1"))
    }

    @Test
    fun emparejaPorNombreNormalizado() {
        val camareros = listOf(lucia.copy(nombre = "LUCÍA García", sesionActiva = true))
        assertTrue(CandadoComandas.admitida(ronda("lucia garcía"), camareros, "c-1"))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), listOf(lucia), "c-1"))
    }

    @Test
    fun camareroSinNombreNoPuedeComandar() {
        // Nombre null en lista blanca: el autenticado no tiene nombre que emparejar → no comanda.
        val camareros = listOf(camarero("c-1", null, sesionActiva = true))
        assertFalse(CandadoComandas.admitida(ronda("Lucía García"), camareros, "c-1"))
    }
}
