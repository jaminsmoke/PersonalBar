package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.TicketEstado
import com.jaminsmoke.personalbar.lan.BarLanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Proyección de ticket para la expo: une Ticket + Ronda (mesa, número, camarero, preparador). */
data class ExpoTicket(
    val id: String,
    val mesa: String,
    val ronda: Int,
    val camarero: String?,
    val preparadoPor: String?,
    val estado: TicketEstado,
    val numeroCola: Int,
    val destino: Destino,
    val lineas: List<String>,
)

data class ExpoUiState(
    val drinkQueue: List<ExpoTicket> = emptyList(),
    val foodQueue: List<ExpoTicket> = emptyList(),
    val roomActive: Boolean = false,
    val camareros: List<Camarero> = emptyList(),
    val activeCamarero: Camarero? = null,
)

/** Base intermedia del combine (evita el overload de 6 flows). */
private data class ColasBase(
    val bebida: List<Ticket>,
    val comida: List<Ticket>,
    val rondas: List<Ronda>,
    val active: Boolean,
    val camareros: List<Camarero>,
)

class ExpoViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    private val _uiState = MutableStateFlow(ExpoUiState())
    val uiState: StateFlow<ExpoUiState> = _uiState.asStateFlow()

    /** Sesión activa del puesto: qué camarero está preparando. */
    private val _activeCamarero = MutableStateFlow<Camarero?>(null)
    val activeCamarero: StateFlow<Camarero?> = _activeCamarero.asStateFlow()

    init {
        val base = combine(
            repository.bebidaQueue,
            repository.comidaQueue,
            repository.rondas,
            PersonalBarApp.get().roomActive,
            repository.camareros,
        ) { bebida, comida, rondas, active, camareros ->
            ColasBase(bebida, comida, rondas, active, camareros)
        }
        viewModelScope.launch {
            combine(base, _activeCamarero) { b, activo ->
                val rondasPorId = b.rondas.associateBy { it.id }
                ExpoUiState(
                    drinkQueue = b.bebida.map { it.toExpoTicket(rondasPorId) },
                    foodQueue = b.comida.map { it.toExpoTicket(rondasPorId) },
                    roomActive = b.active,
                    camareros = b.camareros.filter { it.estado == CamareroEstado.ACTIVA },
                    activeCamarero = activo,
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Arranca/para el nodo (service + server) según el estado actual. */
    fun toggleLocal() {
        val app = PersonalBarApp.get()
        if (app.roomActive.value) {
            BarLanService.stop(app)
        } else {
            BarLanService.start(app)
        }
    }

    /** Sesión activa: quién está preparando en el puesto. */
    fun setPreparador(camareroId: String) {
        _activeCamarero.value = repository.camareros.value.firstOrNull { it.id == camareroId }
    }

    fun clearPreparador() {
        _activeCamarero.value = null
    }

    /** Marca el ticket como preparado por la sesión activa. Sin sesión, no-op. */
    fun marcarPreparado(ticketId: String) {
        val preparador = _activeCamarero.value ?: return
        repository.marcarPreparado(ticketId, preparador.nombre ?: preparador.id.take(8))
    }

    /** Marca el ticket como recogido (sale de la cola). */
    fun marcarRecogido(ticketId: String) {
        repository.marcarRecogido(ticketId)
    }
}

private fun Ticket.toExpoTicket(rondas: Map<String, Ronda>): ExpoTicket {
    val ronda = rondas[rondaId]
    return ExpoTicket(
        id = id,
        mesa = ronda?.mesaId ?: "—",
        ronda = ronda?.numero ?: 0,
        camarero = ronda?.camarero,
        preparadoPor = preparadoPor,
        estado = estado,
        numeroCola = numeroCola,
        destino = destino,
        lineas = lineas.map { "${it.cantidad}x ${it.nombreProducto}" },
    )
}
