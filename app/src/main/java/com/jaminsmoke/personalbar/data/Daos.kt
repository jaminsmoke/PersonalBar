package com.jaminsmoke.personalbar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * DAOs de Room. Estrategia v0.1: persistencia por dominio con reemplazo completo
 * (`replace*`) — volúmenes pequeños y garantiza que la BD refleja el estado runtime
 * sin diffs por fila. Las tablas singleton usan `@Upsert` (PK fija).
 */
@Dao
interface BarDao {

    // ── Establecimiento (tabla singleton) ─────────────────────────────────────

    @Query("SELECT * FROM establecimientos LIMIT 1")
    suspend fun getEstablecimiento(): Establecimiento?

    @Upsert
    suspend fun upsertEstablecimiento(establecimiento: Establecimiento)

    // ── IdentityConfig (tabla singleton) ─────────────────────────────────────

    @Query("SELECT * FROM identity_config LIMIT 1")
    suspend fun getIdentityConfig(): IdentityConfig?

    @Upsert
    suspend fun upsertIdentityConfig(config: IdentityConfig)

    // ── SesionNegocio (tabla singleton) ──────────────────────────────────────

    @Query("SELECT * FROM sesion_negocio LIMIT 1")
    suspend fun getSesionNegocio(): SesionNegocio?

    @Upsert
    suspend fun upsertSesionNegocio(sesion: SesionNegocio)

    @Query("DELETE FROM sesion_negocio")
    suspend fun clearSesionNegocio()

    // ── Salas ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM salas ORDER BY orden")
    suspend fun getSalas(): List<Sala>

    @Transaction
    suspend fun replaceSalas(salas: List<Sala>) {
        deleteSalas()
        insertSalas(salas)
    }

    @Query("DELETE FROM salas")
    suspend fun deleteSalas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalas(salas: List<Sala>)

    // ── Mesas ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM mesas ORDER BY salaId, indiceZona")
    suspend fun getMesas(): List<Mesa>

    @Transaction
    suspend fun replaceMesas(mesas: List<Mesa>) {
        deleteMesas()
        insertMesas(mesas)
    }

    @Query("DELETE FROM mesas")
    suspend fun deleteMesas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMesas(mesas: List<Mesa>)

    // ── Productos ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM productos ORDER BY categoria, nombre")
    suspend fun getProductos(): List<Producto>

    @Transaction
    suspend fun replaceProductos(productos: List<Producto>) {
        deleteProductos()
        insertProductos(productos)
    }

    @Query("DELETE FROM productos")
    suspend fun deleteProductos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductos(productos: List<Producto>)

    // ── Rondas ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM rondas ORDER BY creadoEn")
    suspend fun getRondas(): List<Ronda>

    @Transaction
    suspend fun replaceRondas(rondas: List<Ronda>) {
        deleteRondas()
        insertRondas(rondas)
    }

    @Query("DELETE FROM rondas")
    suspend fun deleteRondas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRondas(rondas: List<Ronda>)

    // ── Tickets (colas + servidos) ───────────────────────────────────────────

    @Query("SELECT * FROM tickets ORDER BY rondaId")
    suspend fun getTickets(): List<Ticket>

    @Transaction
    suspend fun replaceTickets(tickets: List<Ticket>) {
        deleteTickets()
        insertTickets(tickets)
    }

