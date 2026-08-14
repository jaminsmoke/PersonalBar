package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.lan.IdentityClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: establecimiento, conexión con Identity y CRUD de salas. */
class AjustesViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento
    val salas: StateFlow<List<Sala>> = repository.salas
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso de string (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    private val _identityError = MutableStateFlow<Int?>(null)
    val identityError: StateFlow<Int?> = _identityError.asStateFlow()

    fun crearSala(nombre: String) {
        _mensaje.value =
            if (repository.crearSala(nombre)) R.string.ajustes_sala_creada
            else R.string.ajustes_error_nombre
    }

    fun renombrarSala(id: String, nombre: String) {
        _mensaje.value =
            if (repository.renombrarSala(id, nombre)) R.string.ajustes_sala_renombrada
            else R.string.ajustes_error_nombre
    }

    fun eliminarSala(id: String) {
        _mensaje.value =
            if (repository.eliminarSala(id)) R.string.ajustes_sala_eliminada
            else R.string.ajustes_error_eliminar
    }

    /**
     * Conecta con Identity: login de la cuenta de negocio + vínculo del establecimiento
     * (crea el UUID si no existe) + espejo inicial de miembros.
     */
    fun conectarIdentity(baseUrl: String, email: String, password: String) {
        val url = baseUrl.trim()
        val mail = email.trim()
        if (url.isEmpty() || mail.isEmpty() || password.isEmpty()) {
            _identityError.value = R.string.identity_campos_incompletos
            return
        }
        _identityError.value = null
        _trabajando.value = true
        viewModelScope.launch {
            IdentityClient.configurar(url)
            val loginOk = IdentityClient.loginNegocio(mail, password)
            if (!loginOk) {
                _trabajando.value = false
                _identityError.value = R.string.identity_login_fallido
                return@launch
            }
            val uuid = IdentityClient.vincularEstablecimiento(establecimiento.value.nombre)
            if (uuid == null) {
                _trabajando.value = false
                _identityError.value = R.string.identity_vincular_fallido
                return@launch
            }
            repository.setIdentityConfig(
                IdentityConfig(conectado = true, baseUrl = url, establecimientoUuid = uuid)
            )
            // Espejo inicial: miembros ACTIVA de Identity → lista blanca local.
            val miembros = IdentityClient.listarMiembros()
                .filter { it.estado.equals("activa", ignoreCase = true) }
                .map { it.camareroId }
            repository.sincronizarMiembros(miembros)
            _trabajando.value = false
        }
    }

    fun desconectarIdentity() {
        IdentityClient.desconectar()
        repository.setIdentityConfig(IdentityConfig())
        _identityError.value = null
    }
}
