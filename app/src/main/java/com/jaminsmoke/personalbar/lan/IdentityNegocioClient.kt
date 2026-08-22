package com.jaminsmoke.personalbar.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.jaminsmoke.personalbar.BuildConfig
import com.jaminsmoke.personalbar.data.Invitacion
import com.jaminsmoke.personalbar.data.InvitacionEstado
import com.jaminsmoke.personalbar.data.CambioRemoto
import com.jaminsmoke.personalbar.data.ConflictoRemoto
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.NotificacionRemoto
import com.jaminsmoke.personalbar.data.OperacionCatalogo
import com.jaminsmoke.personalbar.data.ProductoRemoto
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.Zona
import com.jaminsmoke.personalbar.data.invitacionEstadoDesdeApi

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

/** Resultado de `POST /v1/auth/negocio/me/password`. */
enum class CambioPasswordResult {
    OK,
    ACTUAL_INCORRECTA,
    ERROR,
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

/** Cuerpo de `POST /v1/auth/negocio/me/password` (rotación de la contraseña de negocio). */
@Serializable
data class CambioPasswordRequest(
    @SerialName("password_actual") val passwordActual: String,
    @SerialName("password_nueva") val passwordNueva: String,
)

@Serializable
data class CambioPasswordResponse(
    val status: String,
)

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

/** `GET/PATCH /v1/establecimientos/{id}/perfil-web`. */
@Serializable
data class IdentityPerfilWeb(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val eslogan: String? = null,
    val descripcion: String? = null,
    val direccion: String? = null,
    val ciudad: String? = null,
    val telefono: String? = null,
    @SerialName("email_contacto") val emailContacto: String? = null,
    val web: String? = null,
    val redes: Map<String, String> = emptyMap(),
    val tz: String = "Europe/Madrid",
    val plantilla: String = "estate_hospitality",
    @SerialName("color_primario") val colorPrimario: String? = null,
    @SerialName("web_publica") val webPublica: Boolean = true,
    @SerialName("mostrar_equipo") val mostrarEquipo: Boolean = false,
    @SerialName("hero_url") val heroUrl: String? = null,
)

/** PATCH parcial: los null no se serializan (`EncodeDefault.NEVER`). */
@Serializable
data class IdentityPerfilWebUpdate(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val eslogan: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val descripcion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direccion: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ciudad: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val telefono: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("email_contacto") val emailContacto: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val web: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val redes: Map<String, String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tz: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("color_primario") val colorPrimario: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("web_publica") val webPublica: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("mostrar_equipo") val mostrarEquipo: Boolean? = null,
)

@Serializable
data class IdentityTurnoHorario(val abre: String, val cierra: String)

@Serializable
data class IdentityHorarioDia(
    @SerialName("dia_semana") val diaSemana: Int,
    val cerrado: Boolean = false,
    val turnos: List<IdentityTurnoHorario> = emptyList(),
)

@Serializable
data class IdentityHorarioResponse(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val dias: List<IdentityHorarioDia> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class IdentityHorarioUpdate(val dias: List<IdentityHorarioDia>)

/** Respuesta de los endpoints de jornada CFC (`/v1/establecimientos/{id}/cfc/...`). */
@Serializable
data class JornadaCfcResponse(
    val id: String = "",
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    @SerialName("abierta_en") val abiertaEn: String? = null,
    @SerialName("ultimo_heartbeat") val ultimoHeartbeat: String? = null,
    @SerialName("cerrada_en") val cerradaEn: String? = null,
    @SerialName("bar_en_linea") val barEnLinea: Boolean = true,
)

/** Una mesa del conjunto que Bar envía a Identity (UUID estable + etiqueta UX). */
@Serializable
data class MesaCfcItem(
    @SerialName("mesa_uuid") val mesaUuid: String,
    val etiqueta: String,
)

/** Respuesta de los endpoints de mesas CFC (PUT GET rotar). */
@Serializable
data class MesaCfcResponse(
    @SerialName("mesa_uuid") val mesaUuid: String = "",
    val etiqueta: String = "",
    val estado: String = "",
    @SerialName("url_publica") val urlPublica: String? = null,
)

@Serializable
data class IdentityImagenGaleria(
    val id: String,
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val url: String = "",
    val mimetype: String = "",
    val size: Int = 0,
    val orden: Int = 0,
    @SerialName("creada_en") val creadaEn: String? = null,
)

@Serializable
data class IdentityCamarero(
    val id: String,
    val nombre: String = "",
    val apellidos: String = "",
    val email: String = "",
)

/** Entrada del directorio de camareros (`GET .../camareros/directorio`). Sin email (privacidad). */
@Serializable
data class IdentityCamareroDirectorio(
    val id: String,
    val nombre: String = "",
    val apellidos: String = "",
    val nick: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    val libre: Boolean = false,
    val visibilidad: String = "nunca",
) {
    val nombreCompleto: String
        get() = "$nombre $apellidos".trim().ifBlank { nick.orEmpty().ifBlank { "Camarero" } }

    val iniciales: String
        get() = buildString {
            nombre.firstOrNull()?.let { append(it.uppercaseChar()) }
            apellidos.firstOrNull()?.let { append(it.uppercaseChar()) }
        }.ifBlank { nick?.firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifBlank { "?" } }
}

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

/**
 * Mapea la respuesta de Identity a la entidad local [Invitacion] (espejo). El
 * estado lo convierte [invitacionEstadoDesdeApi]; un valor desconocido degrada a
 * PENDIENTE (el estado canónico lo deriva Identity).
 */
fun IdentityInvitacion.toInvitacion(): Invitacion = Invitacion(
    id = id,
    email = email,
    rol = rol,
    estado = invitacionEstadoDesdeApi(estado) ?: InvitacionEstado.PENDIENTE,
    expiraEn = expiraEn,
)

@Serializable
data class IdentityMembresia(
    val id: String,
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    @SerialName("camarero_id") val camareroId: String,
    val rol: String = "staff",
    val estado: String = "activa",
)

/** Cuerpo de `POST /v1/negocio/estadisticas/servicio` (productor del libro de oficio). */
@Serializable
data class ServicioRegistroRequest(
    @SerialName("establecimiento_id") val establecimientoId: String,
    @SerialName("camarero_id") val camareroId: String,
    @SerialName("evento_id") val eventoId: String,
    val tipo: String = "ronda_servida",
    val cantidad: Int = 1,
)

/**
 * Resultado de entregar una operación del outbox de catálogo a Identity.
 * [Aplicada] lleva la revisión canónica del producto (`null` = archivado, el
 * server devuelve un tombstone sin revisión); [Conflicto]/[Rechazada]/[Error]
 * dejan la operación en la cola para el siguiente ciclo (o resolución manual).
 */
sealed interface ResultadoSyncCatalogo {
    data class Aplicada(val revision: Int?) : ResultadoSyncCatalogo
    data object Conflicto : ResultadoSyncCatalogo
    data object Rechazada : ResultadoSyncCatalogo
    data object Error : ResultadoSyncCatalogo
    /** El establecimiento ya no existe en el server (404/410): desvincular y conservar el outbox. */
    data object EstablecimientoFantasma : ResultadoSyncCatalogo
}

/** Payload del producto en `POST /sync/operaciones` (campos exactos del contrato v0.2). */
@Serializable
data class ProductoPayload(
    val nombre: String,
    val categoria: String,
    val descripcion: String? = null,
    val destino: String,
    @SerialName("precio_centimos") val precioCentimos: Int,
    val moneda: String = "EUR",
    val disponible: Boolean = true,
)

/** Cuerpo de `POST /v1/establecimientos/{id}/sync/operaciones`. */
@Serializable
data class OperacionSyncRequest(
    @SerialName("operation_id") val operationId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("aggregate_type") val aggregateType: String = "producto",
    @SerialName("aggregate_id") val aggregateId: String,
    val action: String,
    @SerialName("base_revision") val baseRevision: Int = 0,
    @SerialName("base_snapshot") val baseSnapshot: Map<String, String>? = null,
    val payload: ProductoPayload? = null,
    @SerialName("client_created_at") val clientCreatedAt: String,
)

/**
 * Snapshot de producto devuelto por Identity (`result_snapshot` de `POST /sync/operaciones`
 * y `snapshot` de `GET /sync/cambios`). Para `archivar` es un tombstone (`id` +
 * `archived_at`, sin el resto); para crear/actualizar trae el snapshot completo.
 */
@Serializable
data class ProductoSnapshot(
    val id: String = "",
    val nombre: String? = null,
    val categoria: String? = null,
    val descripcion: String? = null,
    val destino: String? = null,
    @SerialName("precio_centimos") val precioCentimos: Int? = null,
    val moneda: String? = null,
    val disponible: Boolean? = null,
    val revision: Int? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
) {
    /** true si es el tombstone de un archivado (sin datos de producto). */
    val esArchivado: Boolean get() = nombre == null && archivedAt != null

    fun toRemoto(): ProductoRemoto = ProductoRemoto(
        id = id,
        nombre = nombre.orEmpty(),
        categoria = categoria.orEmpty(),
        precio = (precioCentimos ?: 0) / 100.0,
        disponible = disponible ?: true,
        revision = revision ?: 0,
        descripcion = descripcion,
    )
}

@Serializable
data class OperacionSyncResponse(
    @SerialName("operation_id") val operationId: String = "",
    val estado: String = "",
    @SerialName("aggregate_type") val aggregateType: String = "",
    @SerialName("aggregate_id") val aggregateId: String = "",
    val action: String = "",
    @SerialName("base_revision") val baseRevision: Int = 0,
    @SerialName("global_revision") val globalRevision: Int? = null,
    @SerialName("result_snapshot") val resultSnapshot: ProductoSnapshot? = null,
    @SerialName("conflict_id") val conflictId: String? = null,
    @SerialName("client_created_at") val clientCreatedAt: String = "",
    @SerialName("server_received_at") val serverReceivedAt: String = "",
)

/** `client_created_at` con offset obligatorio (el server rechaza timestamps naive con 422). */
private val SYNC_TS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")

internal fun epochUtcIso(epochMs: Long): String =
    SYNC_TS_FORMAT.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))

