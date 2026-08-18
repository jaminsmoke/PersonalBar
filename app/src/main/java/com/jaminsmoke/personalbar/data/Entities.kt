package com.jaminsmoke.personalbar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Cuenta del establecimiento (negocio/local). Fuente de verdad en Bar.
 * v0.1: un nodo = un establecimiento; Identity aporta la identidad externa.
 * Persistida en Room (tabla singleton, 1 fila).
 */
@Serializable
@Entity(tableName = "establecimientos")
data class Establecimiento(
    @PrimaryKey val idEstable: String,
    val nombre: String,
)

/**
 * Sala de servicio del mapa (barra, interior, terraza…). Primer nivel del layout;
 * las mesas cuelgan de una sala.
 */
@Serializable
@Entity(tableName = "salas")
data class Sala(
    @PrimaryKey val id: String,
    val nombre: String,
    val orden: Int,
)

/**
 * Mesa canónica del nodo. Identidad local [id] (in-memory, como `Sala.id`);
 * identidad de red es [idZona] (prefijo de sala + [indiceZona]), nunca el id local.
 */
@Serializable
@Entity(tableName = "mesas")
data class Mesa(
    @PrimaryKey val id: String,
    val salaId: String,
    val indiceZona: Int,
    val numero: Int = 0,
    val alias: String? = null,
    val forma: MesaForma = MesaForma.CUADRADA,
    val capacidad: Int = 4,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val girada: Boolean = false,
    val bloqueada: Boolean = false,
    val reservaActivaId: String? = null,
) {
    /** ID dentro de la sala, p. ej. "B1" para Barra 1. Requiere el nombre de la sala. */
    fun idZona(nombreSala: String): String = "${zonaPrefijo(nombreSala)}$indiceZona"

    /** Nombre visible: alias del usuario si existe; si no, el ID de zona (B1, T2…). */
    fun nombreVisible(nombreSala: String): String = alias ?: idZona(nombreSala)
}

/** Producto del catálogo canónico del nodo. La categoría deriva el destino. */
@Serializable
@Entity(tableName = "productos")
data class Producto(
    @PrimaryKey val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double = 0.0,
    val disponible: Boolean = true,
)

/** Línea de una ronda/ticket: producto + cantidad. */
@Serializable
data class Linea(
    val productoId: String,
    val nombreProducto: String,
    val cantidad: Int,
    val estado: LineaEstado = LineaEstado.PENDIENTE,
)

/**
 * Ticket de expo: una ronda partida por destino. Preparado/recogido es por ticket
 * (no por mesa): las cañas pueden estar preparadas sin la pizza. [preparadoPor]
 * es el camarero (lista blanca) que elaboró el ticket, simétrico a `Ronda.camarero`.
 * Las líneas se persisten como JSON (TypeConverter).
 */
@Serializable
@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey val id: String,
    val rondaId: String,
    val destino: Destino,
    val estado: TicketEstado = TicketEstado.PENDIENTE,
    val preparadoPor: String? = null,
    /**
     * Id de cola visible/hablable por destino en el turno («Cola 1 Bebida»).
     * Monótono: al recoger Cola 1, Cola 2 no pasa a 1 (ancla estable para voz/tacto).
     */
    val numeroCola: Int = 0,
    val lineas: List<Linea>,
)

/**
 * Ronda enviada por Commander: la unidad que Bar parte en tickets BARRA/COCINA.
 * Las líneas se persisten como JSON (TypeConverter).
 */
@Serializable
@Entity(tableName = "rondas")
data class Ronda(
    @PrimaryKey val id: String,
    val mesaId: String,          // idZona ("T3")
    val numero: Int,             // número de ronda de la mesa
    val camarero: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val lineas: List<Linea>,
)

/** Reserva (hold comercial) sobre una mesa. [mesaId] referencia [Mesa.id] local. */
@Serializable
@Entity(tableName = "reservas")
data class Reserva(
    @PrimaryKey val id: String,
    val mesaId: String,
    val nombre: String,
    val paraEpoch: Long? = null,
    val creadaEn: Long = System.currentTimeMillis(),
    val canceladaEn: Long? = null,
)

