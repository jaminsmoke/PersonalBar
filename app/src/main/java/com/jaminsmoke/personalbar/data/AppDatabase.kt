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
 * (sesión de la cuenta de negocio con «Recuérdame»); v5 sustituye `logoClave`
 * por `logoUrl` (logo sincronizado contra Identity); v7 añade
 * `sesion_negocio.dataOrigin` (procedencia canónica de Identity); v8 añade
 * `camareros.sesionActiva` (sesión de trabajo concedida por Bar). Schema exportado
 * a `app/schemas/` para versionar migraciones futuras igual que Commander.
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
        QrKey::class,
        AltaPendiente::class,
    ],
    version = 8,
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

        /**
         * v4→v5: `sesion_negocio.logoClave` (placeholder local) se sustituye por
         * `logoUrl` (URL del logo en Identity). Se recrea la tabla singleton
         * conservando token/email/nombre/uuid/tipo; `logoUrl` arranca NULL.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sesion_negocio_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "token TEXT, " +
                        "email TEXT, " +
                        "nombreMostrar TEXT, " +
                        "establecimientoUuid TEXT, " +
                        "tipo TEXT, " +
                        "logoUrl TEXT)"
                )
                db.execSQL(
                    "INSERT INTO sesion_negocio_new (id, token, email, nombreMostrar, establecimientoUuid, tipo, logoUrl) " +
                        "SELECT id, token, email, nombreMostrar, establecimientoUuid, tipo, NULL FROM sesion_negocio"
                )
                db.execSQL("DROP TABLE sesion_negocio")
                db.execSQL("ALTER TABLE sesion_negocio_new RENAME TO sesion_negocio")
            }
        }

        /**
         * v5→v6: `qr_keys` (clave pública Ed25519 de Identity para verificar
         * QRs offline) y `altas_pendientes` (altas offline con sync diferido).
         * Tablas nuevas; sin datos previos que migrar.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS qr_keys (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "keyId TEXT NOT NULL, " +
                        "publicKey TEXT NOT NULL, " +
                        "algorithm TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS altas_pendientes (" +
                        "camareroId TEXT NOT NULL PRIMARY KEY, " +
                        "payload TEXT NOT NULL, " +
                        "creadaEn INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v6→v7: `sesion_negocio.dataOrigin` (procedencia canónica de Identity,
         * `real|test|demo`). Columna nullable; las sesiones existentes quedan NULL
         * (equivalen a `real`).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sesion_negocio ADD COLUMN dataOrigin TEXT")
            }
        }

        /**
         * v7→v8: `camareros.sesionActiva` (sesión de trabajo concedida por Bar).
         * Por defecto nadie tiene sesión activa hasta que Commander pida iniciarla
         * y Bar la acepte.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE camareros ADD COLUMN sesionActiva INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