/** Cuerpo de error de Identity (`{code, detail}`); el `code` es estable para ramificar. */
@Serializable
data class IdentityError(val code: String = "", val detail: String = "")

/** true si la respuesta es el 404/410 de «establecimiento fantasma» (ya no existe en el server). */
internal fun esEstablecimientoFantasma(code: Int, text: String): Boolean {
    if (code != 404 && code != 410) return false
    return runCatching { LanJson.decodeFromString<IdentityError>(text).code }
        .getOrNull() == "identity.establecimiento_no_encontrado"
}

/**
 * Mapea la respuesta de `POST /sync/conflictos/{id}/resolver` a [ResultadoResolucion].
 * 2xx → [ResultadoResolucion.Resuelta]; 409 con `identity.resolucion_sync_obsoleta` →
 * [ResultadoResolucion.Obsoleta]; 409 con `identity.conflicto_sync_ya_resuelto` →
 * [ResultadoResolucion.YaResuelta]; 404/410 fantasma → [ResultadoResolucion.EstablecimientoFantasma].
 */
internal fun mapearResultadoResolucion(code: Int, text: String): ResultadoResolucion {
    if (esEstablecimientoFantasma(code, text)) return ResultadoResolucion.EstablecimientoFantasma
    if (code in 200..299) return ResultadoResolucion.Resuelta
    val error = runCatching { LanJson.decodeFromString<IdentityError>(text) }.getOrNull()
    return when (error?.code) {
        "identity.resolucion_sync_obsoleta" -> ResultadoResolucion.Obsoleta
        "identity.conflicto_sync_ya_resuelto" -> ResultadoResolucion.YaResuelta
        else -> ResultadoResolucion.Error
    }
}

/**
 * Mapea la respuesta de `POST /notificaciones/{id}/leer` a [ResultadoMarcarLeida].
 * 2xx → [ResultadoMarcarLeida.Leida]; 404 `identity.notificacion_no_encontrada` →
 * [ResultadoMarcarLeida.NoEncontrada]; 404/410 fantasma → EstablecimientoFantasma.
 */
