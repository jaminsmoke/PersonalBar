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
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.IdentityPerfilWebUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LocalSeccion { IDENTIDAD, WEB, FOTOS, HORARIO, APARIENCIA }

data class GaleriaItem(val id: String, val bytes: ByteArray?)

/**
 * Datos del establecimiento (tile Local): identidad, perfil-web, fotos y
 * visibilidad. El horario tiene su propio [com.jaminsmoke.personalbar.ui.gestion.HorarioViewModel].
 */
class LocalViewModel : ViewModel() {
    private val app = PersonalBarApp.get()
    private val repository: BarRepository = app.repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    private val _logoBytes = MutableStateFlow<ByteArray?>(null)
    val logoBytes: StateFlow<ByteArray?> = _logoBytes.asStateFlow()

    private val _webUrl = MutableStateFlow<String?>(null)
    val webUrl: StateFlow<String?> = _webUrl.asStateFlow()

    private val _visibleDirectorio = MutableStateFlow(false)
    val visibleDirectorio: StateFlow<Boolean> = _visibleDirectorio.asStateFlow()

    private val _eslogan = MutableStateFlow("")
    val eslogan: StateFlow<String> = _eslogan.asStateFlow()
    private val _descripcion = MutableStateFlow("")
    val descripcion: StateFlow<String> = _descripcion.asStateFlow()
    private val _direccion = MutableStateFlow("")
    val direccion: StateFlow<String> = _direccion.asStateFlow()
    private val _ciudad = MutableStateFlow("")
    val ciudad: StateFlow<String> = _ciudad.asStateFlow()
    private val _telefono = MutableStateFlow("")
    val telefono: StateFlow<String> = _telefono.asStateFlow()
    private val _emailContacto = MutableStateFlow("")
    val emailContacto: StateFlow<String> = _emailContacto.asStateFlow()
    private val _web = MutableStateFlow("")
    val web: StateFlow<String> = _web.asStateFlow()
    private val _instagram = MutableStateFlow("")
    val instagram: StateFlow<String> = _instagram.asStateFlow()
    private val _facebook = MutableStateFlow("")
    val facebook: StateFlow<String> = _facebook.asStateFlow()
    private val _tiktok = MutableStateFlow("")
    val tiktok: StateFlow<String> = _tiktok.asStateFlow()

    private val _colorPrimario = MutableStateFlow("")
    val colorPrimario: StateFlow<String> = _colorPrimario.asStateFlow()
    private val _tz = MutableStateFlow("Europe/Madrid")
    val tz: StateFlow<String> = _tz.asStateFlow()
    private val _plantilla = MutableStateFlow("estate_hospitality")
    val plantilla: StateFlow<String> = _plantilla.asStateFlow()
    private val _webPublica = MutableStateFlow(true)
    val webPublica: StateFlow<Boolean> = _webPublica.asStateFlow()
    private val _mostrarEquipo = MutableStateFlow(false)
    val mostrarEquipo: StateFlow<Boolean> = _mostrarEquipo.asStateFlow()

