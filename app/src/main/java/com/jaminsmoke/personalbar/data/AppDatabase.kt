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
 * (apertura/cierre del establecimiento por día, fuente local del puesto); v12
 * reasigna `productos.id` de slug a UUID (migración de datos; el esquema no cambia);
 * v13 añade `operaciones_catalogo` (outbox del sync de carta) y `producto_sync`
 * (revisión canónica por producto); v14 añade `catalogo_sync_estado`
 * (cursor global del pull de deltas `GET /sync/cambios`); v15 añade modificadores
 * y subfamilias de carta; v16 añade `descripcion` (copy público del plato) en
 * `productos` y `operaciones_catalogo`; v17 añade `zonas` (agrupación espacial
 * de sala: rectángulo + color de paleta + camarero asignado opcional); v18 añade
 * `mesas.mesaUuid` (identidad canónica e inmutable de familia para QR/CFC,
 * sincronización con Identity y correlación con Commander); v19 añade
 * `cfc_estado` (cursor persistido del pull del inbox CFC: reanuda donde se
 * quedó tras reiniciar, sin re-traer pedidos ya procesados); v20 añade
 * `mesas.camareroId` (asignación directa de camarero responsable, precede a
 * la zona en el reparto de pedidos CFC); v21 añade
 * `sesion_negocio.tokenCifrado` (bearer cifrado AES-GCM con clave Android
 * Keystore; el token en claro nunca se persiste).
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
        OperacionCatalogo::class,
        ProductoSync::class,
        CatalogoSyncEstado::class,
        GrupoModificador::class,
        OpcionModificador::class,
        ProductoGrupo::class,
        Zona::class,
        CfcEstado::class,
        NodoSecreto::class,
    ],
    version = 22,
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

        /**
         * v11→v12: reasigna `productos.id` de slug a un UUID (formato 8-4-4-4-12).
         * Migración de datos sin cambio de esquema: `randomblob()` se evalúa por fila,
         * así cada producto recibe un id único estable. Los tickets/rondas históricos
         * conservan el slug antiguo en `lineas.productoId` (dead data tras el split).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE productos SET id = " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(6)))"
                )
            }
        }

        /**
         * v12→v13: outbox del sync de carta (`operaciones_catalogo`) y revisión
         * canónica por producto (`producto_sync`). Tablas nuevas, arrancan vacías.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS operaciones_catalogo (" +
                        "operationId TEXT NOT NULL PRIMARY KEY, " +
                        "aggregateId TEXT NOT NULL, " +
                        "action TEXT NOT NULL, " +
                        "baseRevision INTEGER NOT NULL, " +
                        "nombre TEXT, " +
                        "categoria TEXT, " +
                        "destino TEXT, " +
                        "precioCentimos INTEGER, " +
                        "moneda TEXT NOT NULL, " +
                        "disponible INTEGER NOT NULL, " +
                        "creadaEn INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS producto_sync (" +
                        "aggregateId TEXT NOT NULL PRIMARY KEY, " +
                        "revision INTEGER NOT NULL, " +
                        "actualizadaEn INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v13→v14: `catalogo_sync_estado` (cursor global del pull de deltas,
         * `GET /sync/cambios?desde=N`). Tabla nueva singleton, arranca vacía.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalogo_sync_estado (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "desdeRevision INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v14→v15: modificadores y subfamilias de carta. `productos` gana
         * `subfamilia` (nullable) y `permiteNota` (0 por defecto); tablas nuevas
         * para grupos de modificadores, sus opciones y la asignación N:M a SKUs.
         * Aditiva: sin datos previos que migrar (los grupos arrancan vacíos).
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos ADD COLUMN subfamilia TEXT")
                db.execSQL("ALTER TABLE productos ADD COLUMN permiteNota INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS grupos_modificador (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "nombre TEXT NOT NULL, " +
                        "multiple INTEGER NOT NULL, " +
                        "obligatorio INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS opciones_modificador (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "grupoId TEXT NOT NULL, " +
                        "nombre TEXT NOT NULL, " +
                        "deltaPrecio REAL NOT NULL, " +
                        "alias TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS producto_grupo (" +
                        "productoId TEXT NOT NULL, " +
                        "grupoId TEXT NOT NULL, " +
                        "PRIMARY KEY (productoId, grupoId))"
                )
            }
        }

        /**
         * v15→v16: copy público del plato (`descripcion`) en catálogo y outbox.
         * Nullable: productos y operaciones previas quedan sin texto.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos ADD COLUMN descripcion TEXT")
                db.execSQL("ALTER TABLE operaciones_catalogo ADD COLUMN descripcion TEXT")
            }
        }

        /**
         * v16→v17: `zonas` (agrupación espacial de sala). Tabla nueva, arranca
         * vacía; sin datos previos que migrar (aditiva).
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS zonas (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "salaId TEXT NOT NULL, " +
                        "nombre TEXT NOT NULL, " +
                        "posX REAL NOT NULL, " +
                        "posY REAL NOT NULL, " +
                        "ancho REAL NOT NULL, " +
                        "alto REAL NOT NULL, " +
                        "color TEXT NOT NULL, " +
                        "camareroId TEXT)"
                )
            }
        }

        /**
         * v17→v18: `mesas.mesaUuid` (identidad canónica de familia para QR CFC,
         * sincronización con Identity y correlación con Commander). Columna nueva
         * nullable: las mesas existentes quedan NULL hasta que
         * [RoomBarRepository.cargar] haga el backfill una vez (genera un UUID v4
         * por mesa sin `mesaUuid` y lo persiste). El `mesaUuid` nunca se reutiliza:
         * borrar una mesa descarta su UUID y crear una mesa nueva genera otro.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // `mesaUuid` es `String = ""` en la entidad → Room espera TEXT NOT NULL
                // (sin DEFAULT SQL). ADD COLUMN con NOT NULL exige DEFAULT no nulo en
                // SQLite; el backfill de [RoomBarRepository] rellena el UUID real.
                db.execSQL("ALTER TABLE mesas ADD COLUMN mesaUuid TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v18→v19: `cfc_estado` (cursor del inbox CFC). Tabla nueva de una fila;
         * arranca vacía y el poller la crea en el primer pull (aditiva).
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cfc_estado (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "cursor INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v19→v20: `mesas.camareroId` (asignación directa de camarero responsable
         * en el reparto CFC). Nullable: las mesas existentes quedan sin asignación
         * (el resolver cae a zona / menor carga / Sin asignar).
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mesas ADD COLUMN camareroId TEXT")
            }
        }

        /**
         * v20→v21: `sesion_negocio.tokenCifrado` (bearer cifrado con Keystore).
         * Nullable: las sesiones pre-v21 conservan `token` en claro hasta que
         * [PersonalBarApp.restaurarSesion] hace el backfill (cifra y borra el claro).
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sesion_negocio ADD COLUMN tokenCifrado TEXT")
            }
        }

        /**
         * v21→v22: `nodo_secreto` — secreto HMAC del nodo LAN (auth de sesión v0.2),
         * persistido cifrado con AndroidKeyStore para que los tokens firmados
         * sobrevivan reinicios. Tabla nueva, aditiva.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nodo_secreto` " +
                        "(`id` TEXT NOT NULL, `secretoCifrado` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