internal fun mapearResultadoMarcarLeida(code: Int, text: String): ResultadoMarcarLeida {
    if (esEstablecimientoFantasma(code, text)) return ResultadoMarcarLeida.EstablecimientoFantasma
    if (code in 200..299) return ResultadoMarcarLeida.Leida
    val error = runCatching { LanJson.decodeFromString<IdentityError>(text) }.getOrNull()
    if (code == 404 && error?.code == "identity.notificacion_no_encontrada") {
        return ResultadoMarcarLeida.NoEncontrada
    }
    return ResultadoMarcarLeida.Error
}

/** Producto de `GET /catalogo` (snapshot completo). */
@Serializable
data class ProductoCatalogoDto(
    val id: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val descripcion: String? = null,
    val destino: String = "",
    @SerialName("precio_centimos") val precioCentimos: Int = 0,
    val moneda: String = "EUR",
    val disponible: Boolean = true,
    val revision: Int = 0,
) {
    fun toRemoto(): ProductoRemoto = ProductoRemoto(
        id = id, nombre = nombre, categoria = categoria,
        precio = precioCentimos / 100.0, disponible = disponible, revision = revision,
        descripcion = descripcion,
    )
}

@Serializable
data class CatalogoResponseDto(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val revision: Int = 0,
    @SerialName("server_time") val serverTime: String = "",
    val productos: List<ProductoCatalogoDto> = emptyList(),
)

/** Cambio (delta) de `GET /sync/cambios`. */
@Serializable
data class CambioSyncDto(
    val revision: Int = 0,
    @SerialName("operation_id") val operationId: String = "",
    @SerialName("aggregate_type") val aggregateType: String = "",
    @SerialName("aggregate_id") val aggregateId: String = "",
    val action: String = "",
    val snapshot: ProductoSnapshot = ProductoSnapshot(),
) {
    fun toRemoto(): CambioRemoto {
        val producto = if (action == "archivar" || snapshot.esArchivado) null else snapshot.toRemoto()
        return CambioRemoto(aggregateId = aggregateId, action = action, producto = producto)
    }
}

@Serializable
data class CambiosResponseDto(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val desde: Int = 0,
    @SerialName("revision_actual") val revisionActual: Int = 0,
    val cambios: List<CambioSyncDto> = emptyList(),
)

/** Resultado del pull de catálogo (snapshot inicial o deltas). */
sealed interface ResultadoPullCatalogo {
    data class Snapshot(val productos: List<ProductoRemoto>, val revision: Int) : ResultadoPullCatalogo
    data class Cambios(val cambios: List<CambioRemoto>, val revisionActual: Int) : ResultadoPullCatalogo
    data object EstablecimientoFantasma : ResultadoPullCatalogo
    data object Error : ResultadoPullCatalogo
}

/** Cuerpo de `POST /v1/establecimientos/{id}/sync/conflictos/{id}/resolver`. */
@Serializable
data class ResolverConflictoRequest(
    val decision: String,       // aceptar|rechazar
    @SerialName("expected_revision") val expectedRevision: Int,
)

/** Conflicto de `GET /sync/conflictos` (un `ConflictoSyncResponse` de Identity). */
@Serializable
data class ConflictoSyncDto(
    val id: String = "",
    @SerialName("operation_id") val operationId: String = "",
    @SerialName("aggregate_type") val aggregateType: String = "",
    @SerialName("aggregate_id") val aggregateId: String = "",
    val action: String = "",
    @SerialName("base_revision") val baseRevision: Int = 0,
    @SerialName("canonical_revision") val canonicalRevision: Int = 0,
    @SerialName("base_snapshot") val baseSnapshot: ProductoSnapshot? = null,
    @SerialName("canonical_snapshot") val canonicalSnapshot: ProductoSnapshot? = null,
    @SerialName("proposed_snapshot") val proposedSnapshot: ProductoSnapshot? = null,
    val estado: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("client_created_at") val clientCreatedAt: String = "",
) {
    fun toRemoto(): ConflictoRemoto = ConflictoRemoto(
        id = id,
        operationId = operationId,
        aggregateId = aggregateId,
        action = action,
        baseRevision = baseRevision,
        canonicalRevision = canonicalRevision,
        canonical = canonicalSnapshot?.takeIf { !it.esArchivado }?.toRemoto(),
        proposed = proposedSnapshot?.takeIf { action != "archivar" && !it.esArchivado }?.toRemoto(),
        estado = estado,
        clientCreatedAt = clientCreatedAt,
    )
}

/** Resultado de `GET /sync/conflictos`. */
sealed interface ResultadoConflictos {
    data class Lista(val conflictos: List<ConflictoRemoto>) : ResultadoConflictos
    data object EstablecimientoFantasma : ResultadoConflictos
    data object Error : ResultadoConflictos
}

/** Resultado de `POST /sync/conflictos/{id}/resolver`. */
sealed interface ResultadoResolucion {
    data object Resuelta : ResultadoResolucion
    data object Obsoleta : ResultadoResolucion          // 409 identity.resolucion_sync_obsoleta
    data object YaResuelta : ResultadoResolucion        // 409 identity.conflicto_sync_ya_resuelto
    data object EstablecimientoFantasma : ResultadoResolucion
    data object Error : ResultadoResolucion
}

/** Notificación de `GET /notificaciones` (bandeja durable del negocio). */
@Serializable
data class NotificacionNegocioDto(
    val id: String = "",
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    @SerialName("conflicto_id") val conflictoId: String? = null,
    val tipo: String = "",
    val titulo: String = "",
    val mensaje: String = "",
    val payload: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("read_at") val readAt: String? = null,
) {
    fun toRemoto(): NotificacionRemoto = NotificacionRemoto(
        id = id,
        conflictoId = conflictoId,
        tipo = tipo,
        titulo = titulo,
        mensaje = mensaje,
        deepLink = payload["deep_link"],
        leida = readAt != null,
    )
}

