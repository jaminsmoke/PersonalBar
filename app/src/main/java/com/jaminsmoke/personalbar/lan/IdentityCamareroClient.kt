package com.jaminsmoke.personalbar.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Clave pública del QR profesional (`GET /v1/keys/qr` del servicio camareros). */
@Serializable
data class IdentityQrPublicKey(
    val algorithm: String = "",
    @SerialName("key_id") val keyId: String = "",
    @SerialName("public_key") val publicKey: String = "",
    @SerialName("qr_prefix") val qrPrefix: String = "",
    val format: String = "",
)

/**
 * Cliente HTTP del servicio Identity **camareros/profesionales** (v0.2). Hoy Bar solo
 * usa el servicio negocio (las operaciones de camarero están proxied por negocio), así
 * que este cliente es la cimentación para futuras llamadas directas a camareros.
 */
object IdentityCamareroClient {

    /** URL por defecto del servicio camareros en desarrollo (emulador → host). */
    const val DEFAULT_BASE_URL: String = "http://10.0.2.2:8080"

    @Volatile
    var baseUrl: String? = DEFAULT_BASE_URL

    fun configurar(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }

    /** `GET /v1/keys/qr` → clave pública Ed25519 para verificar QRs profesionales. */
    suspend fun clavePublicaQr(): IdentityQrPublicKey? = withContext(Dispatchers.IO) {
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/keys/qr", auth = false)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityQrPublicKey>(text) }.getOrNull() else null
    }
}