    private val _heroBytes = MutableStateFlow<ByteArray?>(null)
    val heroBytes: StateFlow<ByteArray?> = _heroBytes.asStateFlow()
    private val _galeria = MutableStateFlow<List<GaleriaItem>>(emptyList())
    val galeria: StateFlow<List<GaleriaItem>> = _galeria.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            _sesion.value = app.db.barDao().getSesionNegocio()
            _logoBytes.value = IdentityNegocioClient.obtenerLogoEstablecimiento()
            _webUrl.value = IdentityNegocioClient.listarEnlacesPublicos()
                .firstOrNull { it.cubreTipo(TipoEnlacePublico.WEB) }
                ?.urlPublica
            _visibleDirectorio.value = IdentityNegocioClient.obtenerEstablecimiento()?.visibleDirectorio ?: false
            val perfil = IdentityNegocioClient.obtenerPerfilWeb()
            if (perfil != null) aplicarPerfil(perfil)
            _heroBytes.value = IdentityNegocioClient.obtenerHero()
            recargarGaleria()
        }
    }

    private fun aplicarPerfil(perfil: com.jaminsmoke.personalbar.lan.IdentityPerfilWeb) {
        _eslogan.value = perfil.eslogan.orEmpty()
        _descripcion.value = perfil.descripcion.orEmpty()
        _direccion.value = perfil.direccion.orEmpty()
        _ciudad.value = perfil.ciudad.orEmpty()
        _telefono.value = perfil.telefono.orEmpty()
        _emailContacto.value = perfil.emailContacto.orEmpty()
        _web.value = perfil.web.orEmpty()
        _instagram.value = perfil.redes["instagram"].orEmpty()
        _facebook.value = perfil.redes["facebook"].orEmpty()
        _tiktok.value = perfil.redes["tiktok"].orEmpty()
        _colorPrimario.value = perfil.colorPrimario.orEmpty()
        _tz.value = perfil.tz.ifBlank { "Europe/Madrid" }
        _plantilla.value = perfil.plantilla
        _webPublica.value = perfil.webPublica
        _mostrarEquipo.value = perfil.mostrarEquipo
    }

    private suspend fun recargarGaleria() {
        val metas = IdentityNegocioClient.listarGaleria()
        _galeria.value = metas.map { meta ->
            GaleriaItem(id = meta.id, bytes = IdentityNegocioClient.obtenerImagenGaleria(meta.id))
        }
    }

    fun setEslogan(v: String) { _eslogan.value = v }
    fun setDescripcion(v: String) { _descripcion.value = v }
    fun setDireccion(v: String) { _direccion.value = v }
    fun setCiudad(v: String) { _ciudad.value = v }
    fun setTelefono(v: String) { _telefono.value = v }
    fun setEmailContacto(v: String) { _emailContacto.value = v }
    fun setWeb(v: String) { _web.value = v }
    fun setInstagram(v: String) { _instagram.value = v }
    fun setFacebook(v: String) { _facebook.value = v }
    fun setTiktok(v: String) { _tiktok.value = v }
    fun setColorPrimario(v: String) { _colorPrimario.value = v }
    fun setTz(v: String) { _tz.value = v }

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

    fun guardarWeb() {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val redes = buildMap {
                _instagram.value.trim().takeIf { it.isNotEmpty() }?.let { put("instagram", it) }
                _facebook.value.trim().takeIf { it.isNotEmpty() }?.let { put("facebook", it) }
                _tiktok.value.trim().takeIf { it.isNotEmpty() }?.let { put("tiktok", it) }
            }
            val actualizado = IdentityNegocioClient.editarPerfilWeb(
                IdentityPerfilWebUpdate(
                    eslogan = _eslogan.value.trim(),
                    descripcion = _descripcion.value.trim(),
                    direccion = _direccion.value.trim(),
                    ciudad = _ciudad.value.trim(),
                    telefono = _telefono.value.trim(),
                    emailContacto = _emailContacto.value.trim().takeIf { it.contains('@') },
                    web = _web.value.trim(),
                    redes = redes,
                ),
            )
            if (actualizado != null) {
                aplicarPerfil(actualizado)
                _mensaje.value = R.string.local_guardado
            } else {
                _mensaje.value = R.string.perfil_guardar_error
            }
            _trabajando.value = false
        }
    }

    fun guardarApariencia() {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val actualizado = IdentityNegocioClient.editarPerfilWeb(
                IdentityPerfilWebUpdate(
                    colorPrimario = _colorPrimario.value.trim(),
                    tz = _tz.value.trim().ifEmpty { "Europe/Madrid" },
                    webPublica = _webPublica.value,
                    mostrarEquipo = _mostrarEquipo.value,
                ),
            )
            if (actualizado != null) {
                aplicarPerfil(actualizado)
                _mensaje.value = R.string.local_guardado
            } else {
                _mensaje.value = R.string.perfil_guardar_error
            }
            _trabajando.value = false
        }
    }

    fun setWebPublica(valor: Boolean) {
        _webPublica.value = valor
    }

    fun setMostrarEquipo(valor: Boolean) {
        _mostrarEquipo.value = valor
    }

    fun subirHero(uri: Uri) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val (bytes, mimetype) = leerImagen(uri)
            if (bytes != null && IdentityNegocioClient.subirHero(bytes, mimetype) != null) {
                _heroBytes.value = IdentityNegocioClient.obtenerHero()
            } else {
                _mensaje.value = R.string.local_fotos_error
            }
            _trabajando.value = false
        }
    }

    fun borrarHero() {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            if (IdentityNegocioClient.borrarHero()) {
                _heroBytes.value = null
            } else {
                _mensaje.value = R.string.local_fotos_error
            }
            _trabajando.value = false
        }
    }

    fun subirGaleria(uri: Uri) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            val (bytes, mimetype) = leerImagen(uri)
            if (bytes != null && IdentityNegocioClient.subirGaleria(bytes, mimetype)) {
                recargarGaleria()
            } else {
                _mensaje.value = R.string.local_fotos_error
            }
            _trabajando.value = false
        }
    }

    fun borrarGaleria(imagenId: String) {
        _mensaje.value = null
        _trabajando.value = true
        viewModelScope.launch {
            if (IdentityNegocioClient.borrarImagenGaleria(imagenId)) {
                recargarGaleria()
            } else {
                _mensaje.value = R.string.local_fotos_error
            }
            _trabajando.value = false
        }
    }

    private suspend fun leerImagen(uri: Uri): Pair<ByteArray?, String> = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        val mimetype = resolver.getType(uri) ?: "image/webp"
        bytes to mimetype
    }
}