/** Resultado de `GET /notificaciones`. */
sealed interface ResultadoNotificaciones {
    data class Lista(val notificaciones: List<NotificacionRemoto>) : ResultadoNotificaciones
    data object EstablecimientoFantasma : ResultadoNotificaciones
    data object Error : ResultadoNotificaciones
}

/** Resultado de `POST /notificaciones/{id}/leer`. */
sealed interface ResultadoMarcarLeida {
    data object Leida : ResultadoMarcarLeida
    data object NoEncontrada : ResultadoMarcarLeida        // 404 identity.notificacion_no_encontrada
    data object EstablecimientoFantasma : ResultadoMarcarLeida
    data object Error : ResultadoMarcarLeida
}

@Serializable
data class LayoutUpdateRequest(
    val salas: List<Sala>,
    val mesas: List<Mesa>,
    val zonas: List<Zona> = emptyList(),
)

@Serializable
data class IdentityLayout(
    @SerialName("establecimiento_id") val establecimientoId: String = "",
    val version: Int = 0,
    val salas: List<Sala> = emptyList(),
    val mesas: List<Mesa> = emptyList(),
    val zonas: List<Zona> = emptyList(),
)

/** Snapshot del layout que Bar envía/recupera de Identity (documento JSONB opaco). */
@Serializable
data class LayoutSnapshot(
    val salas: List<Sala>,
    val mesas: List<Mesa>,
    val zonas: List<Zona> = emptyList(),
)

/**
 * Enlace público del establecimiento (web o carta). `url_publica` la construye
 * Identity con `WEB_NEGOCIO_URL_BASE`; Bar solo la consume y muestra, nunca
 * concatena dominios ni rutas. `tipo=ficha_negocio` es alias legado de `web`.
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

/** Sección de la web pública con fondo propio (valores canónicos de Identity #140). */
enum class FondoSeccion(val apiValor: String) {
    INICIO("inicio"),
    HORARIO("horario"),
    CARTA("carta"),
    EQUIPO("equipo"),
    CONTACTO("contacto");

    companion object {
        /** Todas las secciones en orden canónico (mismo orden que el catálogo de Identity). */
        val TODAS: List<FondoSeccion> = entries
    }
}

/** Miniatura de un fondo Estate del catálogo (`GET .../fondos/catalogo`). */
@Serializable
data class CatalogoFondoItem(
    val id: String = "",
    val seccion: String = "",
    val url: String = "",
)

/** Slot de fondo resuelto (gestión): `fuente` = catalogo | upload | hero. */
@Serializable
data class FondoAsignado(
    val fuente: String = "",
    val id: String? = null,
    val url: String = "",
)

/** Asignación actual de fondos por sección (`GET .../fondos`). */
@Serializable
data class FondosAsignadosResponse(
    val inicio: FondoAsignado = FondoAsignado(),
    val horario: FondoAsignado = FondoAsignado(),
    val carta: FondoAsignado = FondoAsignado(),
    val equipo: FondoAsignado = FondoAsignado(),
    val contacto: FondoAsignado = FondoAsignado(),
) {
    /** Fondo asignado a la sección [seccion], o vacío si no llega. */
    fun de(seccion: FondoSeccion): FondoAsignado = when (seccion) {
        FondoSeccion.INICIO -> inicio
        FondoSeccion.HORARIO -> horario
        FondoSeccion.CARTA -> carta
        FondoSeccion.EQUIPO -> equipo
        FondoSeccion.CONTACTO -> contacto
    }
}

/**
 * Cuerpo del PUT parcial de fondos. `{slot: {fuente:"catalogo", id}}` asigna
 * catálogo; `{slot: null}` vuelve al default Estate. Los slots no incluidos se
 * ignoran en Identity, así que el body lleva **solo** el slot que cambia.
 */
internal fun fondoUpdateBody(slot: FondoSeccion, catalogoId: String?): String {
    val nombre = slot.apiValor
    return if (catalogoId == null) {
        """{"$nombre": null}"""
    } else {
        """{"$nombre": {"fuente": "catalogo", "id": "$catalogoId"}}"""
    }
}

/**
 * Cliente HTTP del servicio Identity **negocio/establecimientos** (v0.2). Config
 * (URL + token + UUID del establecimiento) en memoria (v0.1): se pierde al reiniciar
 * la app. Si no está configurado, los métodos devuelven null/false y Bar sigue con su
 * lista local. Las operaciones «de camarero» (buscar por email, alta por QR) están
 * proxied internamente por este servicio; Bar no habla con el servicio camareros.
 */
object IdentityNegocioClient {

    /** URL por defecto del servicio negocio en el VPS (staging/producción, HTTPS). El
     *  usuario de Bar no configura esta URL (config de entorno). En dev local con Docker
     *  se puede apuntar al host con `IdentityNegocioClient.configurar(...)`. */
    const val DEFAULT_BASE_URL: String = "https://negocio.siberia.solutions"

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

