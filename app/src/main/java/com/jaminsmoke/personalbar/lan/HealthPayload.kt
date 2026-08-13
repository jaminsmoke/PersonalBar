package com.jaminsmoke.personalbar.lan

/**
 * Payload de `GET /health`. Solo liveness; no revela layout ni mesas.
 *
 * `establecimiento` es el nombre canónico del negocio/local (cuenta del nodo).
 * `sala` se mantiene como **alias deprecado** con el mismo valor: los Commander
 * aún leen `sala` como fallback hasta que migren a `establecimiento`.
 */
object HealthPayload {
    fun json(
        ok: Boolean = true,
        role: String = BarLanConfig.ROLE,
        establecimiento: String = "local-1",
        version: String = "0.1",
    ): String = buildString {
        append('{')
        append("\"ok\":").append(ok)
        append(",\"role\":\"").append(role).append('"')
        append(",\"establecimiento\":\"").append(establecimiento).append('"')
        append(",\"sala\":\"").append(establecimiento).append('"')
        append(",\"version\":\"").append(version).append('"')
        append('}')
    }
}
