package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.SesionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: establecimiento + sesiones de dispositivo de la cuenta (v0.4). */
class AjustesViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento

    private val _sesiones = MutableStateFlow<List<SesionItem>>(emptyList())
    val sesiones: StateFlow<List<SesionItem>> = _sesiones

    private val _cargandoSesiones = MutableStateFlow(false)
    val cargandoSesiones: StateFlow<Boolean> = _cargandoSesiones

    private val _errorSesiones = MutableStateFlow(false)
    val errorSesiones: StateFlow<Boolean> = _errorSesiones

    /** Carga las sesiones activas de la cuenta de negocio (`GET /me/sesiones`). */
    fun listarSesiones() {
        if (!IdentityNegocioClient.conectado) return
        viewModelScope.launch {
            _cargandoSesiones.value = true
            _errorSesiones.value = false
            _sesiones.value = IdentityNegocioClient.listarSesiones()
            _cargandoSesiones.value = false
        }
    }

    /** Revoca una sesión concreta y refresca la lista. */
    fun revocarSesion(sesionId: String) {
        viewModelScope.launch {
            val ok = IdentityNegocioClient.revocarSesion(sesionId)
            if (ok) {
                _errorSesiones.value = false
                listarSesiones()
            } else {
                _errorSesiones.value = true
            }
        }
    }

    /** Revoca el resto de sesiones (conserva la actual del puesto). */
    fun revocarOtrasSesiones() {
        viewModelScope.launch {
            val ok = IdentityNegocioClient.revocarOtrasSesiones()
            if (ok) {
                _errorSesiones.value = false
                listarSesiones()
            } else {
                _errorSesiones.value = true
            }
        }
    }

    fun limpiarError() {
        _errorSesiones.value = false
    }
}
