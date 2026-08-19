package com.jaminsmoke.personalbar.ui

import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.ModificadorLinea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoLineaExpoTest {

    @Test
    fun lineaSinExtrasEsCantidadPorNombre() {
        assertEquals("2x Caña", formatoLineaExpo(Linea("cana", "Caña", 2)))
    }

    @Test
    fun lineaConNotaApuntaLaNota() {
        val texto = formatoLineaExpo(Linea("cana", "Caña", 1, nota = "sin espuma"))
        assertEquals("1x Caña · sin espuma", texto)
    }

    @Test
    fun lineaConModificadorDeltaCeroPintaSoloNombre() {
        val linea = Linea(
            "cana", "Caña", 1,
            modificadores = listOf(ModificadorLinea("Punto", "Al punto", 0.0)),
        )
        assertEquals("1x Caña · Al punto", formatoLineaExpo(linea))
    }

    @Test
    fun lineaConModificadorDeltaNoCeroPintaDelta() {
        val linea = Linea(
            "cana", "Caña", 1,
            modificadores = listOf(ModificadorLinea("Extras", "Doble", 0.5)),
        )
        val texto = formatoLineaExpo(linea)
        // El formato exacto del delta depende del locale; se valida la estructura.
        assertTrue(texto.startsWith("1x Caña · Doble +"))
    }

    @Test
    fun lineaConModificadorVacioSeIgnora() {
        val linea = Linea(
            "cana", "Caña", 1,
            modificadores = listOf(ModificadorLinea("Punto", "   ", 0.0)),
        )
        assertEquals("1x Caña", formatoLineaExpo(linea))
    }

    @Test
    fun lineaConNotaYModificadorJuntaAmbos() {
        val linea = Linea(
            "cana", "Caña", 1,
            nota = "sin espuma",
            modificadores = listOf(ModificadorLinea("Punto", "Al punto", 0.0)),
        )
        assertEquals("1x Caña · Al punto · sin espuma", formatoLineaExpo(linea))
    }
}
