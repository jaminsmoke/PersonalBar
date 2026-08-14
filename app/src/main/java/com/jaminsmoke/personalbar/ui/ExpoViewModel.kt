package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.lan.BarLanService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Proyección de ticket para la expo: une Ticket + Ronda (mesa, número, camarero). */
data class ExpoTicket(
    val id: String,
    val mesa: String,
    val ronda: Int,
    val camarero: String?,
    val lineas: List<String>,
)

data class ExpoUiState(
    val drinkQueue: List<ExpoTicket> = emptyList(),
    val foodQueue: List<ExpoTicket> = emptyList(),
    val roomActive: Boolean = false,
)

class ExpoViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    private val _uiState = MutableStateFlow(ExpoUiState())
    val uiState: StateFlow<ExpoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.bebidaQueue,
                repository.comidaQueue,
                repository.rondas,
                PersonalBarApp.get().roomActive,
            ) { bebida, comida, rondas, active ->
                val rondasPorId = rondas.associateBy { it.id }
                ExpoUiState(
                    drinkQueue = bebida.map { it.toExpoTicket(rondasPorId) },
                    foodQueue = comida.map { it.toExpoTicket(rondasPorId) },
                    roomActive = active,
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
}

private fun Ticket.toExpoTicket(rondas: Map<String, Ronda>): ExpoTicket {
    val ronda = rondas[rondaId]
    return ExpoTicket(
        id = id,
        mesa = ronda?.mesaId ?: "—",
        ronda = ronda?.numero ?: 0,
        camarero = ronda?.camarero,
        lineas = lineas.map { "${it.cantidad}x ${it.nombreProducto}" },
    )
}
