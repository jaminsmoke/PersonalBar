package com.jaminsmoke.personalbar.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test de migración Room v1→v2: `tickets.numeroCola` (id de cola por destino).
 *
 * Crea la BD en v1 (schema exportado 1.json), inserta tickets sin `numeroCola`,
 * migra a v2 y valida que el esquema coincide (MigrationTestHelper) y que la
 * columna existe con default 0 (el backfill por destino lo hace RoomBarRepository).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migracion_v2_a_v3_anade_deServicio() {
        // 1. BD en v2 con datos
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO camareros (id, nombre, email, rol, estado, credencialId, altaEn) " +
                    "VALUES ('c-1', 'Ana', 'ana@x.es', 'STAFF', 'ACTIVA', NULL, 0)"
            )
        }

        // 2. Migrar y validar contra 3.json
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)
        db.use {
            val cursor = it.query("SELECT deServicio FROM camareros WHERE id = 'c-1'")
            cursor.use { c ->
                assertNotNull("La columna deServicio existe tras migrar", c)
                c.moveToFirst()
                assertEquals("Default: nadie de servicio hasta marcarlo en la barra", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v3_a_v4_crea_sesion_negocio() {
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)
        db.use {
            it.execSQL(
                "INSERT INTO sesion_negocio (id, token, email, nombreMostrar, establecimientoUuid, tipo, logoClave) " +
                    "VALUES ('local', 'tok-1', 'negocio@x.es', 'La Terraza', 'e-1', 'BAR', NULL)"
            )
            val cursor = it.query("SELECT nombreMostrar FROM sesion_negocio WHERE id = 'local'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("La Terraza", c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v4_a_v5_renombra_logoClave_a_logoUrl() {
        // 1. BD en v4 con una sesión (logoClave era placeholder local)
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO sesion_negocio (id, token, email, nombreMostrar, establecimientoUuid, tipo, logoClave) " +
                    "VALUES ('local', 'tok-1', 'negocio@x.es', 'La Terraza', 'e-1', 'BAR', 'placeholder')"
            )
        }

        // 2. Migrar a v5: se conservan los datos y logoUrl arranca NULL
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)
        db.use {
            val cursor = it.query(
                "SELECT token, email, nombreMostrar, establecimientoUuid, tipo, logoUrl FROM sesion_negocio WHERE id = 'local'"
            )
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("tok-1", c.getString(0))
                assertEquals("negocio@x.es", c.getString(1))
                assertEquals("La Terraza", c.getString(2))
                assertEquals("e-1", c.getString(3))
                assertEquals("BAR", c.getString(4))
                assertEquals(null, c.getString(5))
            }
        }
    }

    @Test
    fun migracion_v5_a_v6_crea_qr_keys_y_altas_pendientes() {
        helper.createDatabase(TEST_DB, 5).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)
        db.use {
            it.execSQL(
                "INSERT INTO qr_keys (id, keyId, publicKey, algorithm) VALUES ('local', 'ed25519-v1', 'abc', 'Ed25519')"
            )
            it.execSQL(
                "INSERT INTO altas_pendientes (camareroId, payload, creadaEn) VALUES ('c-1', 'phid1:...', 0)"
            )
            val cursor = it.query("SELECT publicKey FROM qr_keys WHERE id = 'local'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("abc", c.getString(0))
            }
            val cursor2 = it.query("SELECT payload FROM altas_pendientes WHERE camareroId = 'c-1'")
            cursor2.use { c ->
                c.moveToFirst()
                assertEquals("phid1:...", c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v6_a_v7_anade_dataOrigin() {
        // 1. BD en v6 con una sesión de negocio
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO sesion_negocio (id, token, email, nombreMostrar, establecimientoUuid, tipo, logoUrl) " +
                    "VALUES ('local', 'tok-1', 'negocio@x.es', 'La Terraza', 'e-1', 'BAR', NULL)"
            )
        }

        // 2. Migrar a v7: la sesión se conserva y dataOrigin arranca NULL (real)
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)
        db.use {
            val cursor = it.query(
                "SELECT nombreMostrar, dataOrigin FROM sesion_negocio WHERE id = 'local'"
            )
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("La Terraza", c.getString(0))
                assertEquals(null, c.getString(1))
            }
        }
    }

    @Test
    fun migracion_v7_a_v8_anade_sesionActiva() {
        // 1. BD en v7 con un camarero (sin sesionActiva)
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO camareros (id, nombre, email, rol, estado, credencialId, altaEn, deServicio) " +
                    "VALUES ('c-1', 'Ana', 'ana@x.es', 'STAFF', 'ACTIVA', NULL, 0, 0)"
            )
        }

        // 2. Migrar a v8: nadie con sesión activa por defecto
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8)
        db.use {
            val cursor = it.query("SELECT sesionActiva FROM camareros WHERE id = 'c-1'")
            cursor.use { c ->
                assertNotNull("La columna sesionActiva existe tras migrar", c)
                c.moveToFirst()
                assertEquals("Default: sin sesión activa hasta que Bar la conceda", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v8_a_v9_crea_jornadas_y_servicios_pendientes() {
        helper.createDatabase(TEST_DB, 8).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9)
        db.use {
            it.execSQL(
                "INSERT INTO jornadas (id, camareroId, inicio, fin) VALUES ('j-1', 'c-1', 1000, NULL)"
            )
            it.execSQL(
                "INSERT INTO servicios_pendientes (eventoId, camareroId, tipo, cantidad, creadaEn) " +
                    "VALUES ('servicio:r1', 'c-1', 'ronda_servida', 1, 0)"
            )
            val cursor = it.query("SELECT camareroId FROM jornadas WHERE id = 'j-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("c-1", c.getString(0))
            }
            val cursor2 = it.query("SELECT tipo FROM servicios_pendientes WHERE eventoId = 'servicio:r1'")
            cursor2.use { c ->
                c.moveToFirst()
                assertEquals("ronda_servida", c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v1_a_v2_anade_numeroCola() {
        // 1. BD en v1 con datos
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO tickets (id, rondaId, destino, estado, preparadoPor, lineas) " +
                    "VALUES ('t-1', 'r-1', 'BARRA', 'PENDIENTE', NULL, '[]')"
            )
            db.execSQL(
                "INSERT INTO tickets (id, rondaId, destino, estado, preparadoPor, lineas) " +
                    "VALUES ('t-2', 'r-2', 'COCINA', 'PREPARADO', 'Ana', '[]')"
            )
        }

        // 2. Migrar y validar el esquema contra 2.json
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)
        db.use {
            val cursor = it.query("SELECT numeroCola FROM tickets WHERE id = 't-1'")
            cursor.use { c ->
                assertNotNull("La columna numeroCola existe tras migrar", c)
                c.moveToFirst()
                assertEquals("Default 0 para tickets v1 (backfill en repo)", 0, c.getInt(0))
            }
            val cursor2 = it.query("SELECT numeroCola FROM tickets WHERE id = 't-2'")
            cursor2.use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v9_a_v10_anade_validaHasta() {
        // 1. BD en v9 con una sesión de negocio (sin validaHasta)
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                "INSERT INTO sesion_negocio (id, token, email, nombreMostrar, establecimientoUuid, tipo, logoUrl, dataOrigin) " +
                    "VALUES ('local', 'tok-1', 'negocio@x.es', 'La Terraza', 'e-1', 'BAR', NULL, 'real')"
            )
        }

        // 2. Migrar a v10: la sesión se conserva y validaHasta arranca NULL
        //    (sin validez offline hasta el primer contacto con el VPS)
        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)
        db.use {
            val cursor = it.query(
                "SELECT token, email, nombreMostrar, establecimientoUuid, validaHasta FROM sesion_negocio WHERE id = 'local'"
            )
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("tok-1", c.getString(0))
                assertEquals("negocio@x.es", c.getString(1))
                assertEquals("La Terraza", c.getString(2))
                assertEquals("e-1", c.getString(3))
                assertEquals(null, c.getString(4))
            }
        }
    }

    @Test
    fun migracion_v10_a_v11_crea_horario_local() {
        // 1. BD en v10 (con la sesión de negocio que ya existe, sin horario)
        helper.createDatabase(TEST_DB, 10).close()

        // 2. Migrar a v11: la tabla horario_local existe y arranca vacía
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)
        db.use {
            it.execSQL(
                "INSERT INTO horario_local (diaSemana, abre, cierra) VALUES (1, '10:00', '22:00')"
            )
            val cursor = it.query("SELECT abre, cierra FROM horario_local WHERE diaSemana = 1")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("10:00", c.getString(0))
                assertEquals("22:00", c.getString(1))
            }
        }
    }

    @Test
    fun migracion_v11_a_v12_reasigna_ids_a_uuid() {
        // 1. BD en v11 con productos con id slug
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO productos (id, nombre, categoria, precio, disponible) " +
                    "VALUES ('cana', 'Caña', 'Bebida', 2.0, 1)"
            )
            db.execSQL(
                "INSERT INTO productos (id, nombre, categoria, precio, disponible) " +
                    "VALUES ('croquetas', 'Croquetas', 'Comida', 6.0, 1)"
            )
        }

        // 2. Migrar a v12: los ids pasan a UUID (36 chars), el resto se conserva
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12)
        db.use {
            val cursor = it.query("SELECT id, nombre FROM productos ORDER BY nombre")
            cursor.use { c ->
                c.moveToFirst()
                val idCana = c.getString(0)
                assertEquals("Caña", c.getString(1))
                assertEquals(36, idCana.length)
                c.moveToNext()
                val idCroquetas = c.getString(0)
                assertEquals("Croquetas", c.getString(1))
                assertEquals(36, idCroquetas.length)
                assertTrue(idCana != idCroquetas)
            }
        }
    }

    @Test
    fun migracion_v12_a_v13_crea_outbox_y_revisiones() {
        helper.createDatabase(TEST_DB, 12).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)
        db.use {
            it.execSQL(
                "INSERT INTO operaciones_catalogo (operationId, aggregateId, action, baseRevision, nombre, categoria, destino, precioCentimos, moneda, disponible, creadaEn) " +
                    "VALUES ('op-1', 'p-1', 'crear', 0, 'Caña', 'Bebida', 'barra', 250, 'EUR', 1, 0)"
            )
            it.execSQL(
                "INSERT INTO producto_sync (aggregateId, revision, actualizadaEn) VALUES ('p-1', 1, 0)"
            )
            val cursor = it.query("SELECT aggregateId FROM operaciones_catalogo WHERE operationId = 'op-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("p-1", c.getString(0))
            }
            val cursor2 = it.query("SELECT revision FROM producto_sync WHERE aggregateId = 'p-1'")
            cursor2.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v13_a_v14_crea_catalogo_sync_estado() {
        helper.createDatabase(TEST_DB, 13).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14)
        db.use {
            it.execSQL("INSERT INTO catalogo_sync_estado (id, desdeRevision) VALUES ('local', 3)")
            val cursor = it.query("SELECT desdeRevision FROM catalogo_sync_estado WHERE id = 'local'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v14_a_v15_crea_modificadores() {
        // 1. BD en v14 con un producto (sin subfamilia/permiteNota)
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO productos (id, nombre, categoria, precio, disponible) " +
                    "VALUES ('p-1', 'Caña', 'Bebida', 2.0, 1)"
            )
        }

        // 2. Migrar a v15: columnas nuevas + tablas de modificadores vacías
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15)
        db.use {
            val cursor = it.query("SELECT nombre, subfamilia, permiteNota FROM productos WHERE id = 'p-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("Caña", c.getString(0))
                assertEquals("subfamilia arranca null", null, c.getString(1))
                assertEquals("permiteNota default 0", 0, c.getInt(2))
            }

            // Tablas nuevas operativas
            it.execSQL(
                "INSERT INTO grupos_modificador (id, nombre, multiple, obligatorio) " +
                    "VALUES ('g-1', 'Punto', 0, 1)"
            )
            it.execSQL(
                "INSERT INTO opciones_modificador (id, grupoId, nombre, deltaPrecio, alias) " +
                    "VALUES ('o-1', 'g-1', 'Al punto', 0.0, 'al punto')"
            )
            it.execSQL("INSERT INTO producto_grupo (productoId, grupoId) VALUES ('p-1', 'g-1')")

            val grupo = it.query("SELECT obligatorio FROM grupos_modificador WHERE id = 'g-1'")
            grupo.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            val opcion = it.query("SELECT alias FROM opciones_modificador WHERE id = 'o-1'")
            opcion.use { c ->
                c.moveToFirst()
                assertEquals("al punto", c.getString(0))
            }
            val asignacion = it.query("SELECT productoId, grupoId FROM producto_grupo")
            asignacion.use { c ->
                c.moveToFirst()
                assertEquals("p-1", c.getString(0))
                assertEquals("g-1", c.getString(1))
            }
        }
    }

    @Test
    fun migracion_v15_a_v16_anade_descripcion() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                "INSERT INTO productos (id, nombre, categoria, precio, disponible, permiteNota) " +
                    "VALUES ('p-1', 'Caña', 'Bebida', 2.0, 1, 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, AppDatabase.MIGRATION_15_16)
        db.use {
            val cursor = it.query("SELECT nombre, descripcion FROM productos WHERE id = 'p-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("Caña", c.getString(0))
                assertEquals("descripcion arranca null", null, c.getString(1))
            }
            it.execSQL(
                "UPDATE productos SET descripcion = 'De barril' WHERE id = 'p-1'"
            )
            val after = it.query("SELECT descripcion FROM productos WHERE id = 'p-1'")
            after.use { c ->
                c.moveToFirst()
                assertEquals("De barril", c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v16_a_v17_crea_zonas() {
        // 1. BD en v16 con una sala (sin zonas)
        helper.createDatabase(TEST_DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO salas (id, nombre, orden) VALUES ('sala-1', 'Terraza', 1)"
            )
        }

        // 2. Migrar a v17: la tabla zonas existe y arranca vacía
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, AppDatabase.MIGRATION_16_17)
        db.use {
            it.execSQL(
                "INSERT INTO zonas (id, salaId, nombre, posX, posY, ancho, alto, color, camareroId) " +
                    "VALUES ('zona-1', 'sala-1', 'Barra alta', 40.0, 40.0, 240.0, 160.0, 'AMARILLO', NULL)"
            )
            val cursor = it.query("SELECT nombre, color, camareroId FROM zonas WHERE id = 'zona-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("Barra alta", c.getString(0))
                assertEquals("AMARILLO", c.getString(1))
                assertEquals("camareroId arranca null", null, c.getString(2))
            }
        }
    }

    @Test
    fun migracion_v17_a_v18_anade_mesaUuid_notNull() {
        // 1. BD en v17 con una mesa (sin mesaUuid)
        helper.createDatabase(TEST_DB, 17).use { db ->
            db.execSQL(
                "INSERT INTO salas (id, nombre, orden) VALUES ('sala-1', 'Barra', 1)"
            )
            db.execSQL(
                "INSERT INTO mesas (id, salaId, indiceZona, numero, forma, capacidad, posX, posY, girada, bloqueada) " +
                    "VALUES ('mesa-1', 'sala-1', 1, 1, 'CUADRADA', 4, 120.0, 120.0, 0, 0)"
            )
        }

        // 2. Migrar a v18 y validar contra 18.json: mesaUuid TEXT NOT NULL DEFAULT ''
        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, AppDatabase.MIGRATION_17_18)
        db.use {
            val cursor = it.query("SELECT mesaUuid FROM mesas WHERE id = 'mesa-1'")
            cursor.use { c ->
                assertNotNull("La columna mesaUuid existe tras migrar", c)
                c.moveToFirst()
                assertEquals("mesaUuid arranca vacío (el backfill lo rellena)", "", c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v18_a_v19_anade_cfc_estado() {
        // 1. BD en v18 con una mesa (mesaUuid ya presente)
        helper.createDatabase(TEST_DB, 18).use { db ->
            db.execSQL(
                "INSERT INTO salas (id, nombre, orden) VALUES ('sala-1', 'Barra', 1)"
            )
            db.execSQL(
                "INSERT INTO mesas (id, mesaUuid, salaId, indiceZona, numero, forma, capacidad, posX, posY, girada, bloqueada) " +
                    "VALUES ('mesa-1', 'uuid-1', 'sala-1', 1, 1, 'CUADRADA', 4, 120.0, 120.0, 0, 0)"
            )
        }

        // 2. Migrar a v19 y validar contra 19.json: tabla cfc_estado creada
        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, AppDatabase.MIGRATION_18_19)
        db.use {
            // La tabla nueva arranca vacía; la fila la crea el poller en el primer pull
            val cursor = it.query("SELECT COUNT(*) FROM cfc_estado")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("cfc_estado arranca vacía (aditiva)", 0, c.getInt(0))
            }
        }
    }

    @Test
    fun migracion_v19_a_v20_anade_mesa_camareroId() {
        // 1. BD en v19 con una mesa (sin camareroId)
        helper.createDatabase(TEST_DB, 19).use { db ->
            db.execSQL(
                "INSERT INTO salas (id, nombre, orden) VALUES ('sala-1', 'Barra', 1)"
            )
            db.execSQL(
                "INSERT INTO mesas (id, mesaUuid, salaId, indiceZona, numero, forma, capacidad, posX, posY, girada, bloqueada) " +
                    "VALUES ('mesa-1', 'uuid-1', 'sala-1', 1, 1, 'CUADRADA', 4, 120.0, 120.0, 0, 0)"
            )
        }

        // 2. Migrar a v20 y validar contra 20.json: camareroId TEXT nullable
        val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, AppDatabase.MIGRATION_19_20)
        db.use {
            val cursor = it.query("SELECT camareroId FROM mesas WHERE id = 'mesa-1'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("camareroId arranca null (sin asignación)", null, c.getString(0))
            }
        }
    }

    @Test
    fun migracion_v20_a_v21_anade_tokenCifrado() {
        // 1. BD en v20 con una sesión (token en claro, sin tokenCifrado)
        helper.createDatabase(TEST_DB, 20).use { db ->
            db.execSQL(
                "INSERT INTO sesion_negocio (id, token, email, nombreMostrar, establecimientoUuid) " +
                    "VALUES ('local', 'jwt-en-claro', 'negocio@Test', 'Bar Test', 'uuid-1')"
            )
        }

        // 2. Migrar a v21 y validar contra 21.json: tokenCifrado TEXT nullable
        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true, AppDatabase.MIGRATION_20_21)
        db.use {
            val cursor = it.query("SELECT token, tokenCifrado FROM sesion_negocio WHERE id = 'local'")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("El token pre-v21 se conserva hasta el backfill", "jwt-en-claro", c.getString(0))
                assertEquals("tokenCifrado arranca null (backfill en restaurarSesion)", null, c.getString(1))
            }
        }
    }

    @Test
    fun migracion_v21_a_v22_crea_nodo_secreto() {
        // 1. BD en v21 (sin nodo_secreto)
        helper.createDatabase(TEST_DB, 21).use { }

        // 2. Migrar a v22 y validar contra 22.json: tabla nueva nodo_secreto vacía
        val db = helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22)
        db.use {
            val cursor = it.query("SELECT COUNT(*) FROM nodo_secreto")
            cursor.use { c ->
                c.moveToFirst()
                assertEquals("nodo_secreto arranca vacía (el secreto se genera al arrancar)", 0, c.getInt(0))
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
