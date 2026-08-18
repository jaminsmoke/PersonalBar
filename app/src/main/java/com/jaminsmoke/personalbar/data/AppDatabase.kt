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
 * `camareros.sesionActiva` (sesión de trabajo concedida por Bar); v9 añade
 * `jornadas` (intervalos locales del libro de oficio) y `servicios_pendientes`
 * (cola persistente de eventos por emitir a Identity); v10 añade
 * `sesion_negocio.validaHasta` (validez local de la sesión para login offline,
 * renovada +7 días en cada contacto con el VPS); v11 añade `horario_local`
 * (apertura/cierre del establecimiento por día, fuente local del puesto).
 * Schema exportado a `app/schemas/` para versionar migraciones futuras igual
 * que Commander.
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
        JornadaLocal::class,
        ServicioPendiente::class,
        HorarioLocal::class,
    ],
    version = 11,
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

        /**
         * v8→v9: `jornadas` (intervalos del libro de oficio) y `servicios_pendientes`
         * (cola persistente de eventos por emitir a Identity). Tablas nuevas;
         * sin datos previos que migrar.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS jornadas (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "camareroId TEXT NOT NULL, " +
                        "inicio INTEGER NOT NULL, " +
                        "fin INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS servicios_pendientes (" +
                        "eventoId TEXT NOT NULL PRIMARY KEY, " +
                        "camareroId TEXT NOT NULL, " +
                        "tipo TEXT NOT NULL, " +
                        "cantidad INTEGER NOT NULL, " +
                        "creadaEn INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v9→v10: `sesion_negocio.validaHasta` (validez local de la sesión para
         * login offline, epoch ms). Nullable: las sesiones existentes quedan NULL
         * (sin validez offline) hasta el siguiente contacto exitoso con el VPS
         * (login o revalidación), que la renueva a +7 días.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sesion_negocio ADD COLUMN validaHasta INTEGER")
            }
        }

        /**
         * v10→v11: `horario_local` (apertura/cierre por día de la semana del
         * establecimiento). Tabla nueva con PK por día (1 = lunes … 7 = domingo);
         * sin datos previos que migrar (arranca vacía = sin horario configurado).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS horario_local (" +
                        "diaSemana INTEGER NOT NULL PRIMARY KEY, " +
                        "abre TEXT, " +
                        "cierra TEXT)"
                )
            }
        }
    }
}
