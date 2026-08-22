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

    private val demoEstablecimiento = Establecimiento(idEstable = "local-1", nombre = "La Terraza Test")
    private val demoSala = Sala(id = "sala-barra", nombre = "Barra Test", orden = 1)
    private val demoCatalogo = listOf(
        Producto(id = "cana", nombre = "Caña Test", categoria = "Bebida"),
        Producto(id = "croquetas", nombre = "Croquetas Test", categoria = "Comida"),
    )
    private val demoMesa = Mesa(
        id = "mesa-1", salaId = "sala-barra", indiceZona = 1, numero = 1,
        forma = MesaForma.CUADRADA, capacidad = 4, posX = 40f, posY = 40f,
    )
    private val demoRonda = Ronda(
        id = "r1", mesaId = "B1", numero = 1, camarero = "Lucía Test",
        lineas = listOf(
            Linea(productoId = "cana", nombreProducto = "Caña Test", cantidad = 2),
            Linea(productoId = "croquetas", nombreProducto = "Croquetas Test", cantidad = 1),
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

    @Test
    fun sin_rondas_demo_arranca_con_colas_vacias() = runBlocking {
        // Config de producción (PersonalBarApp): salas/mesas/catálogo por defecto, SIN rondas demo.
        val repo = RoomBarRepository(
            db = db,
            salasIniciales = listOf(demoSala),
            mesasIniciales = listOf(demoMesa),
            catalogoInicial = demoCatalogo,
        )

        assertEquals("Mi local", repo.establecimiento.first().nombre)
        assertEquals(listOf(demoSala), repo.salas.first())
        assertEquals(listOf(demoMesa), repo.mesas.first())
        assertEquals(demoCatalogo, repo.catalogo.first())
        assertTrue(repo.bebidaQueue.first().isEmpty())
        assertTrue(repo.comidaQueue.first().isEmpty())
        assertTrue(repo.rondas.first().isEmpty())
    }

    // ═══ Persistencia y recarga ═══

    @Test
    fun recarga_completa_desde_room() = runBlocking {
        val repo1 = nuevoRepo()

        // Mutar: crear sala/mesa, alta de camarero, config Identity, preparar ticket
        assertTrue(repo1.crearSala("Terraza Test"))
        val salaTerraza = repo1.salas.first().first { it.nombre == "Terraza Test" }.id
        assertTrue(repo1.crearMesa(salaTerraza, MesaForma.REDONDA, 2, null))
        assertTrue(repo1.altaCamarero("cam-1", null))
        repo1.setIdentityConfig(
            IdentityConfig(conectado = true, baseUrl = "http://10.0.2.2:8082", establecimientoUuid = "uuid-1")
        )
        val ticketBebida = repo1.bebidaQueue.first().first()
        assertTrue(repo1.marcarPreparado(ticketBebida.id, "cam-1"))
        assertTrue(repo1.marcarRecogido(ticketBebida.id))
        repo1.awaitPersistencia()

        // «Recuérdame»: sesión con token → `conectado=true` sobrevive a la recarga.
        db.barDao().upsertSesionNegocio(
            SesionNegocio(token = "tok-1", email = "negocio@x.es", establecimientoUuid = "uuid-1")
        )

        // Segunda instancia sobre la misma BD = "reinicio de la app"
        val repo2 = nuevoRepo()

        assertEquals("Terraza Test", repo2.salas.first().first { it.nombre == "Terraza Test" }.nombre)
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
                        Linea(productoId = "cana", nombreProducto = "Caña Test", cantidad = 1),
                        Linea(productoId = "croquetas", nombreProducto = "Croquetas Test", cantidad = 1),
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
                    lineas = listOf(Linea(productoId = "cana", nombreProducto = "Caña Test", cantidad = 1)),
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
        assertTrue(repo1.crearSala("Terraza Test"))
        val salaTerraza = repo1.salas.first().first { it.nombre == "Terraza Test" }.id
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

    // ═══ Zonas: CRUD + asignación persistidas ═══

    @Test
    fun zonas_persisten_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()
        assertTrue(repo1.altaCamarero("cam-1", null, nombre = "carmenTest"))
        assertTrue(
            repo1.crearZona("sala-barra", "Barra alta Test", ZonaColor.AMARILLO, 40f, 40f, 240f, 160f, null)
        )
        val zonaId = repo1.zonas.first().single().id
        assertTrue(repo1.asignarCamareroZona(zonaId, "cam-1"))
        repo1.awaitPersistencia()

        // Segunda instancia sobre la misma BD = "reinicio de la app"
        val repo2 = nuevoRepo()
        val zona = repo2.zonas.first().single()
        assertEquals("sala-barra", zona.salaId)
        assertEquals("Barra alta Test", zona.nombre)
        assertEquals(ZonaColor.AMARILLO, zona.color)
        assertEquals(40f, zona.posX, 0.001f)
        assertEquals(40f, zona.posY, 0.001f)
        assertEquals(240f, zona.ancho, 0.001f)
        assertEquals(160f, zona.alto, 0.001f)
        assertEquals("cam-1", zona.camareroId)

        // Mutar en repo2 y volver a recargar: mover + desasignar sobreviven.
        assertTrue(repo2.moverZona(zonaId, 320f, 320f))
        assertTrue(repo2.asignarCamareroZona(zonaId, null))
        repo2.awaitPersistencia()
        val repo3 = nuevoRepo()
        val zona3 = repo3.zonas.first().single()
        assertEquals(320f, zona3.posX, 0.001f)
        assertEquals(320f, zona3.posY, 0.001f)
        assertEquals(null, zona3.camareroId)
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

    // ═══ Divergencia de identity_config (gate de sesión) ═══

    @Test
    fun identity_config_conectado_sin_sesion_se_corrige() = runBlocking {
        // Simula el bug: login sin «Recuérdame» dejó `identity_config.conectado=true`
        // en Room pero `sesion_negocio` vacía (no se restaura al reiniciar).
        db.barDao().upsertIdentityConfig(
            IdentityConfig(conectado = true, establecimientoUuid = "uuid-1")
        )

        val repo = nuevoRepo()
        // Sin sesión persistida con token → el gate no puede ver «conectado».
        assertFalse(repo.identityConfig.first().conectado)
        assertEquals(null, repo.identityConfig.first().establecimientoUuid)
    }

    @Test
    fun identity_config_conectado_se_mantiene_con_sesion_guardada() = runBlocking {
        // Con «Recuérdame» (sesión con token), `conectado=true` se conserva al recargar.
        db.barDao().upsertSesionNegocio(
            SesionNegocio(
                token = "tok-1",
                email = "negocio@x.es",
                nombreMostrar = "La Terraza Test",
                establecimientoUuid = "uuid-1",
            )
        )
        db.barDao().upsertIdentityConfig(
            IdentityConfig(conectado = true, establecimientoUuid = "uuid-1")
        )
        // Sembrar una sala para que la recarga tome la rama «BD existente» (no el seed).
        db.barDao().insertSalas(listOf(demoSala))

        val repo = nuevoRepo()
        assertTrue(repo.identityConfig.first().conectado)
        assertEquals("uuid-1", repo.identityConfig.first().establecimientoUuid)
    }

    // ═══ Libro de oficio: jornadas + cola de servicios persistidas ═══

    @Test
    fun jornadas_y_cola_de_servicios_persisten_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()

        // Camarero ACTIVA con el nombre de la ronda demo → resuelve la atribución.
        assertTrue(repo1.altaCamarero("cam-1", null, nombre = "Lucía Test"))
        assertTrue(repo1.iniciarSesion("cam-1"))

        // Completar la ronda demo (bebida + comida) → se encola «ronda servida».
        val ticketBebida = repo1.bebidaQueue.first().first { it.rondaId == "r1" }
        assertTrue(repo1.marcarPreparado(ticketBebida.id, "cam-1"))
        assertTrue(repo1.marcarRecogido(ticketBebida.id))
        val ticketComida = repo1.comidaQueue.first().first { it.rondaId == "r1" }
        assertTrue(repo1.marcarPreparado(ticketComida.id, "cam-1"))
        assertTrue(repo1.marcarRecogido(ticketComida.id))
        repo1.awaitPersistencia()

        // Jornada abierta por la sesión + evento encolado.
        assertEquals(1, repo1.jornadas.first().size)
        assertTrue(repo1.jornadas.first().single().fin == null)
        assertEquals("servicio:r1", repo1.serviciosPendientes.first().single().eventoId)

        // Cortar la sesión cierra la jornada; ambas cosas sobreviven a la recarga.
        assertTrue(repo1.cortarSesion("cam-1"))
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        val jornada = repo2.jornadas.first().single()
        assertEquals("cam-1", jornada.camareroId)
        assertTrue(jornada.fin != null)
        val pendiente = repo2.serviciosPendientes.first().single()
        assertEquals("servicio:r1", pendiente.eventoId)
        assertEquals("ronda_servida", pendiente.tipo)
        assertEquals("cam-1", pendiente.camareroId)

        // Eliminar el evento tras «subirlo» a Identity persiste la cola vacía.
        repo2.eliminarServicioPendiente("servicio:r1")
        repo2.awaitPersistencia()
        val repo3 = nuevoRepo()
        assertTrue(repo3.serviciosPendientes.first().isEmpty())
    }

    // ═══ Sync de catálogo: outbox + revisiones persistidas ═══

    @Test
    fun outbox_de_catalogo_y_revisiones_persisten_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()

        // Crear un producto → encola «crear».
        assertTrue(repo1.crearProducto("Café solo", "Bebida", 1.5))
        repo1.awaitPersistencia()
        val creado = repo1.catalogo.first().first { it.nombre == "Café solo" }

        // Revisión canónica tras «aplicar» el crear en el server: a partir de
        // aquí el producto está sincronizado y editar → «actualizar» (si no,
        // editar un producto sin sincronizar re-encola «crear» con el estado nuevo).
        repo1.actualizarRevisionProducto(creado.id, 1)
        repo1.awaitPersistencia()
        assertTrue(repo1.editarProducto(creado.id, "Café con leche", "Bebida", 1.8, true))
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        val ops = repo2.operacionesCatalogo.first()
        assertEquals(2, ops.size)
        assertTrue(ops.any { it.action == "crear" && it.aggregateId == creado.id && it.precioCentimos == 150 })
        assertTrue(ops.any { it.action == "actualizar" && it.aggregateId == creado.id && it.precioCentimos == 180 })
        assertEquals(mapOf(creado.id to 1), repo2.revisionesProducto.first())

        // Eliminar la operación «crear» tras entregarla persiste la cola reducida.
        val crearOp = ops.first { it.action == "crear" }
        repo2.eliminarOperacionCatalogo(crearOp.operationId)
        repo2.awaitPersistencia()
        val repo3 = nuevoRepo()
        assertEquals(1, repo3.operacionesCatalogo.first().size)
        assertEquals("actualizar", repo3.operacionesCatalogo.first().single().action)
    }

    @Test
    fun aplicar_cambios_y_cursor_persisten_tras_recarga() = runBlocking {
        val repo1 = nuevoRepo()
        repo1.aplicarCambiosCatalogo(
            listOf(
                CambioRemoto("nuevo", "crear", ProductoRemoto("nuevo", "Pizza", "Comida", 9.5, true, 1))
            ),
            revisionActual = 1,
        )
        repo1.awaitPersistencia()

        val repo2 = nuevoRepo()
        assertEquals(1, repo2.catalogoSyncDesde.first())
        assertTrue(repo2.catalogo.first().any { it.id == "nuevo" && it.nombre == "Pizza" && it.precio == 9.5 })
        assertEquals(1, repo2.revisionesProducto.first()["nuevo"])
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

    // ═══ Atomicidad: 2xx ⇒ commit durable ═══

    private fun repoVacio() = RoomBarRepository(
        db = db,
        salasIniciales = listOf(demoSala),
        mesasIniciales = listOf(demoMesa),
        catalogoInicial = demoCatalogo,
    )

    private val rondaNueva = Ronda(
        id = "r-nueva", mesaId = "B1", numero = 2, camarero = "Lucía Test",
        lineas = listOf(
            Linea(productoId = "cana", nombreProducto = "Caña Test", cantidad = 1),
        ),
    )

    @Test
    fun crearRonda_persiste_inmediatamente_sin_esperar() = runBlocking {
        val repo = repoVacio()

        assertTrue(repo.crearRonda(rondaNueva))

        // El commit es síncrono: la DB ya tiene la ronda y el ticket (sin awaitPersistencia)
        assertEquals(1, db.barDao().getRondas().size)
        assertEquals(1, db.barDao().getTickets().size)
        assertEquals("r-nueva", db.barDao().getTickets().first().rondaId)
        assertEquals(Destino.BARRA, db.barDao().getTickets().first().destino)
    }

    @Test
    fun crearRonda_sobrevive_a_recarga_inmediata() = runBlocking {
        val repo1 = repoVacio()
        assertTrue(repo1.crearRonda(rondaNueva))

        // Recarga inmediata (sin awaitPersistencia): la comanda se reconstruye
        val repo2 = nuevoRepo()
        assertEquals(listOf(demoRonda, rondaNueva), repo2.rondas.first())
        assertEquals(2, repo2.bebidaQueue.first().size)
        assertEquals(1, repo2.comidaQueue.first().size)
    }

    @Test
    fun marcarPreparado_y_recogido_son_durables_inmediatamente() = runBlocking {
        val repo = nuevoRepo() // ronda demo en colas
        val ticketBebida = repo.bebidaQueue.first().first()

        assertTrue(repo.marcarPreparado(ticketBebida.id, "Lucía Test"))
        assertEquals(TicketEstado.LISTO, db.barDao().getTickets().first { it.id == ticketBebida.id }.estado)

        assertTrue(repo.marcarRecogido(ticketBebida.id))
        assertEquals(TicketEstado.RECOGIDO, db.barDao().getTickets().first { it.id == ticketBebida.id }.estado)

        // Recarga inmediata: el estado sobrevive
        val repo2 = nuevoRepo()
        assertEquals(0, repo2.bebidaQueue.first().size)
        assertEquals(1, repo2.servidos.first().size)
    }

    @Test
    fun commit_fallido_revierte_memoria_y_devuelve_false() {
        val repo = repoVacio()
        db.close() // forzar fallo de commit en el siguiente crearRonda

        assertFalse(repo.crearRonda(rondaNueva))
        // Rollback: la memoria no muestra la comanda (UI y disco coherentes)
        assertEquals(0, repo.rondas.value.size)
        assertEquals(0, repo.bebidaQueue.value.size)
        assertEquals(0, repo.comidaQueue.value.size)
    }

    @Test
    fun crearRonda_duplicada_devuelve_false_sin_tocar_disco() = runBlocking {
        val repo = nuevoRepo()
        val duplicada = demoRonda.copy(id = "dup-1")
        assertTrue(repo.crearRonda(duplicada))

        assertFalse(repo.crearRonda(duplicada))
        // Sin duplicados en disco
        assertEquals(2, db.barDao().getRondas().size) // demo + dup-1 (solo una vez)
        assertEquals(1, db.barDao().getTickets().count { it.rondaId == "dup-1" })
    }
}
