package com.jaminsmoke.personalbar.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