    @Query("DELETE FROM tickets")
    suspend fun deleteTickets()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<Ticket>)

    // ── Reservas ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM reservas ORDER BY creadaEn")
    suspend fun getReservas(): List<Reserva>

    @Transaction
    suspend fun replaceReservas(reservas: List<Reserva>) {
        deleteReservas()
        insertReservas(reservas)
    }

    @Query("DELETE FROM reservas")
    suspend fun deleteReservas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReservas(reservas: List<Reserva>)

    // ── Camareros (lista blanca) ─────────────────────────────────────────────

    @Query("SELECT * FROM camareros ORDER BY altaEn")
    suspend fun getCamareros(): List<Camarero>

    @Transaction
    suspend fun replaceCamareros(camareros: List<Camarero>) {
        deleteCamareros()
        insertCamareros(camareros)
    }

    @Query("DELETE FROM camareros")
    suspend fun deleteCamareros()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamareros(camareros: List<Camarero>)

    // ── Invitaciones ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM invitaciones ORDER BY creadaEn")
    suspend fun getInvitaciones(): List<Invitacion>

    @Transaction
    suspend fun replaceInvitaciones(invitaciones: List<Invitacion>) {
        deleteInvitaciones()
        insertInvitaciones(invitaciones)
    }

    @Query("DELETE FROM invitaciones")
    suspend fun deleteInvitaciones()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitaciones(invitaciones: List<Invitacion>)

    // ── QrKey (clave pública Ed25519 de Identity, singleton) ──────────────

    @Query("SELECT * FROM qr_keys LIMIT 1")
    suspend fun getQrKey(): QrKey?

    @Upsert
    suspend fun upsertQrKey(key: QrKey)

    // ── Altas pendientes (offline → sync diferido a Identity) ─────────────

    @Query("SELECT * FROM altas_pendientes ORDER BY creadaEn")
    suspend fun getAltasPendientes(): List<AltaPendiente>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAltaPendiente(alta: AltaPendiente)

    @Query("DELETE FROM altas_pendientes WHERE camareroId = :camareroId")
    suspend fun deleteAltaPendiente(camareroId: String)

    // ── Jornadas (libro de oficio, lado Bar) ────────────────────────────────

    @Query("SELECT * FROM jornadas ORDER BY inicio")
    suspend fun getJornadas(): List<JornadaLocal>

    @Transaction
    suspend fun replaceJornadas(jornadas: List<JornadaLocal>) {
        deleteJornadas()
        insertJornadas(jornadas)
    }

    @Query("DELETE FROM jornadas")
    suspend fun deleteJornadas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJornadas(jornadas: List<JornadaLocal>)

    // ── Servicios pendientes (cola persistente del libro de oficio) ────────

    @Query("SELECT * FROM servicios_pendientes ORDER BY creadaEn")
    suspend fun getServiciosPendientes(): List<ServicioPendiente>

    @Transaction
    suspend fun replaceServiciosPendientes(servicios: List<ServicioPendiente>) {
        deleteServiciosPendientes()
        insertServiciosPendientes(servicios)
    }

    @Query("DELETE FROM servicios_pendientes")
    suspend fun deleteServiciosPendientes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiciosPendientes(servicios: List<ServicioPendiente>)

    @Query("DELETE FROM servicios_pendientes WHERE eventoId = :eventoId")
    suspend fun deleteServicioPendiente(eventoId: String)

    // ── Horario del establecimiento (local, tabla singleton por día) ────────

    @Query("SELECT * FROM horario_local ORDER BY diaSemana")
    suspend fun getHorario(): List<HorarioLocal>

    @Transaction
    suspend fun replaceHorario(horario: List<HorarioLocal>) {
        deleteHorario()
        insertHorario(horario)
    }

    @Query("DELETE FROM horario_local")
    suspend fun deleteHorario()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHorario(horario: List<HorarioLocal>)

    // ── Outbox de catálogo (sync carta → Identity) ─────────────────────────

    @Query("SELECT * FROM operaciones_catalogo ORDER BY creadaEn")
    suspend fun getOperacionesCatalogo(): List<OperacionCatalogo>

    @Transaction
    suspend fun replaceOperacionesCatalogo(ops: List<OperacionCatalogo>) {
        deleteOperacionesCatalogo()
        insertOperacionesCatalogo(ops)
    }

    @Query("DELETE FROM operaciones_catalogo")
    suspend fun deleteOperacionesCatalogo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperacionesCatalogo(ops: List<OperacionCatalogo>)

    @Query("DELETE FROM operaciones_catalogo WHERE operationId = :operationId")
    suspend fun deleteOperacionCatalogo(operationId: String)

    // ── Revisiones por producto (mirror del sync) ──────────────────────────

    @Query("SELECT * FROM producto_sync")
    suspend fun getProductosSync(): List<ProductoSync>

    @Transaction
    suspend fun replaceProductosSync(rows: List<ProductoSync>) {
        deleteProductosSync()
        insertProductosSync(rows)
    }

    @Query("DELETE FROM producto_sync")
    suspend fun deleteProductosSync()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductosSync(rows: List<ProductoSync>)

    // ── Cursor del pull de catálogo (singleton) ────────────────────────────

    @Query("SELECT * FROM catalogo_sync_estado LIMIT 1")
    suspend fun getCatalogoSyncEstado(): CatalogoSyncEstado?

    @Upsert
    suspend fun upsertCatalogoSyncEstado(estado: CatalogoSyncEstado)

    // ── Cursor del inbox CFC (singleton) ───────────────────────────────────

    @Query("SELECT * FROM cfc_estado LIMIT 1")
    suspend fun getCfcEstado(): CfcEstado?

    @Upsert
    suspend fun upsertCfcEstado(estado: CfcEstado)

    // ── Grupos de modificadores (carta) ─────────────────────────────────────

    @Query("SELECT * FROM grupos_modificador ORDER BY nombre")
    suspend fun getGruposModificador(): List<GrupoModificador>

    @Transaction
    suspend fun replaceGruposModificador(grupos: List<GrupoModificador>) {
        deleteGruposModificador()
        insertGruposModificador(grupos)
    }

    @Query("DELETE FROM grupos_modificador")
    suspend fun deleteGruposModificador()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGruposModificador(grupos: List<GrupoModificador>)

    // ── Opciones de modificador ────────────────────────────────────────────

    @Query("SELECT * FROM opciones_modificador ORDER BY grupoId, nombre")
    suspend fun getOpcionesModificador(): List<OpcionModificador>

    @Transaction
    suspend fun replaceOpcionesModificador(opciones: List<OpcionModificador>) {
        deleteOpcionesModificador()
        insertOpcionesModificador(opciones)
    }

    @Query("DELETE FROM opciones_modificador")
    suspend fun deleteOpcionesModificador()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpcionesModificador(opciones: List<OpcionModificador>)

    // ── Asignación producto ↔ grupo (N:M) ────────────────────────────────

    @Query("SELECT * FROM producto_grupo")
    suspend fun getProductoGrupo(): List<ProductoGrupo>

    @Transaction
    suspend fun replaceProductoGrupo(asignaciones: List<ProductoGrupo>) {
        deleteProductoGrupo()
        insertProductoGrupo(asignaciones)
    }

    @Query("DELETE FROM producto_grupo")
    suspend fun deleteProductoGrupo()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductoGrupo(asignaciones: List<ProductoGrupo>)

    // ── Zonas (agrupación espacial de sala) ───────────────────────────────

    @Query("SELECT * FROM zonas ORDER BY salaId")
    suspend fun getZonas(): List<Zona>

    @Transaction
    suspend fun replaceZonas(zonas: List<Zona>) {
        deleteZonas()
        insertZonas(zonas)
    }

    @Query("DELETE FROM zonas")
    suspend fun deleteZonas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZonas(zonas: List<Zona>)
}
