package com.jaminsmoke.personalbar.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente best-effort de PersonalHostel Identity (v0.2). En v0.1 [baseUrl] y
 * [negocioToken] son `null` (no hay Identity configurada), así que el alta remota
 * es no-op y Bar guarda solo la lista blanca local. Cuando se configuren, creará
 * la membresía en Identity (`POST /v1/establecimientos/{id}/miembros`).
 *
 * La verificación criptográfica del QR (Ed25519) y la invitación por email quedan
 * fuera de Bar: viven en el ítem de Identity `PVTI_lAHOBM87Yc4BgQqZzg2ecbc`.
 */
object IdentityClient {
    @Volatile
    var baseUrl: String? = null

    @Volatile
    var negocioToken: String? = null

    /** @return true si la membresía se creó en Identity; false si no configurado o falló. */
    suspend fun altaMiembro(establecimientoId: String, camareroId: String, rol: String): Boolean {
        val base = baseUrl?.trim()?.trimEnd('/') ?: return false
        val token = negocioToken ?: return false
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$base/v1/establecimientos/$establecimientoId/miembros")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val body = """{"camarero_id":"$camareroId","rol":"$rol"}"""
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                connection.responseCode in 200..299
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