/** Estado de una invitación por email (mirror local; la aceptación vive en Identity Web). */
@Serializable
enum class InvitacionEstado { PENDIENTE, ACEPTADA, REVOCADA, RECHAZADA, EXPIRADA }

/**
 * Estado desde el catálogo de Identity (`pendiente|aceptada|revocada|rechazada|expirada`);
 * null si el valor es desconocido (respuestas de versiones futuras).
 */
fun invitacionEstadoDesdeApi(valor: String?): InvitacionEstado? = when (valor) {
    "pendiente" -> InvitacionEstado.PENDIENTE
    "aceptada" -> InvitacionEstado.ACEPTADA
    "revocada" -> InvitacionEstado.REVOCADA
    "rechazada" -> InvitacionEstado.RECHAZADA
    "expirada" -> InvitacionEstado.EXPIRADA
    else -> null
}

/**
 * Invitación por email creada desde Bar (registro local; la verdad vive en Identity).
 * La aceptación ocurre en Identity Web (magic-link), fuera de Bar.
 */
@Serializable
@Entity(tableName = "invitaciones")
data class Invitacion(
    @PrimaryKey val id: String,
    val email: String,
    val rol: String = "staff",
    val estado: InvitacionEstado = InvitacionEstado.PENDIENTE,
    val expiraEn: String? = null,
    val creadaEn: Long = System.currentTimeMillis(),
)

/** Configuración de la conexión con Identity (persistida en Room, tabla singleton). */
@Entity(tableName = "identity_config")
data class IdentityConfig(
    @PrimaryKey val id: String = "local",
    val conectado: Boolean = false,
    val baseUrl: String? = null,
    val establecimientoUuid: String? = null,
    val error: String? = null,
)

/** Tipo de establecimiento (categoría del negocio), pedido en el registro. Catálogo 1:1 con Identity. */
@Serializable
enum class TipoEstablecimiento { BAR, RESTAURANTE, CAFETERIA, PUB, COPAS }

/** Valor canónico del catálogo de Identity para un tipo (snake_case, sin tildes). */
fun TipoEstablecimiento.apiValor(): String = when (this) {
    TipoEstablecimiento.BAR -> "bar"
    TipoEstablecimiento.RESTAURANTE -> "restaurante"
    TipoEstablecimiento.CAFETERIA -> "cafeteria"
    TipoEstablecimiento.PUB -> "pub"
    TipoEstablecimiento.COPAS -> "copas"
}

/** Tipo desde el valor del catálogo de Identity; null si no coincide. */
fun tipoDesdeApi(valor: String?): TipoEstablecimiento? = when (valor) {
    "bar" -> TipoEstablecimiento.BAR
    "restaurante" -> TipoEstablecimiento.RESTAURANTE
    "cafeteria" -> TipoEstablecimiento.CAFETERIA
    "pub" -> TipoEstablecimiento.PUB
    "copas" -> TipoEstablecimiento.COPAS
    else -> null
}

/**
 * Sesión de la cuenta de negocio en el puesto de Bar (tabla singleton).
 * Se persiste solo si el usuario marcó «Recuérdame» en el login; si no,
 * vive en memoria y se pierde al reiniciar. [tipo] y [logoUrl] se
 * sincronizan contra Identity (fuente canónica del perfil de negocio).
 */
@Entity(tableName = "sesion_negocio")
data class SesionNegocio(
    @PrimaryKey val id: String = "local",
    val token: String? = null,
    val email: String? = null,
    val nombreMostrar: String? = null,
    val establecimientoUuid: String? = null,
    val tipo: TipoEstablecimiento? = null,
    val logoUrl: String? = null,
    /** Procedencia canónica de Identity (`real|test|demo`) para diagnóstico offline. */
    val dataOrigin: String? = null,
    /**
     * Hasta cuándo es válida la sesión local (epoch ms) para operar offline.
     * Se renueva (+7 días) en cada contacto exitoso con el VPS (login o
     * revalidación). `null` = sin validez conocida (sesiones previas a v10:
     * no operan offline hasta el primer contacto); `0` = inválida (401 del
     * server: revocada/borrada). Migración Room v10.
     */
    val validaHasta: Long? = null,
)

