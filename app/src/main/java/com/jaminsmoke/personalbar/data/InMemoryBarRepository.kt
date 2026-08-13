package com.jaminsmoke.personalbar.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementación en memoria del repositorio (v0.1). Sin Room: los datos no
 * sobreviven a reiniciar la app; el seam [BarRepository] permite enchufar Room después.
 */
class InMemoryBarRepository(
    catalogoInicial: List<Producto> = emptyList(),
    mesasIniciales: List<Mesa> = emptyList(),
) : BarRepository {

    private val catalogoPorId = catalogoInicial.associateBy { it.id }
    private val rondasRecibidas = ConcurrentHashMap.newKeySet<String>()

    private val _bebidaQueue = MutableStateFlow<List<Ticket>>(emptyList())
    private val _comidaQueue = MutableStateFlow<List<Ticket>>(emptyList())
    private val _servidos = MutableStateFlow<List<Ticket>>(emptyList())
    private val _rondas = MutableStateFlow<List<Ronda>>(emptyList())
    private val _mesas = MutableStateFlow(mesasIniciales)
    private val _catalogo = MutableStateFlow(catalogoInicial)
    private val _eventos = MutableSharedFlow<SalaEvent>(extraBufferCapacity = 16)

    override val bebidaQueue: StateFlow<List<Ticket>> = _bebidaQueue.asStateFlow()
    override val comidaQueue: StateFlow<List<Ticket>> = _comidaQueue.asStateFlow()
    override val servidos: StateFlow<List<Ticket>> = _servidos.asStateFlow()
    override val rondas: StateFlow<List<Ronda>> = _rondas.asStateFlow()
    override val mesas: StateFlow<List<Mesa>> = _mesas.asStateFlow()
    override val catalogo: StateFlow<List<Producto>> = _catalogo.asStateFlow()
    override val eventos: SharedFlow<SalaEvent> = _eventos.asSharedFlow()

    override fun crearRonda(ronda: Ronda): Boolean {
        if (!rondasRecibidas.add(ronda.id)) return false
        _rondas.update { it + ronda }
        val tickets = RondaSplitter.split(ronda, catalogoPorId)
        _bebidaQueue.update { it + tickets.filter { t -> t.destino == Destino.BARRA } }
        _comidaQueue.update { it + tickets.filter { t -> t.destino == Destino.COCINA } }
        return true
    }

    override fun marcarListo(ticketId: String): Boolean {
        val cambiado = transformTicket(ticketId) { it.copy(estado = TicketEstado.LISTO) }
        if (cambiado) _eventos.tryEmit(SalaEvent.listo(ticketId))
        return cambiado
    }

    override fun marcarServido(ticketId: String): Boolean {
        val ticket = _bebidaQueue.value.firstOrNull { it.id == ticketId }
            ?: _comidaQueue.value.firstOrNull { it.id == ticketId }
            ?: return false
        val servido = ticket.copy(estado = TicketEstado.SERVIDO)
        _bebidaQueue.update { it.filterNot { t -> t.id == ticketId } }
        _comidaQueue.update { it.filterNot { t -> t.id == ticketId } }
        _servidos.update { it + servido }
        _eventos.tryEmit(SalaEvent.servido(ticketId))
        return true
    }

    private fun transformTicket(ticketId: String, transform: (Ticket) -> Ticket): Boolean {
        val enBebida = _bebidaQueue.value.any { it.id == ticketId }
        val enComida = _comidaQueue.value.any { it.id == ticketId }
        if (enBebida) {
            _bebidaQueue.update { list -> list.map { if (it.id == ticketId) transform(it) else it } }
        }
        if (enComida) {
            _comidaQueue.update { list -> list.map { if (it.id == ticketId) transform(it) else it } }
        }
        return enBebida || enComida
    }
}