    /**
     * Desconecta la **sesión** (token/UUID/cuenta) conservando `baseUrl` (config
     * estática del proceso: `DEFAULT_BASE_URL` o el valor de `configurar(...)` de
     * dev/Docker). Es el método correcto para un «logout técnico» — p. ej. el 401
     * de la revalidación (#96) o el cierre de sesión explícito — porque el siguiente
     * `loginNegocio` necesita `baseUrl` (si es null, `IdentityHttp` devuelve -1).
     * [desconectar] (que anula también `baseUrl`) solo debe usarse si se quiere
     * desmontar el cliente por completo (tests/dev).
     */
    fun desconectarConservandoBaseUrl() {
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
     * Resultado de revalidar el token guardado contra el VPS:
     * - [OK] → el server acepta el token (200): la cuenta sigue válida, se renueva +7 días.
     * - [REVOCADA] → 401: token no válido (revocada/borrada/ban). La sesión local se invalida.
     * - [SIN_RED] → fallo de red/indeterminado: no se puede decidir; se deja caducar sola.
     */
    enum class RevalidacionResultado { OK, REVOCADA, SIN_RED }

    /** `GET /v1/auth/negocio/me` → comprueba que el token guardado sigue siendo válido
     *  contra Identity. 2xx → [RevalidacionResultado.OK]; 401 → [RevalidacionResultado.REVOCADA];
     *  fallo de red (-1) → [RevalidacionResultado.SIN_RED]. No cambia el perfil en memoria. */
    suspend fun revalidarToken(): RevalidacionResultado = withContext(Dispatchers.IO) {
        val (code, _) = IdentityHttp.request(baseUrl, "GET", "/v1/auth/negocio/me", token = negocioToken)
        when {
            code in 200..299 -> RevalidacionResultado.OK
            code == 401 -> RevalidacionResultado.REVOCADA
            else -> RevalidacionResultado.SIN_RED
        }
    }

    /** `POST /v1/auth/negocio/me/password` → rota la contraseña de la cuenta de negocio.
     *  Devuelve [CambioPasswordResult.OK] (2xx), [CambioPasswordResult.ACTUAL_INCORRECTA]
     *  (401) o [CambioPasswordResult.ERROR] (red/inesperado). Mantiene la sesión. */
    suspend fun cambiarPassword(actual: String, nueva: String): CambioPasswordResult = withContext(Dispatchers.IO) {
        val body = LanJson.encodeToString(
            CambioPasswordRequest(passwordActual = actual, passwordNueva = nueva)
        )
        val (code, _) = IdentityHttp.request(baseUrl, "POST", "/v1/auth/negocio/me/password", body = body, token = negocioToken)
        when (code) {
            in 200..299 -> CambioPasswordResult.OK
            401 -> CambioPasswordResult.ACTUAL_INCORRECTA
            else -> CambioPasswordResult.ERROR
        }
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

    /** `GET /v1/establecimientos/{id}/camareros/directorio?q=&limit=` — directorio sin email. */
    suspend fun directorioCamareros(q: String? = null, limit: Int = 100): List<IdentityCamareroDirectorio> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val params = mutableListOf("limit=$limit")
        q?.trim()?.takeIf { it.isNotEmpty() }?.let { params.add(0, "q=${URLEncoder.encode(it, "UTF-8")}") }
        val path = "/v1/establecimientos/$id/camareros/directorio?${params.joinToString("&")}"
        val (code, text) = IdentityHttp.request(baseUrl, "GET", path, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<List<IdentityCamareroDirectorio>>(text) }.getOrNull().orEmpty() else emptyList()
    }

    /** `POST /v1/establecimientos/{id}/invitaciones` → crea la invitación y envía el email. */
    suspend fun crearInvitacion(email: String, rol: String = "staff"): IdentityInvitacion? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = """{"email":"$email","rol":"$rol"}"""
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/invitaciones", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityInvitacion>(text) }.getOrNull() else null
    }

    /** `POST /v1/establecimientos/{id}/invitaciones` por `camarero_id` (flujo directorio). */
    suspend fun crearInvitacionPorCamarero(camareroId: String, rol: String = "staff"): IdentityInvitacion? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = """{"camarero_id":"$camareroId","rol":"$rol"}"""
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

