package com.jaminsmoke.personalbar.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.data.apiValor
import com.jaminsmoke.personalbar.lan.CambioPasswordResult
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Perfil del establecimiento (local) contra Identity, fuente de verdad. Muestra
 * nombre, tipo, logo, email y UUID; edita nombre+tipo vía `PATCH /v1/establecimientos/{id}`
 * y el logo del local (`POST/GET/DELETE .../logo`). La web pública se enlaza con el
 * `url_publica` del enlace `web` (o el alias legado `ficha_negocio`).
 */
class PerfilEstablecimientoViewModel : ViewModel() {
    private val app = PersonalBarApp.get()
    private val repository: BarRepository = app.repository

    /** Nombre canónico del establecimiento (mirror local del nodo). */
    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento

    /** Conexión activa con Identity (cuenta de negocio logueada en el header). */
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    /** Bytes del logo efectivo del local (local o heredado de la organización). */
    private val _logoBytes = MutableStateFlow<ByteArray?>(null)
    val logoBytes: StateFlow<ByteArray?> = _logoBytes.asStateFlow()

    /** URL pública de la web del negocio (enlace `web` o alias `ficha_negocio`; null si no hay). */
    private val _webUrl = MutableStateFlow<String?>(null)
    val webUrl: StateFlow<String?> = _webUrl.asStateFlow()

    /** Opt-in del dueño para aparecer en el directorio de establecimientos (sin PII). */
    private val _visibleDirectorio = MutableStateFlow(false)
    val visibleDirectorio: StateFlow<Boolean> = _visibleDirectorio.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        cargar()
    }

    /** Carga la sesión persistida, el logo, la URL de la web y el opt-in del directorio. */
    fun cargar() {
        viewModelScope.launch {
            _sesion.value = app.db.barDao().getSesionNegocio()
            _logoBytes.value = IdentityNegocioClient.obtenerLogoEstablecimiento()
            _webUrl.value = IdentityNegocioClient.listarEnlacesPublicos()
                .firstOrNull { it.cubreTipo(TipoEnlacePublico.WEB) }
                ?.urlPublica
            _visibleDirectorio.value = IdentityNegocioClient.obtenerEstablecimiento()?.visibleDirectorio ?: false
        }
    }

    /** Activa/desactiva la visibilidad del establecimiento en el directorio (opt-in). */
    fun editarVisibilidad(visible: Boolean) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val actualizado = IdentityNegocioClient.editarEstablecimiento(visibleDirectorio = visible)
            if (actualizado != null) {
                _visibleDirectorio.value = actualizado.visibleDirectorio ?: visible
            } else {
                _mensaje.value = R.string.perfil_guardar_error
            }
            _trabajando.value = false
        }
    }

    /** Cambia la contraseña de la cuenta de negocio contra Identity (mantiene la sesión). */
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

    /** Renombra el establecimiento en Identity y refleja el cambio en el mirror local. */
    fun editarNombre(nombre: String) {
        val n = nombre.trim()
        if (n.isEmpty()) {
            _mensaje.value = R.string.perfil_nombre_vacio
            return
        }
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val actualizado = IdentityNegocioClient.editarEstablecimiento(nombre = n)
            if (actualizado != null) {
                repository.renombrarEstablecimiento(actualizado.nombre)
            } else {
                _mensaje.value = R.string.perfil_guardar_error
            }
            _trabajando.value = false
        }
    }

    /** Cambia el tipo del establecimiento en Identity y lo persiste en la sesión local. */
    fun editarTipo(tipo: TipoEstablecimiento) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val actualizado = IdentityNegocioClient.editarEstablecimiento(tipo = tipo.apiValor())
            if (actualizado != null) {
                val actual = _sesion.value
                if (actual != null) {
                    val nueva = actual.copy(tipo = tipo)
                    _sesion.value = nueva
                    app.db.barDao().upsertSesionNegocio(nueva)
                }
            } else {
                _mensaje.value = R.string.perfil_guardar_error
            }
            _trabajando.value = false
        }
    }

    /** Sube/reemplaza el logo del local (Identity lo normaliza a 256×256 WebP). */
    fun subirLogo(uri: Uri) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val (bytes, mimetype) = leerImagen(uri)
            if (bytes != null && IdentityNegocioClient.subirLogoEstablecimiento(bytes, mimetype)) {
                _logoBytes.value = IdentityNegocioClient.obtenerLogoEstablecimiento()
            } else {
                _mensaje.value = R.string.perfil_logo_error
            }
            _trabajando.value = false
        }
    }

    /** Borra el override del logo del local (Identity hereda el logo de la organización). */
    fun borrarLogo() {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            if (IdentityNegocioClient.borrarLogoEstablecimiento()) {
                _logoBytes.value = IdentityNegocioClient.obtenerLogoEstablecimiento()
            } else {
                _mensaje.value = R.string.perfil_logo_error
            }
            _trabajando.value = false
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
