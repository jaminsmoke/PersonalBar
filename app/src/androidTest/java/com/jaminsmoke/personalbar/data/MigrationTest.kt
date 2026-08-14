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
