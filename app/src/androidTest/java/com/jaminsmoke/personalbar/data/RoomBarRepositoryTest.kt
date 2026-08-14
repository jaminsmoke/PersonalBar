package com.jaminsmoke.personalbar.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test de integración del [RoomBarRepository] (persistencia del nodo).
 *
 * Cubre:
 * - Seed demo solo si la BD está vacía (primera instalación)
 * - Persistencia y recarga completa (segunda instancia sobre la misma BD)
 * - Idempotencia de rondas tras recarga
 * - Ciclo preparado → recogido que sobrevive a la recarga
 * - Config de Identity y camareros persistidos
 */
class RoomBarRepositoryTest {

    private lateinit var db: AppDatabase

    private val demoEstablecimiento = Establecimiento(idEstable = "local-1", nombre = "La Terraza")
    private val demoSala = Sala(id = "sala-barra", nombre = "Barra", orden = 1)
    private val demoCatalogo = listOf(
        Producto(id = "cana", nombre = "Caña", categoria = "Bebida"),
        Producto(id = "croquetas", nombre = "Croquetas", categoria = "Comida"),
    )
    private val demoMesa = Mesa(
        id = "mesa-1", salaId = "sala-barra", indiceZona = 1, numero = 1,
        forma = MesaForma.CUADRADA, capacidad = 4, posX = 40f, posY = 40f,
    )
    private val demoRonda = Ronda(
        id = "r1", mesaId = "B1", numero = 1, camarero = "Lucía",
        lineas = listOf(
            Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 2),
            Linea(productoId = "croquetas", nombreProducto = "Croquetas", cantidad = 1),
        ),
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun nuevoRepo() = RoomBarRepository(
        db = db,
        establecimientoInicial = demoEstablecimiento,
        salasIniciales = listOf(demoSala),
        catalogoInicial = demoCatalogo,
        mesasIniciales = listOf(demoMesa),
        rondasDemo = listOf(demoRonda),
    )

    // ═══ Seed: solo en BD vacía ═══

    @Test
    fun seed_se_siembra_en_primera_instalacion() = runBlocking {
        val repo = nuevoRepo()

        assertEquals(demoEstablecimiento, repo.establecimiento.first())
        assertEquals(listOf(demoSala), repo.salas.first())
        assertEquals(demoCatalogo, repo.catalogo.first())
        assertEquals(listOf(demoMesa), repo.mesas.first())

        // La ronda demo se parte en colas: BARRA (caña) y COCINA (croquetas)
        assertEquals(1, repo.bebidaQueue.first().size)
        assertEquals(1, repo.comidaQueue.first().size)
        assertEquals(1, repo.rondas.first().size) // solo la ronda demo sembrada
        // El id de cola se asigna por destino (Cola 1 Bebida y Cola 1 Comida independientes)
        assertEquals(1, repo.bebidaQueue.first().first().numeroCola)
        assertEquals(1, repo.comidaQueue.first().first().numeroCola)
    }

    // ═══ Persistencia y recarga ═══

    @Test
    fun recarga_completa_desde_room() = runBlocking {
        val repo1 = nuevoRepo()

        // Mutar: crear sala/mesa, alta de camarero, config Identity, preparar ticket
        assertTrue(repo1.crearSala("Terraza"))
        val salaTerraza = repo1.salas.first().first { it.nombre == "Terraza" }.id
        assertTrue(repo1.crearMesa(salaTerraza, MesaForma.REDONDA, 2, null))
        assertTrue(repo1.altaCamarero("cam-1", null))
        repo1.setIdentityConfig(
            IdentityConfig(conectado = true, baseUrl = "http://10.0.2.2:8082", establecimientoUuid = "uuid-1")
        )
        val ticketBebida = repo1.bebidaQueue.first().first()
        assertTrue(repo1.marcarPreparado(ticketBebida.id, "cam-1"))
        assertTrue(repo1.marcarRecogido(ticketBebida.id))
        repo1.awaitPersistencia()

        // Segunda instancia sobre la misma BD = "reinicio de la app"
        val repo2 = nuevoRepo()

        assertEquals("Terraza", repo2.salas.first().first { it.nombre == "Terraza" }.nombre)
        assertEquals(1, repo2.mesas.first().count { it.salaId == salaTerraza })
        assertEquals("cam-1", repo2.camareros.first().first().id)
        assertTrue(repo2.identityConfig.first().conectado)
        assertEquals("uuid-1", repo2.identityConfig.first().establecimientoUuid)

        // El ticket recogido sale de la cola y queda en servidos
        assertEquals(0, repo2.bebidaQueue.first().size)
        assertEquals(1, repo2.servidos.first().size)
        assertEquals(TicketEstado.RECOGIDO, repo2.servidos.first().first().estado)
        assertEquals("cam-1", repo2.servidos.first().first().preparadoPor)

        // La cola de comida sigue intacta
        assertEquals(1, repo2.comidaQueue.first().size)
        // El id de cola se persiste y sobrevive a la recarga
        assertEquals(1, repo2.servidos.first().first().numeroCola)
    }

    // ═══ Secuencia de id de cola por destino ═══

