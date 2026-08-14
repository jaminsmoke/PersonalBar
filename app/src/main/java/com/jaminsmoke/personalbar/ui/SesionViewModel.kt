package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.lan.IdentityClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sesión de la cuenta de negocio en el puesto de Bar. El usuario no ve la URL del
 * server Identity (config de entorno/dev); solo hace login/registro.
 */
class SesionViewModel : ViewModel() {
    private val app = PersonalBarApp.get()

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        // Al arrancar, restaurar la sesión persistida (solo si se marcó «Recuérdame»).
        viewModelScope.launch {
            val guardada = app.db.barDao().getSesionNegocio()
            if (guardada?.token != null) {
                hidratarIdentity(guardada)
                _sesion.value = guardada
            }
        }
    }

    /** Login de cuenta de negocio existente. Si [recordar], la sesión se persiste. */
    fun login(email: String, password: String, recordar: Boolean) {
        val mail = email.trim()
        if (mail.isEmpty() || password.isEmpty()) {
            _mensaje.value = R.string.sesion_campos_incompletos
            return
        }
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val ok = IdentityClient.loginNegocio(mail, password)
            if (!ok) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_login_fallido
                return@launch
            }
            val uuid = IdentityClient.vincularEstablecimiento(
                app.repository.establecimiento.value.nombre
            )
            if (uuid == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_vincular_fallido
                return@launch
            }
            val sesion = SesionNegocio(
                token = IdentityClient.negocioToken,
                email = mail,
                nombreMostrar = IdentityClient.cuentaNegocio?.nombreMostrar,
                establecimientoUuid = uuid,
                tipo = _sesion.value?.tipo,
                logoClave = _sesion.value?.logoClave,
            )
            _sesion.value = sesion
            marcarConectado(uuid)
            persistirSesion(sesion, recordar)
            _trabajando.value = false
        }
    }

    /** Registro de cuenta de negocio nueva + login + vínculo del establecimiento. */
    fun registro(
        nombre: String,
        email: String,
        password: String,
        tipo: TipoEstablecimiento?,
        logoClave: String?,
        recordar: Boolean,
    ) {
        val mail = email.trim()
        val nombreTrim = nombre.trim()
        if (nombreTrim.isEmpty() || mail.isEmpty() || password.isEmpty()) {
            _mensaje.value = R.string.sesion_campos_incompletos
            return
        }
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val id = IdentityClient.registroNegocio(nombreTrim, mail, password)
            if (id == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_registro_fallido
                return@launch
            }
            val ok = IdentityClient.loginNegocio(mail, password)
            if (!ok) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_login_fallido
                return@launch
            }
            val uuid = IdentityClient.vincularEstablecimiento(nombreTrim)
            if (uuid == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_vincular_fallido
                return@launch
            }
            val sesion = SesionNegocio(
                token = IdentityClient.negocioToken,
                email = mail,
                nombreMostrar = IdentityClient.cuentaNegocio?.nombreMostrar ?: nombreTrim,
                establecimientoUuid = uuid,
                tipo = tipo,
                logoClave = logoClave,
            )
            _sesion.value = sesion
            marcarConectado(uuid)
            persistirSesion(sesion, recordar)
            _trabajando.value = false
        }
    }

    fun logout() {
        IdentityClient.desconectar()
        _sesion.value = null
        app.repository.setIdentityConfig(IdentityConfig())
        viewModelScope.launch { app.db.barDao().clearSesionNegocio() }
    }

    /** Refleja en el repo que hay una cuenta de negocio vinculada (lo usa Camareros). */
    private fun marcarConectado(uuid: String) {
        app.repository.setIdentityConfig(
            IdentityConfig(conectado = true, establecimientoUuid = uuid)
        )
    }

    /** Persiste la sesión en Room solo si «Recuérdame»; si no, la deja en memoria. */
    private fun persistirSesion(sesion: SesionNegocio, recordar: Boolean) {
        viewModelScope.launch {
            if (recordar) app.db.barDao().upsertSesionNegocio(sesion)
            else app.db.barDao().clearSesionNegocio()
        }
    }

    /** Rehidrata el [IdentityClient] con la sesión guardada en Room. */
    private fun hidratarIdentity(sesion: SesionNegocio) {
        // La URL del server no se guarda por sesión: IdentityClient usa la config de
        // entorno por defecto (dev). Se completa cuando haya server fijo.
        IdentityClient.negocioToken = sesion.token
        IdentityClient.establecimientoUuid = sesion.establecimientoUuid
        IdentityClient.cuentaNegocio = sesion.nombreMostrar?.let { nombre ->
            com.jaminsmoke.personalbar.lan.IdentityCuentaNegocio(
                email = sesion.email.orEmpty(),
                nombreMostrar = nombre,
            )
        }
    }
}
