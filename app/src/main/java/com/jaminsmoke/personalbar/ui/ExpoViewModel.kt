package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Destino de un ticket en la expo. Barra = bebida, Cocina = comida. */
enum class Destino { BARRA, COCINA }

/**
 * Stub visual de ticket. El modelo real (mesa canónica, ronda, líneas, destinos)
 * se define en el ítem de Modelo de datos; aquí solo se valida el layout.
 */
data class TicketStub(
    val mesa: String,
    val ronda: Int,
    val camarero: String?,
    val lineas: List<String>,
    val destino: Destino,
)

data class ExpoUiState(
    val drinkQueue: List<TicketStub> = emptyList(),
    val foodQueue: List<TicketStub> = emptyList(),
    val roomActive: Boolean = false,
)

class ExpoViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ExpoUiState())
    val uiState: StateFlow<ExpoUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = ExpoUiState(
            drinkQueue = listOf(
                TicketStub(
                    mesa = "T3",
                    ronda = 1,
                    camarero = "Lucía",
                    lineas = listOf("2x Caña", "1x Tinto de verano"),
                    destino = Destino.BARRA,
                ),
                TicketStub(
                    mesa = "T7",
                    ronda = 2,
                    camarero = "Marcos",
                    lineas = listOf("3x Caña"),
                    destino = Destino.BARRA,
                ),
            ),
            foodQueue = listOf(
                TicketStub(
                    mesa = "T3",
                    ronda = 1,
                    camarero = "Lucía",
                    lineas = listOf("1x Croquetas", "2x Tostada con tomate"),
                    destino = Destino.COCINA,
                ),
            ),
            roomActive = false,
        )
        viewModelScope.launch {
            PersonalBarApp.get().roomActive.collect { active ->
                _uiState.update { it.copy(roomActive = active) }
            }
        }
    }
}
