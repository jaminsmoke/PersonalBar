package com.jaminsmoke.personalbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonaTest {

    private fun repo() = InMemoryBarRepository(
        salasIniciales = listOf(Sala("sala-1", "Terraza", 1)),
        mesasIniciales = listOf(
            Mesa(id = "mesa-1", salaId = "sala-1", indiceZona = 1, numero = 1, posX = 40f, posY = 40f),
        ),
        camarerosIniciales = listOf(Camarero(id = "c-1", nombre = "carmenTest", estado = CamareroEstado.ACTIVA)),
    )

    // ── CRUD zonas ───────────────────────────────────────────────────────────

    @Test
    fun crearZonaCreaConGeometriaYColor() {
        val r = repo()
        assertTrue(r.crearZona("sala-1", "Barra alta", ZonaColor.AMARILLO, 40f, 40f, 240f, 160f, null))
        val zona = r.zonas.value.single()
        assertEquals("zona-1", zona.id)
        assertEquals("Barra alta", zona.nombre)
        assertEquals(ZonaColor.AMARILLO, zona.color)
        assertEquals(40f, zona.posX, 0.001f)
        assertEquals(40f, zona.posY, 0.001f)
        assertEquals(240f, zona.ancho, 0.001f)
        assertEquals(160f, zona.alto, 0.001f)
        assertNull(zona.camareroId)
    }

    @Test
    fun crearZonaEnSalaInexistenteFalla() {
        assertFalse(repo().crearZona("no-existe", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null))
    }

    @Test
    fun crearZonaNombreVacioFalla() {
        assertFalse(repo().crearZona("sala-1", "  ", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null))
        assertTrue(repo().zonas.value.isEmpty())
    }

    @Test
    fun crearZonaConCamareroInactivoNoAsigna() {
        val r = repo()
        r.altaCamarero("c-2", null, "carlosTest")
        r.revocarCamarero("c-2")
        assertTrue(r.crearZona("sala-1", "VIP", ZonaColor.MORADO, 40f, 40f, 200f, 200f, "c-2"))
        // camarero revocado no se asigna (referencia blanda solo a ACTIVA)
        assertNull(r.zonas.value.single().camareroId)
    }

    @Test
    fun crearZonaEncajaDentroDelBoard() {
        val r = repo()
        // ancho/alto se acotan al board (mínimo una celda); posición fuera de límites se encaja
        assertTrue(r.crearZona("sala-1", "Zona", ZonaColor.ROJO, -500f, -500f, 10000f, 10f, null))
        val zona = r.zonas.value.single()
        assertEquals(ZONA_ANCHO, zona.ancho, 0.001f)
        assertEquals(CELL_F, zona.alto, 0.001f)
        assertEquals(0f, zona.posX, 0.001f)
        assertEquals(0f, zona.posY, 0.001f)
    }

    @Test
    fun editarZonaCambiaNombreYColor() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null)
        val id = r.zonas.value.single().id
        assertTrue(r.editarZona(id, "Zona norte", ZonaColor.VERDE))
        val zona = r.zonas.value.single()
        assertEquals("Zona norte", zona.nombre)
        assertEquals(ZonaColor.VERDE, zona.color)
        assertFalse(r.editarZona("no-existe", "X", ZonaColor.AZUL))
        assertFalse(r.editarZona(id, "  ", ZonaColor.AZUL))
    }

    @Test
    fun moverZonaActualizaPosicion() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null)
        val id = r.zonas.value.single().id
        assertTrue(r.moverZona(id, 320f, 320f))
        val zona = r.zonas.value.single()
        assertEquals(320f, zona.posX, 0.001f)
        assertEquals(320f, zona.posY, 0.001f)
        assertFalse(r.moverZona("no-existe", 0f, 0f))
    }

    @Test
    fun redimensionarZonaActualizaAnchoYAlto() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 160f, null)
        val id = r.zonas.value.single().id
        assertTrue(r.redimensionarZona(id, 400f, 320f))
        val zona = r.zonas.value.single()
        assertEquals(400f, zona.ancho, 0.001f)
        assertEquals(320f, zona.alto, 0.001f)
        assertFalse(r.redimensionarZona("no-existe", 100f, 100f))
    }

    @Test
    fun borrarZonaElimina() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null)
        val id = r.zonas.value.single().id
        assertTrue(r.borrarZona(id))
        assertTrue(r.zonas.value.isEmpty())
        assertFalse(r.borrarZona(id))
    }

    // ── Asignación de camarero ───────────────────────────────────────────────

    @Test
    fun asignarCamareroZonaAsignaYDesasigna() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null)
        val id = r.zonas.value.single().id

        assertTrue(r.asignarCamareroZona(id, "c-1"))
        assertEquals("c-1", r.zonas.value.single().camareroId)

        // desasignar con null
        assertTrue(r.asignarCamareroZona(id, null))
        assertNull(r.zonas.value.single().camareroId)
    }

    @Test
    fun asignarCamareroZonaRechazaInexistenteOInactivo() {
        val r = repo()
        r.crearZona("sala-1", "Zona", ZonaColor.AZUL, 40f, 40f, 200f, 200f, null)
        val id = r.zonas.value.single().id

        assertFalse(r.asignarCamareroZona(id, "no-existe"))
        r.altaCamarero("c-2", null, "carlosTest")
        r.revocarCamarero("c-2")
        assertFalse(r.asignarCamareroZona(id, "c-2"))
        assertNull(r.zonas.value.single().camareroId)
    }

    @Test
    fun asignarCamareroZonaZonaInexistenteFalla() {
        assertFalse(repo().asignarCamareroZona("no-existe", "c-1"))
    }

    // ── Pertenencia por intersección geométrica ──────────────────────────────

    private fun zona(ancho: Float = 400f, alto: Float = 320f) = Zona(
        id = "z-1",
        salaId = "sala-1",
        nombre = "zonaTest",
        posX = 40f,
        posY = 40f,
        ancho = ancho,
        alto = alto,
    )

    private fun mesa(posX: Float, posY: Float) = Mesa(
        id = "mesa-1",
        salaId = "sala-1",
        indiceZona = 1,
        posX = posX,
        posY = posY,
    )

    @Test
    fun zonaContieneMesa_centroDentro() {
        // mesa 120×120 en (200,200): centro (260,260) dentro de zona (40..440, 40..360)
        assertTrue(zonaContieneMesa(zona(), mesa(200f, 200f)))
    }

    @Test
    fun zonaContieneMesa_centroFuera() {
        // mesa en (1200,1200): centro fuera de la zona
        assertFalse(zonaContieneMesa(zona(), mesa(1200f, 1200f)))
    }

    @Test
    fun zonaContieneMesa_bordeInferiorDerecho() {
        // mesa cuyo centro cae justo en el borde inferior-derecho (inclusive)
        val z = zona(ancho = 400f, alto = 320f) // zona hasta (440, 360)
        assertTrue(zonaContieneMesa(z, mesa(320f, 200f))) // centro (380,260) dentro
    }

    @Test
    fun zonasDeMesa_filtraPorSala() {
        val z1 = zona()
        val zOtraSala = z1.copy(id = "z-2", salaId = "sala-otra")
        val en = zonasDeMesa(listOf(z1, zOtraSala), mesa(200f, 200f))
        assertEquals(listOf("z-1"), en.map { it.id })
    }
}
