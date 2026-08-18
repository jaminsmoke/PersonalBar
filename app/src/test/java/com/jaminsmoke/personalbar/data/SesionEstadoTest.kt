package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de la derivación pura [sesionEstadoDe]: SIN_SESION / VALIDA / CADUCADA / INVALIDA. */
class SesionEstadoTest {

    private val ahora = 1_000_000L

    private fun sesion(validaHasta: Long?) = SesionNegocio(
        token = "tok-test",
        email = "barTest@example.com",
        nombreMostrar = "Bar Test",
        establecimientoUuid = "uuid-test",
        validaHasta = validaHasta,
    )

    @Test
    fun `sin sesion es SIN_SESION`() {
        assertEquals(SesionEstado.SIN_SESION, sesionEstadoDe(null, ahora))
    }

    @Test
    fun `sesion sin token es SIN_SESION`() {
        val sinToken = SesionNegocio(token = null, validaHasta = ahora + 1_000)
        assertEquals(SesionEstado.SIN_SESION, sesionEstadoDe(sinToken, ahora))
    }

    @Test
    fun `sesion dentro de validaHasta es VALIDA`() {
        assertEquals(SesionEstado.VALIDA, sesionEstadoDe(sesion(ahora + 1), ahora))
    }

    @Test
    fun `sesion justo en el borde de validaHasta es VALIDA`() {
        assertEquals(SesionEstado.VALIDA, sesionEstadoDe(sesion(ahora), ahora))
    }

    @Test
    fun `sesion pasada de validaHasta es CADUCADA`() {
        assertEquals(SesionEstado.CADUCADA, sesionEstadoDe(sesion(ahora - 1), ahora))
    }

    @Test
    fun `validaHasta nulo es INVALIDA`() {
        assertEquals(SesionEstado.INVALIDA, sesionEstadoDe(sesion(null), ahora))
    }

    @Test
    fun `validaHasta cero es INVALIDA (401 revocada)`() {
        assertEquals(SesionEstado.INVALIDA, sesionEstadoDe(sesion(0L), ahora))
    }

    @Test
    fun `validaHasta negativo es INVALIDA`() {
        assertEquals(SesionEstado.INVALIDA, sesionEstadoDe(sesion(-1L), ahora))
    }
}