/**
 * Clave pública Ed25519 de Identity para verificar QRs `phid1` offline.
 * Tabla singleton (PK fija «local»); se refresca al conectar a Identity.
 * [publicKey] es la clave base64url (32 bytes) que devuelve `GET /v1/keys/qr`.
 */
@Entity(tableName = "qr_keys")
data class QrKey(
    @PrimaryKey val id: String = "local",
    val keyId: String = "",
    val publicKey: String = "",
    val algorithm: String = "Ed25519",
)

/**
 * Alta de camarero hecha offline (verificada localmente) pendiente de subir a
 * Identity (membresia) cuando vuelva la conexión. [payload] es el QR `phid1`
 * completo para reenviar a `POST /miembros/qr`.
 */
@Entity(tableName = "altas_pendientes")
data class AltaPendiente(
    @PrimaryKey val camareroId: String,
    val payload: String,
    val creadaEn: Long = System.currentTimeMillis(),
)

/** Rol de un camarero en el establecimiento. */
@Serializable
enum class RolCamarero { DUENO, STAFF }

/** Estado de un camarero en la lista blanca. */
@Serializable
enum class CamareroEstado { ACTIVA, REVOCADA }

/**
 * Intervalo de jornada local (libro de oficio, lado Bar).
 *
 * El productor abre la jornada al conceder sesión de trabajo ([BarRepository.iniciarSesion])
 * y la cierra al cortar (fin de jornada, timeout o revocación). Es **local**: el libro
 * canónico vive en Identity (camareros `:8080`); Bar lo guarda para no perder el
 * intervalo si se reinicia y para poder proyectarlo luego.
 */
@Entity(tableName = "jornadas")
data class JornadaLocal(
    @PrimaryKey val id: String,
    val camareroId: String,
    val inicio: Long,
    val fin: Long? = null,
)

/**
 * Evento de servicio pendiente de subir a Identity (cola persistente del libro de oficio).
 *
 * [eventoId] es la PK y la clave de idempotencia del server (`servicio:{rondaId}`):
 * reintentar no duplica. El proyector drena la cola cuando la cuenta de negocio está
 * vinculada; éxito borra la fila, fallo la deja para el siguiente intento.
 */
@Entity(tableName = "servicios_pendientes")
data class ServicioPendiente(
    @PrimaryKey val eventoId: String,
    val camareroId: String,
    val tipo: String,
    val cantidad: Int = 1,
    val creadaEn: Long = System.currentTimeMillis(),
)

/**
 * Camarero de la lista blanca del establecimiento (mirror de Identity).
 * La identidad canónica vive en Identity; Bar guarda a quién acepta en la LAN.
 * [id] es el `camarero_id` (UUID) de Identity, extraído del QR `phid1`.
 */
@Serializable
@Entity(tableName = "camareros")
data class Camarero(
    @PrimaryKey val id: String,
    val nombre: String? = null,
    val email: String? = null,
    val rol: RolCamarero = RolCamarero.STAFF,
    val estado: CamareroEstado = CamareroEstado.ACTIVA,
    val credencialId: String? = null,
    val altaEn: Long = System.currentTimeMillis(),
    /**
     * De servicio en el puesto de barra (varios a la vez; la cuenta se crea en
     * Commander, Bar solo la asigna al establecimiento). Migración Room v3.
     */
    val deServicio: Boolean = false,
    /**
     * Sesión de trabajo activa: Bar concedió la jornada (POST /v1/sesion/iniciar)
     * y aún no la cortó (fin de jornada, revocación o salida de la LAN por
     * heartbeat). Solo con esto true el camarero puede mandar comandas.
     * Distinto de [deServicio] (preparador en el puesto). Migración Room v8.
     */
    val sesionActiva: Boolean = false,
)
