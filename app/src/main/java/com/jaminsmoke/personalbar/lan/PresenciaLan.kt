package com.jaminsmoke.personalbar.lan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Anuncio UDP de presencia del nodo (no es HTTP).
 * Puerto [PUERTO], distinto de [BarLanConfig.PORT].
 * Commander confirma con GET /health antes de pintar.
 */
object PresenciaLan {
    const val PUERTO: Int = 8788
    const val MAGIC: String = "phbar1"
    const val HEARTBEAT_MS: Long = 2_000L
    const val ADIOS_VECES: Int = 3

    private val json = Json { ignoreUnknownKeys = true }

    data class Anuncio(
        val establecimiento: String,
        val puertoHttp: Int = BarLanConfig.PORT,
        val activo: Boolean,
    )

    @Serializable
    private data class Wire(
        val ph: String? = null,
        val role: String? = null,
        val establecimiento: String = "",
        val puerto: Int = BarLanConfig.PORT,
        val activo: Boolean? = null,
    )

    fun encode(anuncio: Anuncio): String {
        val nombre = escapeJson(anuncio.establecimiento)
        return "{" +
            "\"ph\":\"$MAGIC\"," +
            "\"role\":\"${BarLanConfig.ROLE}\"," +
            "\"establecimiento\":\"$nombre\"," +
            "\"puerto\":${anuncio.puertoHttp}," +
            "\"activo\":${anuncio.activo}" +
            "}"
    }

    fun decode(texto: String): Anuncio? = try {
        val w = json.decodeFromString(Wire.serializer(), texto)
        if (w.ph != MAGIC) return null
        if (!w.role.equals(BarLanConfig.ROLE, ignoreCase = true)) return null
        val activo = w.activo ?: return null
        if (w.puerto !in 1..65535) return null
        Anuncio(
            establecimiento = w.establecimiento.trim(),
            puertoHttp = w.puerto,
            activo = activo,
        )
    } catch (_: Exception) {
        null
    }

    private fun escapeJson(valor: String): String =
        valor.replace("\\", "\\\\").replace("\"", "\\\"")
}
