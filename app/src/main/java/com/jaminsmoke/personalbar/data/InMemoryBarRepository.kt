package com.jaminsmoke.personalbar.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Máximo sufijo numérico de ids `prefijo-N`; 0 si no hay ninguno (para no colisionar con el seed). */
private fun maxNumSuffix(prefijo: String, ids: Collection<String>): Int =
    ids.mapNotNull { it.removePrefix("$prefijo-").toIntOrNull() }.maxOrNull() ?: 0

/**
 * Implementación en memoria del repositorio (v0.1). Sin Room: los datos no
 * sobreviven a reiniciar la app; el seam [BarRepository] permite enchufar Room después.
 */
class InMemoryBarRepository(
    establecimientoInicial: Establecimiento = Establecimiento("local-1", "Mi local"),
    salasIniciales: List<Sala> = emptyList(),
    catalogoInicial: List<Producto> = emptyList(),
    mesasIniciales: List<Mesa> = emptyList(),
) : BarRepository {

    private val catalogoPorId = catalogoInicial.associateBy { it.id }
    private val rondasRecibidas = ConcurrentHashMap.newKeySet<String>()
    private var salaSeq = maxNumSuffix("sala", salasIniciales.map { it.id })
    private var mesaSeq = maxNumSuffix("mesa", mesasIniciales.map { it.id })
    private var reservaSeq = 0

    private val _establecimiento = MutableStateFlow(establecimientoInicial)
    private val _salas = MutableStateFlow(salasIniciales)
    private val _mesas = MutableStateFlow(mesasIniciales)
    private val _reservas = MutableStateFlow<List<Reserva>>(emptyList())
    private val _bebidaQueue = MutableStateFlow<List<Ticket>>(emptyList())
    private val _comidaQueue = MutableStateFlow<List<Ticket>>(emptyList())
    private val _servidos = MutableStateFlow<List<Ticket>>(emptyList())
    private val _rondas = MutableStateFlow<List<Ronda>>(emptyList())
    private val _catalogo = MutableStateFlow(catalogoInicial)
    private val _camareros = MutableStateFlow<List<Camarero>>(emptyList())
    private val _eventos = MutableSharedFlow<SalaEvent>(extraBufferCapacity = 16)

    override val establecimiento: StateFlow<Establecimiento> = _establecimiento.asStateFlow()
    override val salas: StateFlow<List<Sala>> = _salas.asStateFlow()
    override val mesas: StateFlow<List<Mesa>> = _mesas.asStateFlow()
    override val reservas: StateFlow<List<Reserva>> = _reservas.asStateFlow()
    override val bebidaQueue: StateFlow<List<Ticket>> = _bebidaQueue.asStateFlow()
    override val comidaQueue: StateFlow<List<Ticket>> = _comidaQueue.asStateFlow()
    override val servidos: StateFlow<List<Ticket>> = _servidos.asStateFlow()
    override val rondas: StateFlow<List<Ronda>> = _rondas.asStateFlow()
    override val catalogo: StateFlow<List<Producto>> = _catalogo.asStateFlow()
    override val camareros: StateFlow<List<Camarero>> = _camareros.asStateFlow()
    override val eventos: SharedFlow<SalaEvent> = _eventos.asSharedFlow()

    // ── Rondas / tickets ──────────────────────────────────────────────────────

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

    // ── Salas ─────────────────────────────────────────────────────────────────

    override fun crearSala(nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_salas.value.any { it.nombre.equals(n, ignoreCase = true) }) return false
        val orden = (_salas.value.maxOfOrNull { it.orden } ?: 0) + 1
        val sala = Sala(id = "sala-${++salaSeq}", nombre = n, orden = orden)
        _salas.update { it + sala }
        return true
    }

    override fun renombrarSala(salaId: String, nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_salas.value.any { it.id != salaId && it.nombre.equals(n, ignoreCase = true) }) return false
        if (_salas.value.none { it.id == salaId }) return false
        _salas.update { list -> list.map { if (it.id == salaId) it.copy(nombre = n) else it } }
        return true
    }

    override fun eliminarSala(salaId: String): Boolean {
        if (_mesas.value.any { it.salaId == salaId }) return false
        val antes = _salas.value.size
        _salas.update { it.filterNot { s -> s.id == salaId } }
        return _salas.value.size < antes
    }

    // ── Mesas ─────────────────────────────────────────────────────────────────

    override fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean {
        if (_salas.value.none { it.id == salaId }) return false
        val cap = capacidad.coerceIn(1, 99)
        val a = alias?.trim()?.ifBlank { null }
        val indice = (_mesas.value.filter { it.salaId == salaId }.maxOfOrNull { it.indiceZona } ?: 0) + 1
        val numero = (_mesas.value.maxOfOrNull { it.numero } ?: 0) + 1
        val mesasSala = _mesas.value.filter { it.salaId == salaId }
        val (w, h) = mesaDims(forma, girada = false)
        val candidata = mesasSala.maxByOrNull { it.posX }?.let {
            val (lastW, _) = mesaDims(it.forma, it.girada)
            (it.posX + lastW + CELL_F) to it.posY
        } ?: (CELL_F to CELL_F)
        val ocupadas = mesasSala.map {
            val (ow, oh) = mesaDims(it.forma, it.girada)
            listOf(it.posX, it.posY, ow, oh)
        }
        val (px, py) = findNearestFreeCell(candidata.first, candidata.second, w, h, ocupadas)
        val mesa = Mesa(
            id = "mesa-${++mesaSeq}",
            salaId = salaId,
            indiceZona = indice,
            numero = numero,
            alias = a,
            forma = forma,
            capacidad = cap,
            posX = px,
            posY = py,
        )
        _mesas.update { it + mesa }
        return true
    }

    override fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        val cap = capacidad.coerceIn(1, 99)
        val a = alias?.trim()?.ifBlank { null }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(alias = a, capacidad = cap, forma = forma) else it } }
        return true
    }

    override fun borrarMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        if (mesa.reservaActivaId != null) return false
        _mesas.update { list ->
            list.filterNot { it.id == mesaId }.map { m ->
                if (m.salaId == mesa.salaId && m.indiceZona > mesa.indiceZona) m.copy(indiceZona = m.indiceZona - 1) else m
            }
        }
        return true
    }

    override fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(posX = posX, posY = posY) else it } }
        return true
    }

    override fun girarMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val nuevoGiro = !mesa.girada
        val (w, h) = mesaDims(mesa.forma, nuevoGiro)
        val ocupadas = _mesas.value.filter { it.salaId == mesa.salaId && it.id != mesaId }.map {
            val (ow, oh) = mesaDims(it.forma, it.girada)
            listOf(it.posX, it.posY, ow, oh)
        }
        val (x, y) = findNearestFreeCell(mesa.posX, mesa.posY, w, h, ocupadas)
        _mesas.update { list ->
            list.map { if (it.id == mesaId) it.copy(girada = nuevoGiro, posX = x, posY = y) else it }
        }
        return true
    }

    // ── Reservas / bloqueos ───────────────────────────────────────────────────

    override fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (mesa.bloqueada || mesa.reservaActivaId != null || mesaOcupada(mesa)) return false
        val reserva = Reserva(id = "reserva-${++reservaSeq}", mesaId = mesaId, nombre = n, paraEpoch = paraEpoch)
        _reservas.update { it + reserva }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(reservaActivaId = reserva.id) else it } }
        return true
    }

    override fun cancelarReserva(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val rid = mesa.reservaActivaId ?: return false
        _reservas.update { list -> list.map { if (it.id == rid) it.copy(canceladaEn = System.currentTimeMillis()) else it } }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(reservaActivaId = null) else it } }
        return true
    }

    override fun bloquearMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        if (mesaOcupada(mesa)) return false
        mesa.reservaActivaId?.let { rid ->
            _reservas.update { list -> list.map { if (it.id == rid) it.copy(canceladaEn = System.currentTimeMillis()) else it } }
        }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(bloqueada = true, reservaActivaId = null) else it } }
        return true
    }

    override fun desbloquearMesa(mesaId: String): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(bloqueada = false) else it } }
        return true
    }

    /** True si la mesa tiene tickets abiertos (ronda sin servir), es decir, está OCUPADA/EN_COCINA. */
    private fun mesaOcupada(mesa: Mesa): Boolean {
        val nombreSala = _salas.value.firstOrNull { it.id == mesa.salaId }?.nombre.orEmpty()
        val idZona = mesa.idZona(nombreSala)
        val rondaPorId = _rondas.value.associateBy { it.id }
        val abiertos = _bebidaQueue.value + _comidaQueue.value
        return abiertos.any { rondaPorId[it.rondaId]?.mesaId == idZona }
    }

    // ── Camareros (lista blanca) ──────────────────────────────────────────────

    override fun altaCamarero(camareroId: String, credencialId: String?): Boolean {
        val existente = _camareros.value.firstOrNull { it.id == camareroId }
        if (existente != null && existente.estado == CamareroEstado.ACTIVA) return false
        val camarero = Camarero(id = camareroId, credencialId = credencialId)
        _camareros.update { list ->
            val idx = list.indexOfFirst { it.id == camareroId }
            if (idx >= 0) list.toMutableList().also { it[idx] = camarero } else list + camarero
        }
        return true
    }

    override fun revocarCamarero(camareroId: String): Boolean {
        if (_camareros.value.none { it.id == camareroId }) return false
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(estado = CamareroEstado.REVOCADA) else it }
        }
        return true
    }
}
