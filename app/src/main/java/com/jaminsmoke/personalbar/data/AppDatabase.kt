package com.jaminsmoke.personalbar.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos del nodo (fuente de verdad durable). v2 añade `tickets.numeroCola`
 * (id de cola visible/hablable por destino); v3 añade `camareros.deServicio`
 * (varios preparadores activos en el puesto); v4 añade `sesion_negocio`
 * (sesión de la cuenta de negocio con «Recuérdame»). Schema exportado a
 * `app/schemas/` para versionar migraciones futuras igual que Commander.
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
        SesionNegocio::class,
    ],
    version = 4,
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

        /**
         * v2→v3: `camareros.deServicio` (flag del turno del puesto; por defecto
         * nadie está de servicio hasta que se marque en la barra «Quién soy»).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE camareros ADD COLUMN deServicio INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3→v4: `sesion_negocio` (cuenta de negocio logueada en el puesto).
         * Tabla nueva; sin datos previos que migrar.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sesion_negocio (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "token TEXT, " +
                        "email TEXT, " +
                        "nombreMostrar TEXT, " +
                        "establecimientoUuid TEXT, " +
                        "tipo TEXT, " +
                        "logoClave TEXT)"
                )
            }
        }
    }
}
