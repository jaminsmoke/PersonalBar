package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.NotificacionRemoto
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.ResultadoNotificaciones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la bandeja de notificaciones. */
data class NotificacionesUiState(
    val cargando: Boolean = false,
    val notificaciones: List<NotificacionRemoto> = emptyList(),
    val error: Boolean = false,
)

/**
 * Bandeja de notificaciones del negocio (capa global, abierta desde la campana
 * del header). Lista todas (leídas y no-leídas, ordenadas por fecha descendente
 * que ya aplica el server) y marca leída al tocar una pendiente. Las notificaciones
 * son efímeras (viven en Identity): no se espejan en Room.
 */
class NotificacionesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificacionesUiState())
    val uiState: StateFlow<NotificacionesUiState> = _uiState.asStateFlow()

    private val app = PersonalBarApp.get()

    init {
        cargar()
    }

    fun cargar() = refrescar()

    fun refrescar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            when (val resultado = IdentityNegocioClient.listarNotificaciones(soloNoLeidas = false)) {
                is ResultadoNotificaciones.Lista -> _uiState.value = _uiState.value.copy(
                    cargando = false,
                    notificaciones = resultado.notificaciones,
                    error = false,
                )
                ResultadoNotificaciones.EstablecimientoFantasma,
                ResultadoNotificaciones.Error,
                -> _uiState.value = _uiState.value.copy(cargando = false, error = true)
            }
        }
    }

    /** Marca la notificación como leída (idempotente) y refresca lista + badge. */
    fun marcarLeida(notificacion: NotificacionRemoto) {
        if (notificacion.leida) return
        viewModelScope.launch {
            IdentityNegocioClient.marcarNotificacionLeida(notificacion.id)
            refrescar()
            app.refrescarNotificacionesNoLeidas()
        }
    }
}
