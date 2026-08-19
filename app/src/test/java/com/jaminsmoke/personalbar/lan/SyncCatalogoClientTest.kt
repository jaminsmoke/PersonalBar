package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.destinoSyncDesdeCategoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCatalogoClientTest {

    @Test
    fun destinoSyncDesdeCategoriaMapeaBebidaComidaYDefecto() {
        assertEquals("barra", destinoSyncDesdeCategoria("Bebida"))
        assertEquals("barra", destinoSyncDesdeCategoria("Cafés"))
        assertEquals("cocina", destinoSyncDesdeCategoria("Comida"))
        assertEquals("cocina", destinoSyncDesdeCategoria("Pizza"))
        // categoría desconocida → defecto seguro en barra
        assertEquals("barra", destinoSyncDesdeCategoria("Varios"))
    }

    @Test
    fun epochUtcIsoIncluyeOffsetExplicito() {
        val ts = epochUtcIso(0L)
        assertTrue(
            "timestamp sin offset explícito: $ts",
            ts.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00"))
        )
        assertTrue(ts.endsWith("+00:00"))
    }

    @Test
    fun operacionSyncRequestSerializaCamposSnakeCase() {
        val req = OperacionSyncRequest(
            operationId = "op-1",
            deviceId = "bar-tablet-01",
            aggregateId = "agg-1",
            action = "crear",
            baseRevision = 0,
            payload = ProductoPayload(
                nombre = "Caña",
                categoria = "Bebida",
                destino = "barra",
                precioCentimos = 250,
                moneda = "EUR",
                disponible = true,
            ),
            clientCreatedAt = "2026-08-19T12:00:00.000+00:00",
        )
        val json = LanJson.encodeToString(req)
        assertTrue(json.contains("\"operation_id\":\"op-1\""))
        assertTrue(json.contains("\"device_id\":\"bar-tablet-01\""))
        assertTrue(json.contains("\"aggregate_type\":\"producto\""))
        assertTrue(json.contains("\"aggregate_id\":\"agg-1\""))
        assertTrue(json.contains("\"action\":\"crear\""))
        assertTrue(json.contains("\"base_revision\":0"))
        assertTrue(json.contains("\"precio_centimos\":250"))
        assertTrue(json.contains("\"client_created_at\":\"2026-08-19T12:00:00.000+00:00\""))
    }

    @Test
    fun productoSnapshotToRemotoConvierteCentimosAEuros() {
        val snap = ProductoSnapshot(
            id = "p1", nombre = "Caña", categoria = "Bebida", destino = "barra",
            precioCentimos = 250, moneda = "EUR", disponible = true, revision = 1,
        )
        val remoto = snap.toRemoto()
        assertEquals("p1", remoto.id)
        assertEquals("Caña", remoto.nombre)
        assertEquals(2.5, remoto.precio, 0.0)
        assertEquals(1, remoto.revision)
        assertFalse(snap.esArchivado)
    }

    @Test
    fun camboSyncDtoArchivarMapeaProductoNull() {
        val dto = CambioSyncDto(
            revision = 3, aggregateId = "p1", action = "archivar",
            snapshot = ProductoSnapshot(id = "p1", archivedAt = "2026-08-19T12:00:00+00:00"),
        )
        val cambio = dto.toRemoto()
        assertEquals("archivar", cambio.action)
        assertEquals("p1", cambio.aggregateId)
        assertNull(cambio.producto)
    }

    @Test
    fun esEstablecimientoFantasmaDetectaElCodigo404() {
        assertTrue(
            esEstablecimientoFantasma(
                404,
                """{"code":"identity.establecimiento_no_encontrado","detail":"Establecimiento no encontrado"}""",
            )
        )
        assertFalse(esEstablecimientoFantasma(404, """{"code":"identity.otra_cosa"}"""))
        assertFalse(esEstablecimientoFantasma(403, """{"code":"identity.establecimiento_no_encontrado"}"""))
        assertFalse(esEstablecimientoFantasma(200, ""))
    }

    @Test
    fun archivarSerializaPayloadNull() {
        val req = OperacionSyncRequest(
            operationId = "op-2",
            deviceId = "bar-tablet-01",
            aggregateId = "agg-1",
            action = "archivar",
            payload = null,
            clientCreatedAt = "2026-08-19T12:00:00.000+00:00",
        )
        val json = LanJson.encodeToString(req)
        assertTrue(json.contains("\"payload\":null"))
        assertTrue(json.contains("\"action\":\"archivar\""))
        assertTrue(json.contains("\"base_snapshot\":null"))
    }
}