    /** `PUT /v1/establecimientos/{id}/layout` — respaldo del layout (salas + mesas + zonas). */
    suspend fun guardarLayout(salas: List<Sala>, mesas: List<Mesa>, zonas: List<Zona> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        val body = LanJson.encodeToString(LayoutUpdateRequest(salas = salas, mesas = mesas, zonas = zonas))
        IdentityHttp.request(baseUrl, "PUT", "/v1/establecimientos/$id/layout", body = body, token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/layout` — recupera el layout respaldado, o null. */
    suspend fun obtenerLayout(): LayoutSnapshot? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/layout", token = negocioToken)
        if (code !in 200..299) return@withContext null
        runCatching { LanJson.decodeFromString<IdentityLayout>(text) }
            .getOrNull()?.let { LayoutSnapshot(salas = it.salas, mesas = it.mesas, zonas = it.zonas) }
    }

    // ── Enlaces públicos del establecimiento (web | carta) ───────────────────

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

    // ── Perfil público y web del local (`perfil-web`, hero, galería, horario) ─

    /** `GET /v1/establecimientos/{id}/perfil-web`. */
    suspend fun obtenerPerfilWeb(): IdentityPerfilWeb? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/perfil-web", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityPerfilWeb>(text) }.getOrNull() else null
    }

    /** `PATCH /v1/establecimientos/{id}/perfil-web` — parcial; null no se envía. */
    suspend fun editarPerfilWeb(update: IdentityPerfilWebUpdate): IdentityPerfilWeb? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = LanJson.encodeToString(update)
        val (code, text) = IdentityHttp.request(baseUrl, "PATCH", "/v1/establecimientos/$id/perfil-web", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityPerfilWeb>(text) }.getOrNull() else null
    }

    /** `POST /v1/establecimientos/{id}/hero` (multipart, campo `hero`). */
    suspend fun subirHero(bytes: ByteArray, mimetype: String): IdentityPerfilWeb? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val ok = IdentityHttp.uploadMultipart(baseUrl, "/v1/establecimientos/$id/hero", "hero", "hero.webp", bytes, mimetype, negocioToken)
        if (ok) obtenerPerfilWeb() else null
    }

    /** `GET /v1/establecimientos/{id}/hero` → bytes de la portada, o null. */
    suspend fun obtenerHero(): ByteArray? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, bytes) = IdentityHttp.requestBytes(baseUrl, "GET", "/v1/establecimientos/$id/hero", negocioToken)
        if (code in 200..299) bytes else null
    }

    /** `DELETE /v1/establecimientos/{id}/hero`. */
    suspend fun borrarHero(): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "DELETE", "/v1/establecimientos/$id/hero", token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/galeria`. */
    suspend fun listarGaleria(): List<IdentityImagenGaleria> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/galeria", token = negocioToken)
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<IdentityImagenGaleria>>(text) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
    }

    /** `POST /v1/establecimientos/{id}/galeria` (multipart, campo `imagen`). */
    suspend fun subirGaleria(bytes: ByteArray, mimetype: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.uploadMultipart(baseUrl, "/v1/establecimientos/$id/galeria", "imagen", "foto.webp", bytes, mimetype, negocioToken)
    }

    /** `GET /v1/establecimientos/{id}/galeria/{imagenId}` → bytes, o null. */
    suspend fun obtenerImagenGaleria(imagenId: String): ByteArray? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, bytes) = IdentityHttp.requestBytes(
            baseUrl, "GET", "/v1/establecimientos/$id/galeria/$imagenId", negocioToken,
        )
        if (code in 200..299) bytes else null
    }

    /** `DELETE /v1/establecimientos/{id}/galeria/{imagenId}`. */
    suspend fun borrarImagenGaleria(imagenId: String): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        IdentityHttp.request(baseUrl, "DELETE", "/v1/establecimientos/$id/galeria/$imagenId", token = negocioToken).first in 200..299
    }

    /** `GET /v1/establecimientos/{id}/horario`. */
    suspend fun obtenerHorario(): IdentityHorarioResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/horario", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityHorarioResponse>(text) }.getOrNull() else null
    }

    /** `PATCH /v1/establecimientos/{id}/horario` — reemplazo completo de `dias`. */
    suspend fun guardarHorario(dias: List<IdentityHorarioDia>): IdentityHorarioResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val body = LanJson.encodeToString(IdentityHorarioUpdate(dias))
        val (code, text) = IdentityHttp.request(baseUrl, "PATCH", "/v1/establecimientos/$id/horario", body = body, token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<IdentityHorarioResponse>(text) }.getOrNull() else null
    }

    // ── Jornada CFC (admisión de pedidos de cliente) ──────────────────────────

    /**
     * `POST /v1/establecimientos/{id}/cfc/jornada/abrir` → abre la admisión CFC.
     * Idempotente en el server: si ya hay jornada abierta, refresca el heartbeat
     * y la devuelve. Devuelve la jornada (con `bar_en_linea`) o null si falla.
     */
    suspend fun abrirJornadaCfc(): JornadaCfcResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/cfc/jornada/abrir", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<JornadaCfcResponse>(text) }.getOrNull() else null
    }

    /**
     * `POST /v1/establecimientos/{id}/cfc/jornada/cerrar` → cierra la admisión CFC.
     * Devuelve true si cerró (2xx) o si ya estaba cerrada (409 = sin jornada abierta).
     */
    suspend fun cerrarJornadaCfc(): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        val (code, _) = IdentityHttp.request(baseUrl, "POST", "/v1/establecimientos/$id/cfc/jornada/cerrar", token = negocioToken)
        code in 200..299 || code == 409
    }

    /**
     * `PUT /v1/establecimientos/{id}/cfc/heartbeat` → mantiene `bar_en_linea`.
     * Devuelve la jornada si el heartbeat se aceptó (2xx); null si no hay jornada
     * (409), no hay sesión o la red falla.
     */
    suspend fun heartbeatCfc(): JornadaCfcResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(baseUrl, "PUT", "/v1/establecimientos/$id/cfc/heartbeat", token = negocioToken)
        if (code in 200..299) runCatching { LanJson.decodeFromString<JornadaCfcResponse>(text) }.getOrNull() else null
    }

    // ── Mesas CFC (conjunto público por mesa) ────────────────────────────

    /**
     * `PUT /v1/establecimientos/{id}/mesas-cfc` → envía el conjunto completo
     * de mesas públicas. Identity emite tokens nuevos, actualiza etiquetas y
     * revoca las ausentes. Devuelve la lista resultante con `url_publica`.
     */
    suspend fun sincronizarMesasCfc(mesas: List<MesaCfcItem>): List<MesaCfcResponse> =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext emptyList()
            val body = LanJson.encodeToString(mapOf("mesas" to mesas))
            val (code, text) = IdentityHttp.request(
                baseUrl, "PUT", "/v1/establecimientos/$id/mesas-cfc",
                body = body, token = negocioToken,
            )
            if (code in 200..299) {
                runCatching { LanJson.decodeFromString<List<MesaCfcResponse>>(text) }
                    .getOrDefault(emptyList())
            } else emptyList()
        }

    /**
     * `GET /v1/establecimientos/{id}/mesas-cfc` → lista de mesas CFC activas
     * con `url_publica`. Devuelve vacío si no hay sesión o la petición falla.
     */
    suspend fun listarMesasCfc(): List<MesaCfcResponse> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val (code, text) = IdentityHttp.request(
            baseUrl, "GET", "/v1/establecimientos/$id/mesas-cfc", token = negocioToken,
        )
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<MesaCfcResponse>>(text) }
                .getOrDefault(emptyList())
        } else emptyList()
    }

    /**
     * `POST /v1/establecimientos/{id}/mesas-cfc/{mesaUuid}/rotar` → rota el
     * token de una mesa (el QR impreso viejo deja de valer con 410). Devuelve
     * la nueva entrada con `url_publica` o null si falla / no existe.
     */
    suspend fun rotarMesaCfc(mesaUuid: String): MesaCfcResponse? =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext null
            val (code, text) = IdentityHttp.request(
                baseUrl, "POST",
                "/v1/establecimientos/$id/mesas-cfc/$mesaUuid/rotar",
                token = negocioToken,
            )
            if (code in 200..299) {
                runCatching { LanJson.decodeFromString<MesaCfcResponse>(text) }.getOrNull()
            } else null
        }

    // ── Libro de oficio (productor: estadísticas de servicio) ────────────────

    /**
     * `POST /v1/negocio/estadisticas/servicio` → registra un evento de servicio
     * («ronda servida») en el libro de oficio del camarero. Idempotente por
     * [eventoId]: reintentar tras un timeout no duplica. Devuelve true si Identity
     * aceptó (2xx); false si no hay sesión de negocio o la petición falló
     * (el proyector deja el evento en la cola y reintenta).
     */
    suspend fun registrarServicio(
        camareroId: String,
        eventoId: String,
        tipo: String = "ronda_servida",
        cantidad: Int = 1,
    ): Boolean = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext false
        val body = LanJson.encodeToString(
            ServicioRegistroRequest(
                establecimientoId = id,
                camareroId = camareroId,
                eventoId = eventoId,
                tipo = tipo,
                cantidad = cantidad,
            )
        )
        IdentityHttp.request(baseUrl, "POST", "/v1/negocio/estadisticas/servicio", body = body, token = negocioToken).first in 200..299
    }

    // ── Sync de catálogo (outbox → Identity) ────────────────────────────────

    /** Id de dispositivo estable para el `device_id` del contrato. v0.1: un nodo = un puesto. */
    const val DEVICE_ID: String = "bar-tablet-01"

    /**
     * `POST /v1/establecimientos/{id}/sync/operaciones` → entrega una operación
     * del outbox de catálogo. Idempotente por `operation_id`: reintentar (p. ej.
     * tras un timeout) no duplica ni cambia el resultado. Devuelve [Error] si no
     * hay sesión de negocio, la red falla o la respuesta es ilegible (el proyector
     * la deja en la cola y reintenta en el siguiente ciclo).
     */
    suspend fun enviarOperacionCatalogo(op: OperacionCatalogo): ResultadoSyncCatalogo = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext ResultadoSyncCatalogo.Error
        // `archivar` no lleva payload (el server espera null); crear/actualizar exigen el payload completo.
        val payload = if (op.action == "archivar" || op.nombre == null) {
            null
        } else {
            ProductoPayload(
                nombre = op.nombre,
                categoria = op.categoria.orEmpty(),
                descripcion = op.descripcion,
                destino = op.destino ?: "barra",
                precioCentimos = op.precioCentimos ?: 0,
                moneda = op.moneda,
                disponible = op.disponible,
            )
        }
        val body = LanJson.encodeToString(
            OperacionSyncRequest(
                operationId = op.operationId,
                deviceId = DEVICE_ID,
                aggregateType = "producto",
                aggregateId = op.aggregateId,
                action = op.action,
                baseRevision = op.baseRevision,
                baseSnapshot = null,
                payload = payload,
                clientCreatedAt = epochUtcIso(op.creadaEn),
            )
        )
        val (code, text) = IdentityHttp.request(
            baseUrl, "POST", "/v1/establecimientos/$id/sync/operaciones",
            body = body, token = negocioToken,
        )
        if (esEstablecimientoFantasma(code, text)) return@withContext ResultadoSyncCatalogo.EstablecimientoFantasma
        if (code !in 200..299) return@withContext ResultadoSyncCatalogo.Error
        val resp = runCatching { LanJson.decodeFromString<OperacionSyncResponse>(text) }.getOrNull()
            ?: return@withContext ResultadoSyncCatalogo.Error
        when (resp.estado) {
            "aplicada" -> ResultadoSyncCatalogo.Aplicada(resp.resultSnapshot?.revision)
            "conflicto" -> ResultadoSyncCatalogo.Conflicto
            "rechazada" -> ResultadoSyncCatalogo.Rechazada
            else -> ResultadoSyncCatalogo.Error
        }
    }

    /**
     * `GET /v1/establecimientos/{id}/catalogo` → snapshot completo del catálogo
     * canónico (para decidir el seed inicial y la divergencia en el primer contacto).
     */
    suspend fun obtenerCatalogoRemoto(): ResultadoPullCatalogo = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext ResultadoPullCatalogo.Error
        val (code, text) = IdentityHttp.request(baseUrl, "GET", "/v1/establecimientos/$id/catalogo", token = negocioToken)
        if (esEstablecimientoFantasma(code, text)) return@withContext ResultadoPullCatalogo.EstablecimientoFantasma
        if (code !in 200..299) return@withContext ResultadoPullCatalogo.Error
        val resp = runCatching { LanJson.decodeFromString<CatalogoResponseDto>(text) }.getOrNull()
            ?: return@withContext ResultadoPullCatalogo.Error
        ResultadoPullCatalogo.Snapshot(resp.productos.map { it.toRemoto() }, resp.revision)
    }

    /**
     * `GET /v1/establecimientos/{id}/sync/cambios?desde=N` → deltas aplicados desde
     * la revisión global [desde]. Devuelve los cambios y la revisión global actual.
     */
    suspend fun obtenerCambiosRemoto(desde: Int): ResultadoPullCatalogo = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext ResultadoPullCatalogo.Error
        val (code, text) = IdentityHttp.request(
            baseUrl, "GET", "/v1/establecimientos/$id/sync/cambios?desde=$desde", token = negocioToken,
        )
        if (esEstablecimientoFantasma(code, text)) return@withContext ResultadoPullCatalogo.EstablecimientoFantasma
        if (code !in 200..299) return@withContext ResultadoPullCatalogo.Error
        val resp = runCatching { LanJson.decodeFromString<CambiosResponseDto>(text) }.getOrNull()
            ?: return@withContext ResultadoPullCatalogo.Error
        ResultadoPullCatalogo.Cambios(resp.cambios.map { it.toRemoto() }, resp.revisionActual)
    }

    // ── Conflictos de catálogo (superficie de resolución) ───────────────────

    /**
     * `GET /v1/establecimientos/{id}/sync/conflictos?estado=` → conflictos del
     * establecimiento. Por defecto lista solo los `pendiente` (los que exigen
     * decisión); el resto de estados los puede pedir la UI explícitamente.
     */
    suspend fun listarConflictos(estado: String = "pendiente"): ResultadoConflictos = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext ResultadoConflictos.Error
        val (code, text) = IdentityHttp.request(
            baseUrl, "GET", "/v1/establecimientos/$id/sync/conflictos?estado=$estado", token = negocioToken,
        )
        if (esEstablecimientoFantasma(code, text)) return@withContext ResultadoConflictos.EstablecimientoFantasma
        if (code !in 200..299) return@withContext ResultadoConflictos.Error
        val resp = runCatching { LanJson.decodeFromString<List<ConflictoSyncDto>>(text) }.getOrNull()
            ?: return@withContext ResultadoConflictos.Error
        ResultadoConflictos.Lista(resp.map { it.toRemoto() })
    }

    /**
     * `POST /v1/establecimientos/{id}/sync/conflictos/{id}/resolver` → acepta o
     * rechaza el conflicto. `expectedRevision` debe ser la revisión canónica que se
     * vio al listar; si el canónico se movió, el server responde 409 y el resultado
     * es [ResultadoResolucion.Obsoleta] (la UI refresca antes de decidir de nuevo).
     */
    suspend fun resolverConflicto(conflictoId: String, decision: String, expectedRevision: Int): ResultadoResolucion =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext ResultadoResolucion.Error
            val body = LanJson.encodeToString(
                ResolverConflictoRequest(decision = decision, expectedRevision = expectedRevision)
            )
            val (code, text) = IdentityHttp.request(
                baseUrl, "POST", "/v1/establecimientos/$id/sync/conflictos/$conflictoId/resolver",
                body = body, token = negocioToken,
            )
            mapearResultadoResolucion(code, text)
        }

    // ── Notificaciones de negocio (bandeja durable) ─────────────────────────

    /**
     * `GET /v1/establecimientos/{id}/notificaciones?solo_no_leidas=` → bandeja
     * durable de avisos del negocio. [soloNoLeidas] filtra las pendientes de
     * lectura (lo usa el badge de la campana del header); false devuelve todas
     * ordenadas por fecha descendente.
     */
    suspend fun listarNotificaciones(soloNoLeidas: Boolean = false): ResultadoNotificaciones =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext ResultadoNotificaciones.Error
            val (code, text) = IdentityHttp.request(
                baseUrl, "GET", "/v1/establecimientos/$id/notificaciones?solo_no_leidas=$soloNoLeidas",
                token = negocioToken,
            )
            if (esEstablecimientoFantasma(code, text)) return@withContext ResultadoNotificaciones.EstablecimientoFantasma
            if (code !in 200..299) return@withContext ResultadoNotificaciones.Error
            val resp = runCatching { LanJson.decodeFromString<List<NotificacionNegocioDto>>(text) }.getOrNull()
                ?: return@withContext ResultadoNotificaciones.Error
            ResultadoNotificaciones.Lista(resp.map { it.toRemoto() })
        }

    /**
     * `POST /v1/establecimientos/{id}/notificaciones/{id}/leer` → marca leída
     * (idempotente en server). 404 `identity.notificacion_no_encontrada` →
     * [ResultadoMarcarLeida.NoEncontrada] (ya no existe; el contador se corrige
     * en el siguiente pull).
     */
    suspend fun marcarNotificacionLeida(notificacionId: String): ResultadoMarcarLeida =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext ResultadoMarcarLeida.Error
            val (code, text) = IdentityHttp.request(
                baseUrl, "POST", "/v1/establecimientos/$id/notificaciones/$notificacionId/leer",
                token = negocioToken,
            )
            mapearResultadoMarcarLeida(code, text)
        }

    // ── Fondos de la web pública (catálogo Estate o upload por sección) ───

    /** `GET /v1/establecimientos/{id}/fondos/catalogo` → miniaturas Estate por sección. */
    suspend fun listarCatalogoFondos(): List<CatalogoFondoItem> = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext emptyList()
        val (code, text) = IdentityHttp.request(
            baseUrl, "GET", "/v1/establecimientos/$id/fondos/catalogo", token = negocioToken,
        )
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<List<CatalogoFondoItem>>(text) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
    }

    /** `GET /v1/establecimientos/{id}/fondos` → asignación actual por sección. */
    suspend fun obtenerFondos(): FondosAsignadosResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(
            baseUrl, "GET", "/v1/establecimientos/$id/fondos", token = negocioToken,
        )
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<FondosAsignadosResponse>(text) }.getOrNull()
        } else {
            null
        }
    }

    /**
     * `PUT /v1/establecimientos/{id}/fondos` → asignación parcial. [catalogoId]
     * asigna ese fondo de catálogo; `null` vuelve al default Estate. Devuelve la
     * asignación resultante (o null si falla).
     */
    suspend fun actualizarFondos(seccion: FondoSeccion, catalogoId: String?): FondosAsignadosResponse? =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext null
            val body = fondoUpdateBody(seccion, catalogoId)
            val (code, text) = IdentityHttp.request(
                baseUrl, "PUT", "/v1/establecimientos/$id/fondos", body = body, token = negocioToken,
            )
            if (code in 200..299) {
                runCatching { LanJson.decodeFromString<FondosAsignadosResponse>(text) }.getOrNull()
            } else {
                null
            }
        }

    /** `POST /v1/establecimientos/{id}/fondos/{slot}` (multipart, campo `imagen`).
     *  Sustituye el catálogo del slot por el upload; Identity normaliza a WebP. */
    suspend fun subirFondo(seccion: FondoSeccion, bytes: ByteArray, mimetype: String): FondosAsignadosResponse? =
        withContext(Dispatchers.IO) {
            val id = establecimientoUuid ?: return@withContext null
            val ok = IdentityHttp.uploadMultipart(
                baseUrl, "/v1/establecimientos/$id/fondos/${seccion.apiValor}",
                "imagen", "fondo.webp", bytes, mimetype, negocioToken,
            )
            if (ok) obtenerFondos() else null
        }

    /** `DELETE /v1/establecimientos/{id}/fondos/{slot}` → vuelve al default Estate. */
    suspend fun borrarFondo(seccion: FondoSeccion): FondosAsignadosResponse? = withContext(Dispatchers.IO) {
        val id = establecimientoUuid ?: return@withContext null
        val (code, text) = IdentityHttp.request(
            baseUrl, "DELETE", "/v1/establecimientos/$id/fondos/${seccion.apiValor}", token = negocioToken,
        )
        if (code in 200..299) {
            runCatching { LanJson.decodeFromString<FondosAsignadosResponse>(text) }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Bytes de la miniatura de un fondo: si [url] es absoluta (catálogo vía
     * `WEB_NEGOCIO_URL_BASE`) GET directo sin token; si es relativa (upload propio
     * del establecimiento) GET con token del servicio negocio.
     */
    suspend fun obtenerBytesFondo(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            val (code, bytes) = IdentityHttp.requestBytesUrl(url)
            if (code in 200..299) bytes else null
        } else {
            val (code, bytes) = IdentityHttp.requestBytes(baseUrl, "GET", url, negocioToken)
            if (code in 200..299) bytes else null
        }
    }
}
