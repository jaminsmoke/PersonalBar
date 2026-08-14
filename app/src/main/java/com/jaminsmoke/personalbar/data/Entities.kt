package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/**
 * Cuenta del establecimiento (negocio/local). Fuente de verdad en Bar.
 * v0.1: un nodo = un establecimiento; Identity (v0.2) aportará la identidad externa.
 */
@Serializable
data class Establecimiento(
    val idEstable: String,
    val nombre: String,
)

/**
 * Sala de servicio del mapa (barra, interior, terraza…). Primer nivel del layout;
 * las mesas cuelgan de una sala.
 */
@Serializable
data class Sala(
    val id: String,
    val nombre: String,
    val orden: Int,
)

/**
 * Mesa canónica del nodo. Identidad local [id] (in-memory, como `Sala.id`);
 * identidad de red es [idZona] (prefijo de sala + [indiceZona]), nunca el id local.
 */
@Serializable
data class Mesa(
    val id: String,
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
data class Producto(
    val id: String,
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
 */
@Serializable
data class Ticket(
    val id: String,
    val rondaId: String,
    val destino: Destino,
    val estado: TicketEstado = TicketEstado.PENDIENTE,
    val preparadoPor: String? = null,
    val lineas: List<Linea>,
)

/** Ronda enviada por Commander: la unidad que Bar parte en tickets BARRA/COCINA. */
@Serializable
data class Ronda(
    val id: String,
    val mesaId: String,          // idZona ("T3")
    val numero: Int,             // número de ronda de la mesa
    val camarero: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val lineas: List<Linea>,
)

/** Reserva (hold comercial) sobre una mesa. [mesaId] referencia [Mesa.id] local. */
@Serializable
data class Reserva(
    val id: String,
    val mesaId: String,
    val nombre: String,
    val paraEpoch: Long? = null,
    val creadaEn: Long = System.currentTimeMillis(),
    val canceladaEn: Long? = null,
)

/** Estado de una invitación por email (mirror local; la aceptación vive en Identity Web). */
@Serializable
enum class InvitacionEstado { PENDIENTE, ACEPTADA, REVOCADA }

/**
 * Invitación por email creada desde Bar (registro local; la verdad vive en Identity).
 * La aceptación ocurre en Identity Web (magic-link), fuera de Bar.
 */
@Serializable
data class Invitacion(
    val id: String,
    val email: String,
    val rol: String = "staff",
    val estado: InvitacionEstado = InvitacionEstado.PENDIENTE,
    val expiraEn: String? = null,
    val creadaEn: Long = System.currentTimeMillis(),
)

/** Configuración de la conexión con Identity (v0.1 in-memory; se pierde al reiniciar). */
data class IdentityConfig(
    val conectado: Boolean = false,
    val baseUrl: String? = null,
    val establecimientoUuid: String? = null,
    val error: String? = null,
)

/** Rol de un camarero en el establecimiento. */
@Serializable
enum class RolCamarero { DUENO, STAFF }

/** Estado de un camarero en la lista blanca. */
@Serializable
enum class CamareroEstado { ACTIVA, REVOCADA }

/**
 * Camarero de la lista blanca del establecimiento (mirror de Identity).
 * La identidad canónica vive en Identity; Bar guarda a quién acepta en la LAN.
 * [id] es el `camarero_id` (UUID) de Identity, extraído del QR `phid1`.
 */
@Serializable
data class Camarero(
    val id: String,
    val nombre: String? = null,
    val email: String? = null,
    val rol: RolCamarero = RolCamarero.STAFF,
    val estado: CamareroEstado = CamareroEstado.ACTIVA,
    val credencialId: String? = null,
    val altaEn: Long = System.currentTimeMillis(),
)
