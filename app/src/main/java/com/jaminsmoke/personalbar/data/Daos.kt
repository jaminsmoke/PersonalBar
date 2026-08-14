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
}
