package com.jaminsmoke.personalbar.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.data.apiValor
import com.jaminsmoke.personalbar.data.tipoDesdeApi
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.IdentityCuentaNegocio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sesión de la cuenta de negocio en el puesto de Bar. El usuario no ve la URL del
 * server Identity (config de entorno/dev); solo hace login/registro. El tipo y el
 * logo del establecimiento se sincronizan contra Identity.
 */
class SesionViewModel : ViewModel() {
    private val app = PersonalBarApp.get()

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    /** Bytes del logo descargado de Identity (null = sin logo o aún no cargado). */
    private val _logoBytes = MutableStateFlow<ByteArray?>(null)
    val logoBytes: StateFlow<ByteArray?> = _logoBytes.asStateFlow()

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
                cargarLogo()
                sincronizarDesdeIdentity()
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
            val ok = IdentityNegocioClient.loginNegocio(mail, password)
            if (!ok) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_login_fallido
                return@launch
            }
            val uuid = IdentityNegocioClient.vincularEstablecimiento(
                app.repository.establecimiento.value.nombre
            )
            if (uuid == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_vincular_fallido
                return@launch
            }
            val perfil = IdentityNegocioClient.cuentaNegocio
            val sesion = SesionNegocio(
                token = IdentityNegocioClient.negocioToken,
                email = mail,
                nombreMostrar = perfil?.nombreMostrar,
                establecimientoUuid = uuid,
                tipo = tipoDesdeApi(perfil?.tipoEstablecimiento),
                logoUrl = perfil?.logoUrl,
                dataOrigin = perfil?.dataOrigin ?: IdentityNegocioClient.establecimientoDataOrigin,
            )
            _sesion.value = sesion
            marcarConectado(uuid)
            persistirSesion(sesion, recordar)
            cargarLogo()
            sincronizarDesdeIdentity()
            _trabajando.value = false
        }
    }

    /** Registro de cuenta de negocio nueva + login + vínculo + subida del logo (si hay). */
    fun registro(
        nombre: String,
        email: String,
        password: String,
        tipo: TipoEstablecimiento?,
        logoUri: Uri?,
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
            val id = IdentityNegocioClient.registroNegocio(nombreTrim, mail, password, tipo?.apiValor())
            if (id == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_registro_fallido
                return@launch
            }
            val ok = IdentityNegocioClient.loginNegocio(mail, password)
            if (!ok) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_login_fallido
                return@launch
            }
            val uuid = IdentityNegocioClient.vincularEstablecimiento(nombreTrim)
            if (uuid == null) {
                _trabajando.value = false
                _mensaje.value = R.string.sesion_vincular_fallido
                return@launch
            }

            // Subir el logo si el usuario eligió imagen (el server lo normaliza a WebP).
            var logoUrl: String? = null
            if (logoUri != null) {
                val (bytes, mimetype) = leerImagen(logoUri)
                if (bytes != null && IdentityNegocioClient.subirLogo(bytes, mimetype)) {
                    logoUrl = IdentityNegocioClient.LOGO_PATH
                }
            }

            val sesion = SesionNegocio(
                token = IdentityNegocioClient.negocioToken,
                email = mail,
                nombreMostrar = IdentityNegocioClient.cuentaNegocio?.nombreMostrar ?: nombreTrim,
                establecimientoUuid = uuid,
                tipo = tipo,
                logoUrl = logoUrl,
                dataOrigin = IdentityNegocioClient.cuentaNegocio?.dataOrigin
                    ?: IdentityNegocioClient.establecimientoDataOrigin,
            )
            _sesion.value = sesion
            marcarConectado(uuid)
            persistirSesion(sesion, recordar)
            cargarLogo()
            sincronizarDesdeIdentity()
            _trabajando.value = false
        }
    }

    fun logout() {
        IdentityNegocioClient.desconectar()
        _sesion.value = null
        _logoBytes.value = null
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

    /** Descarga el logo de Identity y lo expone para el header. */
    private fun cargarLogo() {
        viewModelScope.launch {
            _logoBytes.value = IdentityNegocioClient.obtenerLogo()
        }
    }

    /**
     * Re-pulla desde Identity (fuente de verdad) el layout y los camareros al iniciar
     * o restaurar sesión: SQLite hace mirror. El layout reemplaza el local; los
     * camareros ACTIVA se sincronizan a la lista blanca.
     */
    private fun sincronizarDesdeIdentity() {
        viewModelScope.launch {
            IdentityNegocioClient.obtenerLayout()?.let { (salas, mesas) ->
                if (salas.isNotEmpty() || mesas.isNotEmpty()) {
                    app.repository.reemplazarLayout(salas, mesas)
                }
            }
            val miembros = IdentityNegocioClient.listarMiembros()
                .filter { it.estado.equals("activa", ignoreCase = true) }
                .map { it.camareroId }
            app.repository.sincronizarMiembros(miembros)
        }
    }

    /** Lee los bytes y el mimetype de una imagen elegida (galería). */
    private suspend fun leerImagen(uri: Uri): Pair<ByteArray?, String> = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        val mimetype = resolver.getType(uri) ?: "image/webp"
        bytes to mimetype
    }

    /** Rehidrata el [IdentityNegocioClient] con la sesión guardada en Room. */
    private fun hidratarIdentity(sesion: SesionNegocio) {
        // La URL del server no se guarda por sesión: IdentityNegocioClient usa la config de
        // entorno por defecto (dev). Se completa cuando haya server fijo.
        IdentityNegocioClient.negocioToken = sesion.token
        IdentityNegocioClient.establecimientoUuid = sesion.establecimientoUuid
        IdentityNegocioClient.cuentaNegocio = sesion.nombreMostrar?.let { nombre ->
            IdentityCuentaNegocio(
                email = sesion.email.orEmpty(),
                nombreMostrar = nombre,
                tipoEstablecimiento = sesion.tipo?.apiValor(),
                logoUrl = sesion.logoUrl,
                dataOrigin = sesion.dataOrigin,
            )
        }
    }
}
