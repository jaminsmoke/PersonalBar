package com.jaminsmoke.personalbar.lan

/**
 * Payload de `GET /health`. Solo liveness; no revela layout ni mesas.
 * Construido como JSON plano (sin dependencias de serialización).
 */
object HealthPayload {
    fun json(
        ok: Boolean = true,
        role: String = BarLanConfig.ROLE,
        sala: String = "vacia",
        version: String = "0.1",
    ): String = buildString {
        append('{')
        append("\"ok\":").append(ok)
        append(",\"role\":\"").append(role).append('"')
        append(",\"sala\":\"").append(sala).append('"')
        append(",\"version\":\"").append(version).append('"')
        append('}')
    }
}
