package com.jaminsmoke.personalbar.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLEncoder
import com.jaminsmoke.personalbar.BuildConfig
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.Sala

// ── Respuestas del servicio Identity negocio (v0.2) que consume Bar ───────────

/**
 * Procedencia canónica de Identity (`data_origin = real|test|demo`). Inmutable y
 * heredada por el establecimiento y sus productos en servidor; Bar solo la propaga
 * y la valida. [desdeApi] tolera valores desconocidos (respuestas de versiones
 * futuras) degradando a [REAL].
 */
enum class DataOrigin(val apiValor: String) {
    REAL("real"),
    TEST("test"),
    DEMO("demo");

    companion object {
        fun desdeApi(valor: String?): DataOrigin? = when (valor) {
            null -> null
            "real" -> REAL
            "test" -> TEST
            "demo" -> DEMO
            else -> null
        }
    }
}

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
    @SerialName("data_origin") val dataOrigin: String? = null,
)

@Serializable
data class IdentityRegistroResponse(
    val id: String,
    @SerialName("data_origin") val dataOrigin: String? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegistroNegocioRequest(
    @SerialName("nombre_mostrar") val nombreMostrar: String,
    val email: String,
    val password: String,
    @SerialName("tipo_establecimiento") val tipoEstablecimiento: String? = null,
    // Se omite en el JSON cuando es null (real): el default de Identity es `real` y
    // no debe serializarse explícitamente salvo para test/demo.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("data_origin") val dataOrigin: String? = null,
)

@Serializable
data class IdentityEstablecimiento(
    val id: String,
    val nombre: String,
    @SerialName("tipo_establecimiento") val tipoEstablecimiento: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("cuenta_negocio_id") val cuentaNegocioId: String? = null,
    @SerialName("data_origin") val dataOrigin: String? = null,
    /** Opt-in del dueño para aparecer en el directorio de establecimientos (sin PII). */
    @SerialName("visible_directorio") val visibleDirectorio: Boolean? = null,
)

/** Cuerpo de `PATCH /v1/establecimientos/{id}`. Los campos null se omiten (Identity
 *  exige al menos uno); se envían solo los que cambian. */
@Serializable
data class EstablecimientoUpdateRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nombre: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("tipo_establecimiento")
    val tipoEstablecimiento: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("visible_directorio")
    val visibleDirectorio: Boolean? = null,
)

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
    @SerialName("establecimiento_id") val establecimientoId: String? = null,
    @SerialName("expira_en") val expiraEn: String? = null,
    @SerialName("creada_en") val creadaEn: String? = null,
)

@Serializable
data class IdentityMembresia(
    val id: String,
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    @SerialName("camarero_id") val camareroId: String,
    val rol: String = "staff",
    val estado: String = "activa",
)

@Serializable
data class LayoutUpdateRequest(
    val salas: List<Sala>,
    val mesas: List<Mesa>,
)

@Serializable
data class IdentityLayout(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val version: Int = 0,
    val salas: List<Sala> = emptyList(),
    val mesas: List<Mesa> = emptyList(),
)

/**
 * Enlace público del establecimiento (ficha o carta). `url_publica` la construye
 * Identity con sus envs (FICHA_NEGOCIO_URL_BASE / CARTA_URL_BASE); Bar solo la
 * consume y muestra, nunca concatena dominios ni rutas.
 */
