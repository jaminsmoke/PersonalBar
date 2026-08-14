package com.jaminsmoke.personalbar.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

// ── Respuestas del servicio Identity negocio (v0.2) que consume Bar ───────────

@Serializable
data class IdentityLoginResponse(val token: String, val cuenta: IdentityCuentaNegocio = IdentityCuentaNegocio())

@Serializable
data class IdentityCuentaNegocio(
    val id: String = "",
    val email: String = "",
    @SerialName("nombre_mostrar") val nombreMostrar: String = "",
    @SerialName("tipo_establecimiento") val tipoEstablecimiento: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("camarero_vinculado_id") val camareroVinculadoId: String? = null,
)

@Serializable
data class IdentityRegistroResponse(val id: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegistroNegocioRequest(
    @SerialName("nombre_mostrar") val nombreMostrar: String,
    val email: String,
    val password: String,
    @SerialName("tipo_establecimiento") val tipoEstablecimiento: String? = null,
)

@Serializable
data class IdentityEstablecimiento(val id: String, val nombre: String)

@Serializable
data class IdentityCamarero(
    val id: String,
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
)

@Serializable
data class IdentityInvitacion(
    val id: String,
    val email: String,
    val rol: String = "staff",
    val estado: String = "pendiente",
    @SerialName("expira_en") val expiraEn: String? = null,
)

@Serializable
data class IdentityMembresia(
    val id: String,
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    @SerialName("camarero_id") val camareroId: String,
    val rol: String = "staff",
    val estado: String = "activa",
)

/**
 * Cliente HTTP del servicio Identity **negocio/establecimientos** (v0.2). Config
 * (URL + token + UUID del establecimiento) en memoria (v0.1): se pierde al reiniciar
 * la app. Si no está configurado, los métodos devuelven null/false y Bar sigue con su
 * lista local. Las operaciones «de camarero» (buscar por email, alta por QR) están
 * proxied internamente por este servicio; Bar no habla con el servicio camareros.
 */
object IdentityNegocioClient {

    /** URL por defecto del servicio negocio en desarrollo (emulador → host). En producción
     *  será un VPS; el usuario de Bar no configura esta URL (config de entorno). */
    const val DEFAULT_BASE_URL: String = "http://10.0.2.2:8082"

    /** Path relativo del logo de la cuenta de negocio (así lo devuelve Identity). */
    const val LOGO_PATH: String = "/v1/auth/negocio/me/logo"

    @Volatile
    var baseUrl: String? = DEFAULT_BASE_URL

    @Volatile
    var negocioToken: String? = null

    @Volatile
    var establecimientoUuid: String? = null

    /** Perfil de la cuenta de negocio logueada (nombre mostrado, email…). */
    @Volatile
    var cuentaNegocio: IdentityCuentaNegocio? = null

    val conectado: Boolean get() = baseUrl != null && negocioToken != null && establecimientoUuid != null

    fun configurar(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }

    fun desconectar() {
        baseUrl = null
        negocioToken = null
        establecimientoUuid = null
        cuentaNegocio = null
    }

    /** `POST /v1/auth/negocio/registro` → crea la cuenta de negocio. Devuelve el id o null. */
    suspend fun registroNegocio(
        nombreMostrar: String,
        email: String,
        password: String,
        tipo: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val body = LanJson.encodeToString(
            RegistroNegocioRequest(
                nombreMostrar = nombreMostrar,
                email = email,
                password = password,
                tipoEstablecimiento = tipo,
            )
        )
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/auth/negocio/registro", body = body, auth = false)
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<IdentityRegistroResponse>(text).id }.getOrNull()
        } else {
            null
        }
    }

    /** `POST /v1/auth/negocio/login` → guarda el token y el perfil de la cuenta de negocio. */
    suspend fun loginNegocio(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = LanJson.encodeToString(LoginRequest(email = email, password = password))
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/auth/negocio/login", body = body, auth = false)
        if (code in 200..299) {
            val resp = runCatching { LanJson.decodeFromString<IdentityLoginResponse>(text) }.getOrNull()
            if (resp != null && resp.token.isNotBlank()) {
                negocioToken = resp.token
                cuentaNegocio = resp.cuenta.takeIf { it.id.isNotBlank() || it.nombreMostrar.isNotBlank() }
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    /** `POST /v1/auth/negocio/me/logo` (multipart, campo `logo`) → sube el logo. El server
     *  lo normaliza a 256×256 WebP. Devuelve true si quedó subido. */
    suspend fun subirLogo(bytes: ByteArray, mimetype: String): Boolean = withContext(Dispatchers.IO) {
        IdentityHttp.uploadMultipart(baseUrl, LOGO_PATH, "logo", "logo.webp", bytes, mimetype, negocioToken)
    }

    /** `GET /v1/auth/negocio/me/logo` → bytes del logo, o null si no hay/falla. */
    suspend fun obtenerLogo(): ByteArray? = withContext(Dispatchers.IO) {
        val (code, bytes) = IdentityHttp.requestBytes(baseUrl, "GET", LOGO_PATH, negocioToken)
        if (code in 200..299) bytes else null
    }

    /** Crea o encuentra el establecimiento por nombre y guarda su UUID. */
    suspend fun vincularEstablecimiento(nombre: String): String? = withContext(Dispatchers.IO) {
        // 1. buscar entre los establecimientos de la cuenta (GET /mios)
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/mios", token = negocioToken)
        if (code in 200..299) {
            val existentes = runCatching { LanJson.decodeFromString<List<IdentityEstablecimiento>>(text) }
                .getOrNull().orEmpty()
            val encontrado = existentes.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
            if (encontrado != null) {
                establecimientoUuid = encontrado.id
                return@withContext encontrado.id
            }
        }
        // 2. crear el establecimiento si no existe
        val body = """{"nombre":"$nombre"}"""
        val (c2, t2) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos", body = body, token = negocioToken)
        if (c2 in 200..299) {
            val creado = runCatching { LanJson.decodeFromString<IdentityEstablecimiento>(t2) }.getOrNull()
            creado?.let { establecimientoUuid = it.id }
            return@withContext creado?.id
        }
        null
    }

    /** `POST /v1/establecimientos/{id}/miembros/qr` — el server verifica la firma Ed25519. */
    suspend fun altaPorQr(qr: String, rol: String = "staff"): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        val body = """{"qr":"$qr","rol":"$rol"}"""
        IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/miembros/qr", body = body, token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/camareros/buscar?email=` */
    suspend fun buscarCamareroPorEmail(email: String): IdentityCamarero? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val q = URLEncoder.encode(email, "UTF-8")
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/camareros/buscar?email=$q", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityCamarero>(text) }.getOrNull() else null
    }

    /** `POST /v1/establecimientos/{id}/invitaciones` → crea la invitación y envía el email. */
    suspend fun crearInvitacion(email: String, rol: String = "staff"): IdentityInvitacion? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = """{"email":"$email","rol":"$rol"}"""
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/invitaciones", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityInvitacion>(text) }.getOrNull() else null
    }

    /** `POST /v1/establecimientos/{id}/invitaciones/{invitacionId}/revocar` */
    suspend fun revocarInvitacion(invitacionId: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/invitaciones/$invitacionId/revocar", token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/miembros` → membresías del establecimiento. */
    suspend fun listarMiembros(): List<IdentityMembresia> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/miembros", token = negocioToken)
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<IdentityMembresia>>(text) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
    }

    /** `DELETE /v1/establecimientos/{id}/miembros/{camareroId}` */
    suspend fun revocarMiembro(camareroId: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "DELETE", "/v1/establecimientos/$id/miembros/$camareroId", token = negocioToken).first in 200..299
    }
}
