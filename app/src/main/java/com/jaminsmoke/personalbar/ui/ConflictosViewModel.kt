package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.ConflictoRemoto
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.ResultadoConflictos
import com.jaminsmoke.personalbar.lan.ResultadoNotificaciones
import com.jaminsmoke.personalbar.lan.ResultadoResolucion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la pantalla de conflictos de catálogo. */
data class ConflictosUiState(
    val cargando: Boolean = false,
    val conflictos: List<ConflictoRemoto> = emptyList(),
    /** Ids de conflicto con una resolución en curso (deshabilitan sus botones). */
    val resolviendo: Set<String> = emptySet(),
    val error: Boolean = false,
    /** Feedback de la última acción como id de recurso (null = sin aviso). */
    val aviso: Int? = null,
)

/**
 * Pantalla «Conflictos»: lista los conflictos `pendiente` (diff canónico →
 * propuesto) y acepta/rechaza cada divergencia. Los conflictos son efímeros
 * (viven en Identity): se recargan bajo demanda y no se espejan en Room.
 */
class ConflictosViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ConflictosUiState())
    val uiState: StateFlow<ConflictosUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() = refrescar()

    /** Refresca la lista de pendientes. No pisa `aviso` ni `resolviendo`. */
    fun refrescar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true)
            when (val resultado = IdentityNegocioClient.listarConflictos("pendiente")) {
                is ResultadoConflictos.Lista -> _uiState.value = _uiState.value.copy(
                    cargando = false,
                    conflictos = resultado.conflictos,
                    error = false,
                )
                ResultadoConflictos.EstablecimientoFantasma,
                ResultadoConflictos.Error,
                -> _uiState.value = _uiState.value.copy(cargando = false, error = true)
            }
        }
    }

    fun aceptar(conflicto: ConflictoRemoto) =
        resolver(conflicto, "aceptar", R.string.conflictos_resuelto_aceptado)

    fun rechazar(conflicto: ConflictoRemoto) =
        resolver(conflicto, "rechazar", R.string.conflictos_resuelto_rechazado)

    private fun resolver(conflicto: ConflictoRemoto, decision: String, avisoOk: Int) {
        if (conflicto.id in _uiState.value.resolviendo) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolviendo = _uiState.value.resolviendo + conflicto.id)
            val resultado = IdentityNegocioClient.resolverConflicto(
                conflictoId = conflicto.id,
                decision = decision,
                expectedRevision = conflicto.canonicalRevision,
            )
            val sinResolviendo = _uiState.value.resolviendo - conflicto.id
            when (resultado) {
                ResultadoResolucion.Resuelta -> {
                    _uiState.value = _uiState.value.copy(resolviendo = sinResolviendo, aviso = avisoOk)
                    sincronizarNotificacionResuelta(conflicto.id)
                    refrescar()
                }
                ResultadoResolucion.Obsoleta -> {
                    _uiState.value = _uiState.value.copy(
                        resolviendo = sinResolviendo,
                        aviso = R.string.conflictos_obsoleto,
                    )
                    refrescar()
                }
                ResultadoResolucion.YaResuelta -> {
                    _uiState.value = _uiState.value.copy(
                        resolviendo = sinResolviendo,
                        aviso = R.string.conflictos_ya_resuelta,
                    )
                    sincronizarNotificacionResuelta(conflicto.id)
                    refrescar()
                }
                ResultadoResolucion.EstablecimientoFantasma,
                ResultadoResolucion.Error,
                -> _uiState.value = _uiState.value.copy(resolviendo = sinResolviendo, error = true)
            }
        }
    }

    /**
     * Tras resolver un conflicto, la notificación asociada queda sin marcar en el
     * server (`resolve_conflict` no toca `read_at`). Best-effort: la marca leída
     * por `conflicto_id` y refresca el badge de la campana.
     */
    private fun sincronizarNotificacionResuelta(conflictoId: String) {
        viewModelScope.launch {
            when (val r = IdentityNegocioClient.listarNotificaciones(soloNoLeidas = true)) {
                is ResultadoNotificaciones.Lista -> r.notificaciones
                    .firstOrNull { it.conflictoId == conflictoId }
                    ?.let { IdentityNegocioClient.marcarNotificacionLeida(it.id) }
                ResultadoNotificaciones.EstablecimientoFantasma,
                ResultadoNotificaciones.Error,
                -> Unit
            }
            PersonalBarApp.get().refrescarNotificacionesNoLeidas()
        }
    }
}
