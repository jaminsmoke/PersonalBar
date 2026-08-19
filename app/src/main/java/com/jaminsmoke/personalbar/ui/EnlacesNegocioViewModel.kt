package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.lan.IdentityEnlacePublico
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Alias HTTP deprecado de Identity; se persiste y se lista como `web`. */
const val TIPO_ENLACE_WEB_LEGADO = "ficha_negocio"

/**
 * Tipos de enlace público del negocio (valores canónicos que Identity acepta en `tipo`).
 * Dos oficios distintos: web (material promocional) y carta (mesa/servilletero).
 * Un tercer QR futuro se añade aquí; no concatenar rutas sobre `url_publica`.
 */
enum class TipoEnlacePublico(val apiValor: String, val labelRes: Int, val ayudaRes: Int) {
    WEB("web", R.string.enlaces_tipo_web, R.string.enlaces_tipo_web_ayuda),
    CARTA("carta", R.string.enlaces_tipo_carta, R.string.enlaces_tipo_carta_ayuda),
}

/** true si Identity marca el enlace como activo (revocados/rotados se descartan). */
fun IdentityEnlacePublico.estaActivo(): Boolean =
    estado.equals("activo", ignoreCase = true)

/**
 * true si este enlace cubre la tarjeta [tarjeta]: `web` acepta también el alias
 * legado `ficha_negocio` (Identity lo persiste como `web` tras la migración 0011).
 */
fun IdentityEnlacePublico.cubreTipo(tarjeta: TipoEnlacePublico): Boolean {
    if (!estaActivo()) return false
    return when (tarjeta) {
        TipoEnlacePublico.WEB ->
            tipo == TipoEnlacePublico.WEB.apiValor || tipo == TIPO_ENLACE_WEB_LEGADO
        TipoEnlacePublico.CARTA -> tipo == TipoEnlacePublico.CARTA.apiValor
    }
}

/**
 * Panel «Enlaces del negocio»: crear (idempotente), listar, revocar y rotar los
 * enlaces públicos (web + carta) contra Identity. Los enlaces son datos efímeros
 * de Identity (se rotan/revocan y el QR debe estar fresco), así que se consultan
 * directo al cliente — no se espejan en Room.
 */
class EnlacesNegocioViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    /** Cuenta del establecimiento conectada (gate de la pantalla). */
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig

    /** Conectividad de red (para avisar si una acción online no puede completarse). */
    val isOnline: StateFlow<Boolean> = PersonalBarApp.get().conectividad.isOnline

    private val _enlaces = MutableStateFlow<List<IdentityEnlacePublico>>(emptyList())
    val enlaces: StateFlow<List<IdentityEnlacePublico>> = _enlaces.asStateFlow()

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        cargar()
    }

    /** Carga el listado de enlaces desde Identity. */
    fun cargar() {
        if (!repository.identityConfig.value.conectado) {
            _mensaje.value = R.string.enlaces_sin_identity
            return
        }
        _trabajando.value = true
        viewModelScope.launch {
            _enlaces.value = IdentityNegocioClient.listarEnlacesPublicos()
            _trabajando.value = false
        }
    }

    /** Crea el enlace del tipo (idempotente en Identity) y recarga la lista. */
    fun crear(tipo: TipoEnlacePublico) {
        if (!repository.identityConfig.value.conectado) return
        _trabajando.value = true
        viewModelScope.launch {
            IdentityNegocioClient.crearEnlacePublico(tipo.apiValor)
            _enlaces.value = IdentityNegocioClient.listarEnlacesPublicos()
            _trabajando.value = false
        }
    }

    /** Revoca el enlace en Identity y recarga la lista. */
    fun revocar(enlaceId: String) {
        if (!repository.identityConfig.value.conectado) return
        _trabajando.value = true
        viewModelScope.launch {
            IdentityNegocioClient.revocarEnlacePublico(enlaceId)
            _enlaces.value = IdentityNegocioClient.listarEnlacesPublicos()
            _trabajando.value = false
        }
    }

    /** Rota el enlace (el slug anterior pasa a 410) y recarga la lista. */
    fun rotar(enlaceId: String) {
        if (!repository.identityConfig.value.conectado) return
        _trabajando.value = true
        viewModelScope.launch {
            IdentityNegocioClient.rotarEnlacePublico(enlaceId)
            _enlaces.value = IdentityNegocioClient.listarEnlacesPublicos()
            _trabajando.value = false
        }
    }
}
