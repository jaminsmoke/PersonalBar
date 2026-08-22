package com.jaminsmoke.personalbar.ui.gestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.MesaCfcResponse
import com.jaminsmoke.personalbar.PersonalBarApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de gestión de QRs por mesa (CFC).
 * Consulta el listado de mesas CFC a Identity y permite rotar tokens.
 */
class QrMesasViewModel : ViewModel() {

    private val repository: BarRepository = PersonalBarApp.get().repository

    /** Configuración de identidad (gate de conexión). */
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    /** Conectado a Identity. */
    val conectado: StateFlow<Boolean> = PersonalBarApp.get().conectividad.isOnline

    private val _mesas = MutableStateFlow<List<MesaCfcResponse>>(emptyList())
    val mesas: StateFlow<List<MesaCfcResponse>> = _mesas.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    init {
        cargar()
    }

    /** Carga el listado de mesas CFC desde Identity. */
    fun cargar() {
        if (!IdentityNegocioClient.conectado) return
        viewModelScope.launch {
            _trabajando.value = true
            try {
                _mesas.value = IdentityNegocioClient.listarMesasCfc()
            } finally {
                _trabajando.value = false
            }
        }
    }

    /**
     * Rota el token de una mesa: el QR impreso viejo deja de valer (410).
     * Tras la rotación, recarga el listado para obtener la nueva `url_publica`.
     */
    fun rotar(mesaUuid: String) {
        viewModelScope.launch {
            _trabajando.value = true
            try {
                IdentityNegocioClient.rotarMesaCfc(mesaUuid)
                // Recargar el listado completo para reflejar la nueva URL
                _mesas.value = IdentityNegocioClient.listarMesasCfc()
            } finally {
                _trabajando.value = false
            }
        }
    }
}
