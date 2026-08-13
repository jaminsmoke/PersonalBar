package com.jaminsmoke.personalbar.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de verdad del nodo. Una implementación en memoria basta para v0.1;
 * Room será otra implementación del mismo seam sin tocar UI/ViewModel.
 */
interface BarRepository {
    /** Cuenta del establecimiento (un nodo = un establecimiento en v0.1). */
    val establecimiento: StateFlow<Establecimiento>

    /** Salas del mapa (primer nivel del layout). */
    val salas: StateFlow<List<Sala>>

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

    /** Eventos de sala (listo/servido) para re-enviar por SSE a Commander. */
    val eventos: SharedFlow<SalaEvent>

    /** @return true si la ronda se procesó; false si ya existía (idempotente). */
    fun crearRonda(ronda: Ronda): Boolean

    /** @return true si el ticket se encontró y se marcó listo. */
    fun marcarListo(ticketId: String): Boolean

    /** @return true si el ticket se encontró y pasó a servidos. */
    fun marcarServido(ticketId: String): Boolean

    /** @return true si se creó la sala; false si el nombre ya existe o está vacío. */
    fun crearSala(nombre: String): Boolean

    /** @return true si se renombró; false si no existe o el nombre ya está en uso. */
    fun renombrarSala(salaId: String, nombre: String): Boolean

    /** @return true si se eliminó; false si no existe o tiene mesas. */
    fun eliminarSala(salaId: String): Boolean
}
