package com.jaminsmoke.personalbar.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.SesionEstado
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.data.apiValor
import com.jaminsmoke.personalbar.data.tipoDesdeApi
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
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
 *
 * El **estado de sesión vive en [PersonalBarApp]** (proceso): restauración,
 * validez local (`validaHasta`, 7 días) y revalidación contra el VPS funcionan
 * también con la app en segundo plano (FGS «Local activo»). Este ViewModel es la
 * fachada de UI: expone `sesion`/`logoBytes` delegados a la app, y el feedback
 * (`trabajando`/`mensaje`) del flujo de login/registro.
 */
class SesionViewModel : ViewModel() {
    private val app = PersonalBarApp.get()

    /** Sesión de negocio actual (fuente: [PersonalBarApp.sesion]). */
    val sesion: StateFlow<SesionNegocio?> = app.sesion

    /** Estado derivado de la sesión: solo [SesionEstado.VALIDA] abre el gate del puesto. */
    val sesionEstado: StateFlow<SesionEstado> = app.sesionEstado

    /** Bytes del logo descargado de Identity (fuente: [PersonalBarApp.logoBytes]). */
    val logoBytes: StateFlow<ByteArray?> = app.logoBytes

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    init {
        // La restauración de la sesión persistida («Recuérdame») la hace la app en
        // onCreate (proceso); aquí solo nos aseguramos de que se dispare si el
        // proceso ya estaba vivo cuando se creó el ViewModel.
        app.restaurarSesion()
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
                validaHasta = System.currentTimeMillis() + PersonalBarApp.SESION_VALIDEZ_MS,
            )
            app.setSesion(sesion, recordar)
            marcarConectado(uuid)
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
                validaHasta = System.currentTimeMillis() + PersonalBarApp.SESION_VALIDEZ_MS,
            )
            app.setSesion(sesion, recordar)
            marcarConectado(uuid)
            _trabajando.value = false
        }
    }

    fun logout() {
        app.cerrarSesion()
    }

    /** Refleja en el repo que hay una cuenta de negocio vinculada (lo usa Camareros). */
    private fun marcarConectado(uuid: String) {
        app.repository.setIdentityConfig(
            IdentityConfig(conectado = true, establecimientoUuid = uuid)
        )
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