    @Test
    fun id_de_cola_es_monotono_por_destino_y_no_compacta() = runBlocking {
        val repo1 = nuevoRepo()

        // Nueva ronda con bebida y comida → Cola 2 Bebida, Cola 2 Comida
        assertTrue(
            repo1.crearRonda(
                Ronda(
                    id = "r2", mesaId = "B1", numero = 2,
                    lineas = listOf(
                        Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 1),
                        Linea(productoId = "croquetas", nombreProducto = "Croquetas", cantidad = 1),
                    ),
                )
            )
        )
        assertEquals(1, repo1.bebidaQueue.first().first { it.rondaId == "r1" }.numeroCola)
        assertEquals(2, repo1.bebidaQueue.first().first { it.rondaId == "r2" }.numeroCola)
        assertEquals(1, repo1.comidaQueue.first().first { it.rondaId == "r1" }.numeroCola)
        assertEquals(2, repo1.comidaQueue.first().first { it.rondaId == "r2" }.numeroCola)

        // Recoger Cola 1 Bebida → Cola 2 Bebida NO pasa a 1 (ancla estable)
        val ticket1 = repo1.bebidaQueue.first().first { it.numeroCola == 1 }
        assertTrue(repo1.marcarPreparado(ticket1.id, "cam-1"))
        assertTrue(repo1.marcarRecogido(ticket1.id))
        assertEquals(2, repo1.bebidaQueue.first().first { it.rondaId == "r2" }.numeroCola)
        repo1.awaitPersistencia()

        // Tras recarga, la secuencia continúa (Cola 3) sin colisionar
        val repo2 = nuevoRepo()
        assertTrue(
            repo2.crearRonda(
                Ronda(
                    id = "r3", mesaId = "B1", numero = 3,
                    lineas = listOf(Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 1)),
                )
            )
        )
        assertEquals(3, repo2.bebidaQueue.first().first { it.rondaId == "r3" }.numeroCola)
    }

    // ═══ Idempotencia tras recarga ═══

    @Test
    fun idempotencia_de_ronda_sobrevive_a_la_recarga() = runBlocking {
        val repo1 = nuevoRepo()
        assertFalse("La ronda demo ya existe", repo1.crearRonda(demoRonda))
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        assertFalse("Sigue siendo idempotente tras recarga", repo2.crearRonda(demoRonda))
        assertEquals(1, repo2.bebidaQueue.first().size) // no se duplican tickets
    }

    // ═══ Secuencias de IDs continúan tras recarga ═══

    @Test
    fun secuencias_continuan_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()
        assertTrue(repo1.crearSala("Terraza"))
        val salaTerraza = repo1.salas.first().first { it.nombre == "Terraza" }.id
        assertTrue(repo1.crearMesa(salaTerraza, MesaForma.REDONDA, 2, null))
        val mesaId1 = repo1.mesas.first().first { it.salaId == salaTerraza }.id
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        assertTrue("No colisiona con el seed", repo2.crearMesa(salaTerraza, MesaForma.REDONDA, 2, null))
        val nuevos = repo2.mesas.first().filter { it.salaId == salaTerraza }
        assertEquals(2, nuevos.size)
        // La mesa nueva de repo2 continúa la secuencia: no reutiliza ids existentes
        val mesaNueva = nuevos.first { it.id != mesaId1 }
        assertTrue(mesaNueva.id != "mesa-1" && mesaNueva.id != mesaId1)
    }

    // ═══ Invitaciones y revocaciones persistidas ═══

    @Test
    fun invitaciones_persisten_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()
        repo1.registrarInvitacion(
            Invitacion(id = "inv-1", email = "x@y.es", rol = "staff", estado = InvitacionEstado.PENDIENTE)
        )
        assertTrue(repo1.revocarInvitacionLocal("inv-1"))
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        val inv = repo2.invitaciones.first().first()
        assertEquals("x@y.es", inv.email)
        assertEquals(InvitacionEstado.REVOCADA, inv.estado)
    }

    // ═══ Espejo de Identity (sincronizarMiembros) ═══

    @Test
    fun sincronizar_miembros_persiste() = runBlocking {
        val repo1 = nuevoRepo()
        repo1.sincronizarMiembros(listOf("cam-a", "cam-b"))
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        assertEquals(2, repo2.camareros.first().size)
        assertEquals(setOf("cam-a", "cam-b"), repo2.camareros.first().map { it.id }.toSet())
    }

    // ═══ De servicio: varios preparadores + persistencia ═══

    @Test
    fun de_servicio_varios_y_persistido() = runBlocking {
        val repo1 = nuevoRepo()
        repo1.sincronizarMiembros(listOf("cam-a", "cam-b"))
        repo1.ponerDeServicio("cam-a")
        repo1.ponerDeServicio("cam-b")
        repo1.awaitPersistencia()

        // Dos de servicio a la vez.
        assertEquals(2, repo1.deServicio.first().size)

        // Recarga: el turno sobrevive (coherente con la persistencia del nodo).
        val repo2 = nuevoRepo()
        assertEquals(2, repo2.deServicio.first().size)
        assertEquals(setOf("cam-a", "cam-b"), repo2.deServicio.first().map { it.id }.toSet())

        // Quitar uno no afecta al otro; y sobrevive a la recarga.
        assertTrue(repo2.quitarDeServicio("cam-a"))
        repo2.awaitPersistencia()
        val repo3 = nuevoRepo()
        assertEquals(listOf("cam-b"), repo3.deServicio.first().map { it.id })
    }
}
