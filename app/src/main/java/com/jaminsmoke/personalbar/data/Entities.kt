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
 * Mesa canónica del nodo. La identidad en red es el idZona (sala + indiceZona),
 * no un id local autoincrementable: la misma mesa debe reconocerse igual en todos
 * los dispositivos de la sala.
 */
@Serializable
data class Mesa(
    val salaId: String,          // referencia a Sala.id
    val indiceZona: Int,
    val alias: String? = null,
    val forma: String = "CUADRADA",
    val capacidad: Int = 4,
    val posX: Float = 0f,
    val posY: Float = 0f,
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
 * Ticket de expo: una ronda partida por destino. Listo/servido es por ticket
 * (no por mesa): las cañas pueden estar listas sin la pizza.
 */
@Serializable
data class Ticket(
    val id: String,
    val rondaId: String,
    val destino: Destino,
    val estado: TicketEstado = TicketEstado.PENDIENTE,
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
