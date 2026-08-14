package com.jaminsmoke.personalbar.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos del nodo (fuente de verdad durable). v2 añade `tickets.numeroCola`
 * (id de cola visible/hablable por destino). Schema exportado a `app/schemas/`
 * para versionar migraciones futuras igual que Commander.
 */
@Database(
    entities = [
        Establecimiento::class,
        Sala::class,
        Mesa::class,
        Producto::class,
        Ticket::class,
        Ronda::class,
        Reserva::class,
        Invitacion::class,
        Camarero::class,
        IdentityConfig::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barDao(): BarDao

    companion object {
        /**
         * v1→v2: `tickets.numeroCola` (id de cola por destino). Los tickets
         * existentes quedan en 0; el backfill por destino lo hace
         * [RoomBarRepository] al cargar (no es SQL por la lógica de secuencia).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tickets ADD COLUMN numeroCola INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