@Serializable
data class IdentityEnlacePublico(
    val id: String,
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val tipo: String = "",
    val slug: String? = null,
    val estado: String = "activo",
    @SerialName("expira_en") val expiraEn: String? = null,
    @SerialName("url_publica") val urlPublica: String? = null,
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

    /** Procedencia (`data_origin`) del establecimiento vinculado, devuelta por Identity. */
    @Volatile
    var establecimientoDataOrigin: String? = null

    val conectado: Boolean get() = baseUrl != null && negocioToken != null && establecimientoUuid != null

    fun configurar(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }

    fun desconectar() {
        baseUrl = null
        negocioToken = null
        establecimientoUuid = null
        cuentaNegocio = null
        establecimientoDataOrigin = null
    }

    /** `POST /v1/auth/negocio/registro` → crea la cuenta de negocio. Devuelve el id o null. */
    suspend fun registroNegocio(
        nombreMostrar: String,
        email: String,
        password: String,
        tipo: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        // La procedencia solo se envía cuando NO es `real` (builds de test/demo con
        // -PdataOrigin). En builds normales el campo se omite y Identity aplica `real`.
        val dataOrigin = DataOrigin.desdeApi(BuildConfig.DATA_ORIGIN)?.takeIf { it != DataOrigin.REAL }
        val body = LanJson.encodeToString(
            RegistroNegocioRequest(
                nombreMostrar = nombreMostrar,
                email = email,
                password = password,
                tipoEstablecimiento = tipo,
                dataOrigin = dataOrigin?.apiValor,
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

    /**
     * Crea o encuentra el establecimiento por nombre y guarda su UUID. También
     * captura la procedencia (`data_origin`) devuelta y la valida contra la cuenta:
     * si ambas son conocidas y no `real` pero difieren, hay linaje incoherente y se
     * devuelve null sin fijar la sesión.
     */
    suspend fun vincularEstablecimiento(nombre: String): String? = withContext(Dispatchers.IO) {
        var encontrado: IdentityEstablecimiento? = null
        // 1. buscar entre los establecimientos de la cuenta (GET /mios)
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/mios", token = negocioToken)
        if (code in 200..299) {
            val existentes = runCatching { LanJson.decodeFromString<List<IdentityEstablecimiento>>(text) }
                .getOrNull().orEmpty()
            encontrado = existentes.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
        }
        // 2. crear el establecimiento si no existe
        if (encontrado == null) {
            val body = """{"nombre":"$nombre"}"""
            val (c2, t2) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos", body = body, token = negocioToken)
            if (c2 in 200..299) {
                encontrado = runCatching { LanJson.decodeFromString<IdentityEstablecimiento>(t2) }.getOrNull()
            }
        }
        val est = encontrado ?: return@withContext null
        // Coherencia de linaje: cuenta y establecimiento no pueden contradecirse.
        val cuentaOrigen = cuentaNegocio?.dataOrigin?.let { DataOrigin.desdeApi(it) }
        val estOrigen = DataOrigin.desdeApi(est.dataOrigin)
        if (cuentaOrigen != null && estOrigen != null && cuentaOrigen != estOrigen) {
            establecimientoDataOrigin = null
            return@withContext null
        }
        establecimientoUuid = est.id
        establecimientoDataOrigin = est.dataOrigin
        return@withContext est.id
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

    /** `GET /v1/establecimientos/{id}/invitaciones` — lista las invitaciones del establecimiento
     *  (filtro opcional `?estado=`). El server deriva `expirada` para pendientes vencidas. */
    suspend fun listarInvitaciones(estado: String? = null): List<IdentityInvitacion> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val q = estado?.takeIf { it.isNotBlank() }?.let { "?estado=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/invitaciones$q", token = negocioToken)
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<IdentityInvitacion>>(text) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
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

    /** `PUT /v1/establecimientos/{id}/layout` — respaldo del layout (salas + mesas). */
    suspend fun guardarLayout(salas: List<Sala>, mesas: List<Mesa>): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        val body = LanJson.encodeToString(LayoutUpdateRequest(salas = salas, mesas = mesas))
        IdentityHttp.request(baseUrl, "PUT", "/v1/establecimientos/$id/layout", body = body, token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/layout` — recupera el layout respaldado, o null. */
    suspend fun obtenerLayout(): Pair<List<Sala>, List<Mesa>>? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/layout", token = negocioToken)
        if (code !in 200..299) return@withContext null
        runCatching { LanJson.decodeFromString<IdentityLayout>(text) }
            .getOrNull()?.let { it.salas to it.mesas }
    }

    // ── Enlaces públicos del establecimiento (ficha_negocio | carta) ─────────

    /** `POST /v1/establecimientos/{id}/enlaces` — crea el enlace; idempotente: si ya
     *  existe uno activo del mismo tipo devuelve 200 con el existente (y 201 al crear). */
    suspend fun crearEnlacePublico(tipo: String): IdentityEnlacePublico? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = """{"tipo":"$tipo"}"""
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/enlaces", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityEnlacePublico>(text) }.getOrNull() else null
    }

    /** `GET /v1/establecimientos/{id}/enlaces` — lista los enlaces del establecimiento. */
    suspend fun listarEnlacesPublicos(): List<IdentityEnlacePublico> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/enlaces", token = negocioToken)
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<IdentityEnlacePublico>>(text) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
    }

    /** `POST /v1/establecimientos/{id}/enlaces/{enlaceId}/revocar` */
    suspend fun revocarEnlacePublico(enlaceId: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/enlaces/$enlaceId/revocar", token = negocioToken).first in 200..299
    }

    /** `POST /v1/establecimientos/{id}/enlaces/{enlaceId}/rotar` — sustituye el enlace;
     *  el slug anterior pasa a responder 410. Devuelve el enlace nuevo (o null si falla). */
    suspend fun rotarEnlacePublico(enlaceId: String): IdentityEnlacePublico? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/enlaces/$enlaceId/rotar", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityEnlacePublico>(text) }.getOrNull() else null
    }

    // ── Perfil del establecimiento (nombre / tipo / logo del local) ──────────

    /** `GET /v1/establecimientos/{id}` → perfil canónico del establecimiento. */
    suspend fun obtenerEstablecimiento(): IdentityEstablecimiento? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityEstablecimiento>(text) }.getOrNull() else null
    }

    /** `PATCH /v1/establecimientos/{id}` → actualiza nombre, tipo y/o el opt-in de
     *  visibilidad en el directorio. Identity exige al menos un campo: [nombre], [tipo]
     *  y/o [visibleDirectorio] no nulos. Devuelve el perfil actualizado. */
    suspend fun editarEstablecimiento(
        nombre: String? = null,
        tipo: String? = null,
        visibleDirectorio: Boolean? = null,
    ): IdentityEstablecimiento? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = LanJson.encodeToString(
            EstablecimientoUpdateRequest(
                nombre = nombre?.takeIf { it.isNotBlank() },
                tipoEstablecimiento = tipo?.takeIf { it.isNotBlank() },
                visibleDirectorio = visibleDirectorio,
            )
        )
        val (code, text) = IdentityHttp.request(baseUrl, "PATCH", "/v1/establecimientos/$id", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityEstablecimiento>(text) }.getOrNull() else null
    }

    /** `POST /v1/establecimientos/{id}/logo` (multipart) → sube/reemplaza el logo del local. */
    suspend fun subirLogoEstablecimiento(bytes: ByteArray, mimetype: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.uploadMultipart(baseUrl, "/v1/establecimientos/$id/logo", "logo", "logo.webp", bytes, mimetype, negocioToken)
    }

    /** `GET /v1/establecimientos/{id}/logo` → bytes del logo efectivo (local o heredado), o null. */
    suspend fun obtenerLogoEstablecimiento(): ByteArray? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, bytes) = IdentityHttp.requestBytes(baseUrl, "GET", "/v1/establecimientos/$id/logo", negocioToken)
        if (code in 200..299) bytes else null
    }

    /** `DELETE /v1/establecimientos/{id}/logo` → borra el override local (hereda el logo org). */
    suspend fun borrarLogoEstablecimiento(): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "DELETE", "/v1/establecimientos/$id/logo", token = negocioToken).first in 200..299
    }
}
