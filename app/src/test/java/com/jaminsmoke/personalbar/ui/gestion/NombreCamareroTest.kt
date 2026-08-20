package com.jaminsmoke.personalbar.ui.gestion

import com.jaminsmoke.personalbar.data.Camarero
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de [nombreCamarero]: resolución de nombre visible para la sección de jornadas. */
class NombreCamareroTest {

    private val camareros = listOf(
        Camarero(id = "cam-12345678", nombre = "Lucia Garcia Test"),
        Camarero(id = "cam-sin-nombre", nombre = null),
    )

    @Test
    fun `nombre presente se usa tal cual`() {
        assertEquals("Lucia Garcia Test", nombreCamarero(camareros, "cam-12345678"))
    }

    @Test
    fun `nombre null cae al prefijo corto del id`() {
        assertEquals("cam-sin-", nombreCamarero(camareros, "cam-sin-nombre"))
    }

    @Test
    fun `id inexistente cae al prefijo corto del id recibido`() {
        assertEquals("cam-otro", nombreCamarero(camareros, "cam-otro-id"))
    }
}
