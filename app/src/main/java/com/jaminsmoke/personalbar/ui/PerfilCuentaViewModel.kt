package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.lan.CambioPasswordResult
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cuenta de negocio (Perfil del hub): email, UUID y contraseña. Los datos del
 * local viven en [LocalViewModel].
 */
class PerfilCuentaViewModel : ViewModel() {
    private val app = PersonalBarApp.get()
    private val repository: BarRepository = app.repository

    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        viewModelScope.launch {
            _sesion.value = app.db.barDao().getSesionNegocio()
        }
    }

    fun cambiarPassword(actual: String, nueva: String, confirmacion: String) {
        val error = validarNuevaPassword(nueva, confirmacion)
        if (error != null) {
            _mensaje.value = error
            return
        }
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val resultado = IdentityNegocioClient.cambiarPassword(actual, nueva)
            _mensaje.value = when (resultado) {
                CambioPasswordResult.OK -> R.string.perfil_password_ok
                CambioPasswordResult.ACTUAL_INCORRECTA -> R.string.perfil_password_actual_incorrecta
                CambioPasswordResult.ERROR -> R.string.perfil_password_error
            }
            _trabajando.value = false
        }
    }
}

/**
 * Valida la nueva contraseña de negocio: mín 8 caracteres y coincidencia con la
 * confirmación. Devuelve el id de recurso del error, o null si es válida.
 */
fun validarNuevaPassword(nueva: String, confirmacion: String): Int? = when {
    nueva.length < 8 -> R.string.perfil_password_corta
    nueva != confirmacion -> R.string.perfil_password_no_coincide
    else -> null
}
