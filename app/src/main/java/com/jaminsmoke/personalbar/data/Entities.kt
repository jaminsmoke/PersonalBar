package com.jaminsmoke.personalbar.data

/**
 * Mesa canónica del nodo. La identidad en red es [idZona] (zona + indiceZona),
 * no un id local autoincrementable: la misma mesa debe reconocerse igual en todos
 * los dispositivos de la sala.
 */
data class Mesa(
    val zona: String,
    val indiceZona: Int,
    val alias: String? = null,
    val forma: String = "CUADRADA",
    val capacidad: Int = 4,
    val posX: Float = 0f,
    val posY: Float = 0f,
) {
    /** ID dentro de la zona, p. ej. "B1" para Barra 1. Identidad estable en red. */
    val idZona: String get() = "${zonaPrefijo(zona)}$indiceZona"

    /** Nombre visible: alias del usuario si existe; si no, el ID de zona (B1, T2…). */
    val nombreVisible: String get() = alias ?: idZona
}

/** Producto del catálogo canónico del nodo. La categoría deriva el destino. */
data class Producto(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double = 0.0,
    val disponible: Boolean = true,
)

/** Línea de una ronda/ticket: producto + cantidad. */
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
data class Ticket(
    val id: String,
    val rondaId: String,
    val destino: Destino,
    val estado: TicketEstado = TicketEstado.PENDIENTE,
    val lineas: List<Linea>,
)

/** Ronda enviada por Commander: la unidad que Bar parte en tickets BARRA/COCINA. */
data class Ronda(
    val id: String,
    val mesaId: String,          // Mesa.idZona ("T3")
    val numero: Int,             // número de ronda de la mesa
    val camarero: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val lineas: List<Linea>,
)
