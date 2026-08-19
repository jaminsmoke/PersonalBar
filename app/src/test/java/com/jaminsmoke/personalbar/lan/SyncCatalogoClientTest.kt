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

    @Test
    fun conflictoSyncDtoDeserializaSnakeCase() {
        val json = """[
            {
                "id": "c1",
                "operation_id": "op1",
                "aggregate_type": "producto",
                "aggregate_id": "p1",
                "action": "actualizar",
                "base_revision": 0,
                "canonical_revision": 2,
                "canonical_snapshot": {"id":"p1","nombre":"Caña","precio_centimos":250,"disponible":true,"revision":2},
                "proposed_snapshot": {"id":"p1","nombre":"Caña doble","precio_centimos":300,"disponible":true},
                "estado": "pendiente",
                "device_id": "bar-tablet-01",
                "client_created_at": "2026-08-19T12:00:00+00:00"
            }
        ]""".trimIndent()
        val list = LanJson.decodeFromString<List<ConflictoSyncDto>>(json)
        assertEquals(1, list.size)
        assertEquals("op1", list[0].operationId)
        assertEquals(2, list[0].canonicalRevision)
        assertEquals("Caña", list[0].toRemoto().canonical?.nombre)
        assertEquals("Caña doble", list[0].toRemoto().proposed?.nombre)
    }

    @Test
    fun conflictoSyncDtoArchivarDejaPropuestoNull() {
        val dto = ConflictoSyncDto(
            id = "c1", aggregateId = "p1", action = "archivar", canonicalRevision = 2,
            canonicalSnapshot = ProductoSnapshot(id = "p1", nombre = "Caña", precioCentimos = 250, revision = 2),
            proposedSnapshot = ProductoSnapshot(id = "p1"),
        )
        val conflicto = dto.toRemoto()
        assertNull(conflicto.proposed)
        assertEquals("Caña", conflicto.canonical?.nombre)
        assertEquals(2.5, conflicto.canonical?.precio ?: 0.0, 0.0)
    }

    @Test
    fun conflictoSyncDtoSinCanonicalDejaCanonicalNull() {
        val dto = ConflictoSyncDto(
            id = "c1", aggregateId = "p1", action = "actualizar",
            proposedSnapshot = ProductoSnapshot(id = "p1", nombre = "Nuevo", precioCentimos = 100),
        )
        val conflicto = dto.toRemoto()
        assertNull(conflicto.canonical)
        assertEquals("Nuevo", conflicto.proposed?.nombre)
    }

    @Test
    fun resolverConflictoRequestSerializaSnakeCase() {
        val json = LanJson.encodeToString(ResolverConflictoRequest(decision = "aceptar", expectedRevision = 3))
        assertTrue(json.contains("\"decision\":\"aceptar\""))
        assertTrue(json.contains("\"expected_revision\":3"))
    }

    @Test
    fun mapearResultadoResolucionMapeaCodigos() {
        assertEquals(ResultadoResolucion.Resuelta, mapearResultadoResolucion(200, "{}"))
        assertEquals(
            ResultadoResolucion.Obsoleta,
            mapearResultadoResolucion(409, """{"code":"identity.resolucion_sync_obsoleta"}"""),
        )
        assertEquals(
            ResultadoResolucion.YaResuelta,
            mapearResultadoResolucion(409, """{"code":"identity.conflicto_sync_ya_resuelto"}"""),
        )
        assertEquals(
            ResultadoResolucion.EstablecimientoFantasma,
            mapearResultadoResolucion(404, """{"code":"identity.establecimiento_no_encontrado"}"""),
        )
        assertEquals(ResultadoResolucion.Error, mapearResultadoResolucion(500, "{}"))
    }

    @Test
    fun notificacionNegocioDtoMapeaCamposYDeepLink() {
        val json = """[
            {
                "id": "n1",
                "establecimiento_id": "e1",
                "conflicto_id": "c1",
                "tipo": "conflicto_sync",
                "titulo": "Cambio pendiente",
                "mensaje": "Revisa los valores",
                "payload": {"conflicto_id":"c1","deep_link":"personalhostel://establecimientos/e1/conflictos/c1"},
                "created_at": "2026-08-19T12:00:00+00:00",
                "read_at": null
            }
        ]""".trimIndent()
        val list = LanJson.decodeFromString<List<NotificacionNegocioDto>>(json)
        assertEquals(1, list.size)
        val n = list[0].toRemoto()
        assertEquals("n1", n.id)
        assertEquals("c1", n.conflictoId)
        assertEquals("conflicto_sync", n.tipo)
        assertEquals("personalhostel://establecimientos/e1/conflictos/c1", n.deepLink)
        assertFalse(n.leida)
    }

    @Test
    fun notificacionNegocioDtoLeidaSinDeepLink() {
        val dto = NotificacionNegocioDto(
            id = "n1", conflictoId = "c1", tipo = "conflicto_sync",
            titulo = "T", mensaje = "M", payload = emptyMap(),
            readAt = "2026-08-19T12:00:00+00:00",
        )
        val n = dto.toRemoto()
        assertTrue(n.leida)
        assertNull(n.deepLink)
    }

    @Test
    fun mapearResultadoMarcarLeidaMapeaCodigos() {
        assertEquals(ResultadoMarcarLeida.Leida, mapearResultadoMarcarLeida(200, "{}"))
        assertEquals(
            ResultadoMarcarLeida.NoEncontrada,
            mapearResultadoMarcarLeida(404, """{"code":"identity.notificacion_no_encontrada"}"""),
        )
        assertEquals(
            ResultadoMarcarLeida.EstablecimientoFantasma,
            mapearResultadoMarcarLeida(404, """{"code":"identity.establecimiento_no_encontrado"}"""),
        )
        assertEquals(ResultadoMarcarLeida.Error, mapearResultadoMarcarLeida(500, "{}"))
    }
}
