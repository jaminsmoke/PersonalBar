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

    /** Mesas canónicas del nodo. */
    val mesas: StateFlow<List<Mesa>>

    /** Reservas (holds comerciales) activas/canceladas. */
    val reservas: StateFlow<List<Reserva>>

    /** Cola de bebida (tickets BARRA en PENDIENTE/LISTO). */
    val bebidaQueue: StateFlow<List<Ticket>>

    /** Cola de comida (tickets COCINA en PENDIENTE/LISTO). */
    val comidaQueue: StateFlow<List<Ticket>>

    /** Tickets completados (recogidos hoy, servidos mañana): salen de la cola y se acumulan aquí. */
    val servidos: StateFlow<List<Ticket>>

    /** Rondas recibidas. */
    val rondas: StateFlow<List<Ronda>>

    /** Catálogo canónico del nodo. */
    val catalogo: StateFlow<List<Producto>>

    /** Lista blanca de camareros del establecimiento (mirror de Identity). */
    val camareros: StateFlow<List<Camarero>>

    /** Eventos de sala (listo/servido) para re-enviar por SSE a Commander. */
    val eventos: SharedFlow<SalaEvent>

    /** @return true si la ronda se procesó; false si ya existía (idempotente). */
    fun crearRonda(ronda: Ronda): Boolean

    /** @return true si el ticket estaba PENDIENTE y se marcó PREPARADO con el preparador. */
    fun marcarPreparado(ticketId: String, preparadoPor: String): Boolean

    /** @return true si el ticket estaba PREPARADO y pasó a RECOGIDO (sale de la cola). */
    fun marcarRecogido(ticketId: String): Boolean

    /** @return true si se creó la sala; false si el nombre ya existe o está vacío. */
    fun crearSala(nombre: String): Boolean

    /** @return true si se renombró; false si no existe o el nombre ya está en uso. */
    fun renombrarSala(salaId: String, nombre: String): Boolean

    /** @return true si se eliminó; false si no existe o tiene mesas. */
    fun eliminarSala(salaId: String): Boolean

    /** @return true si se creó la mesa (auto-posicionada en celda libre). */
    fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean

    /** @return true si se actualizó la configuración de la mesa. */
    fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean

    /** @return true si se borró; false si no existe o tiene reserva activa. */
    fun borrarMesa(mesaId: String): Boolean

    /** @return true si se movió la mesa a (posX, posY). */
    fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean

    /** @return true si se giró (re-encuadrando en celda libre si hace falta). */
    fun girarMesa(mesaId: String): Boolean

    /** @return true si se reservó; false si ocupada/bloqueada/ya reservada o nombre vacío. */
    fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean

    /** @return true si se canceló la reserva activa de la mesa. */
    fun cancelarReserva(mesaId: String): Boolean

    /** @return true si se bloqueó; false si ocupada o inexistente. */
    fun bloquearMesa(mesaId: String): Boolean

    /** @return true si se desbloqueó; false si inexistente. */
    fun desbloquearMesa(mesaId: String): Boolean

    /** @return true si se dio de alta; false si ya existe activa. */
    fun altaCamarero(camareroId: String, credencialId: String?): Boolean

    /** @return true si se revocó; false si no existe. */
    fun revocarCamarero(camareroId: String): Boolean
}
