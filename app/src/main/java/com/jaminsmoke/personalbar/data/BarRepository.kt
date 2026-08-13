package com.jaminsmoke.personalbar.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de verdad del nodo. Una implementación en memoria basta para v0.1;
 * Room será otra implementación del mismo seam sin tocar UI/ViewModel.
 */
interface BarRepository {
    /** Cola de bebida (tickets BARRA en PENDIENTE/LISTO). */
    val bebidaQueue: StateFlow<List<Ticket>>

    /** Cola de comida (tickets COCINA en PENDIENTE/LISTO). */
    val comidaQueue: StateFlow<List<Ticket>>

    /** Tickets servidos (salen de la cola y se acumulan aquí). */
    val servidos: StateFlow<List<Ticket>>

    /** Rondas recibidas. */
    val rondas: StateFlow<List<Ronda>>

    /** Mesas canónicas del nodo. */
    val mesas: StateFlow<List<Mesa>>

    /** Catálogo canónico del nodo. */
    val catalogo: StateFlow<List<Producto>>

    fun crearRonda(ronda: Ronda)
    fun marcarListo(ticketId: String)
    fun marcarServido(ticketId: String)
}
