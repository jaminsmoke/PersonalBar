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
    /** Camareros ACTIVA marcados de servicio en el puesto (varios a la vez). */
    val deServicio: List<Camarero> = emptyList(),
    /** El que prepara ahora (último chip pulsado); sin él no se marca Preparado. */
    val enMano: Camarero? = null,
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

    /** Quién prepara ahora («en mano»): último chip pulsado de los de servicio. */
    private val _enMano = MutableStateFlow<Camarero?>(null)
    val enMano: StateFlow<Camarero?> = _enMano.asStateFlow()

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
            combine(base, repository.deServicio, _enMano) { b, deServicio, enMano ->
                val rondasPorId = b.rondas.associateBy { it.id }
                ExpoUiState(
                    drinkQueue = b.bebida.map { it.toExpoTicket(rondasPorId) },
                    foodQueue = b.comida.map { it.toExpoTicket(rondasPorId) },
                    roomActive = b.active,
                    camareros = b.camareros.filter { it.estado == CamareroEstado.ACTIVA },
                    deServicio = deServicio,
                    enMano = enMano,
                )
            }.collect { state ->
                _uiState.value = state
                // Tras arranque/recarga (Room) el «en mano» no se persiste: si hay
                // camareros de servicio y nadie tiene el ticket en mano, el primero
                // de la lista lo toma (coherente con la sesión múltiple del puesto).
                if (state.enMano == null && state.deServicio.isNotEmpty()) {
                    _enMano.value = state.deServicio.first()
                }
            }
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

    /**
     * Alterna «de servicio» del camarero en el puesto (añadir/quitar, no sustituir).
     * Al ponerlo de servicio queda además como «en mano» (el que prepara ahora).
     */
    fun alternarDeServicio(camareroId: String) {
        val camarero = repository.camareros.value.firstOrNull { it.id == camareroId } ?: return
        if (camarero.deServicio) {
            repository.quitarDeServicio(camareroId)
            if (_enMano.value?.id == camareroId) _enMano.value = null
        } else {
            repository.ponerDeServicio(camareroId)
            _enMano.value = repository.camareros.value.firstOrNull { it.id == camareroId }
        }
    }

    /** Fija el «en mano» (último chip pulsado) sin cambiar la lista de servicio. */
    fun seleccionarEnMano(camareroId: String) {
        _enMano.value = repository.camareros.value.firstOrNull { it.id == camareroId }
    }

    fun clearPreparador() {
        _enMano.value = null
    }

    /** Marca el ticket como preparado por el que está «en mano». Sin él, no-op. */
    fun marcarPreparado(ticketId: String) {
        val preparador = _enMano.value ?: return
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
