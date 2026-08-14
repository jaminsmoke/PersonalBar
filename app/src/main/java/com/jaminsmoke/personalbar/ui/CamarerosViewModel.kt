package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.AltaPendiente
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.Invitacion
import com.jaminsmoke.personalbar.data.InvitacionEstado
import com.jaminsmoke.personalbar.data.Phid1
import com.jaminsmoke.personalbar.data.QrKey
import com.jaminsmoke.personalbar.data.QrParser
import com.jaminsmoke.personalbar.data.QrVerificador
import com.jaminsmoke.personalbar.lan.IdentityCamareroClient
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resultado del alta por QR (sincrónico). La verificación remota va en background. */
enum class AltaResultado { OK, QR_INVALIDO, YA_EXISTE }

/** ViewModel de la sección Camareros: lista blanca, invitaciones por email y espejo de Identity. */
class CamarerosViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val camareros: StateFlow<List<Camarero>> = repository.camareros
    val identityConfig: StateFlow<IdentityConfig> = repository.identityConfig
    val invitaciones: StateFlow<List<Invitacion>> = repository.invitaciones

    /** Conectividad de red (para deshabilitar acciones online sin conexión). */
    val isOnline: StateFlow<Boolean> = PersonalBarApp.get().conectividad.isOnline

    private val _trabajando = MutableStateFlow(false)
    val trabajando: StateFlow<Boolean> = _trabajando.asStateFlow()

    /** Último mensaje de feedback como id de recurso (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    /**
     * Alta por QR. Con Identity conectado, delega en `POST /miembros/qr` (el server
     * verifica la firma); si la rechaza, avisa y NO da de alta local. Sin Identity,
     * conserva el comportamiento local v0.1.
     */
    fun altaPorQr(payload: String): AltaResultado {
        val phid = QrParser.parsear(payload)
            ?: return AltaResultado.QR_INVALIDO.also { _mensaje.value = R.string.camareros_qr_invalido }
        if (repository.identityConfig.value.conectado) {
            if (!isOnline.value) {
                // Offline: verificar localmente contra la clave pública cacheada.
                return altaOffline(payload, phid)
            }
            _trabajando.value = true
            viewModelScope.launch {
                val ok = IdentityNegocioClient.altaPorQr(payload, "staff")
                refrescarClaveQr()
                _trabajando.value = false
                if (!ok) {
                    _mensaje.value = R.string.camareros_qr_rechazado
                    return@launch
                }
                _mensaje.value = null
                // Bar recoge la info de la cuenta desde Identity (no la edita):
                // el nombre/email se guardan cuando el server los expone.
                repository.altaCamarero(phid.camareroId, phid.credencialId)
            }
            return AltaResultado.OK
        }
        return if (repository.altaCamarero(phid.camareroId, phid.credencialId)) {
            _mensaje.value = null
            AltaResultado.OK
        } else {
            _mensaje.value = R.string.camareros_ya_existe
            AltaResultado.YA_EXISTE
        }
    }

    /**
     * Alta offline: verifica la firma Ed25519 del QR contra la clave pública de
     * Identity cacheada y, si es válida, da de alta localmente y registra el alta
     * como pendiente de subir a Identity al reconectar.
     */
    private fun altaOffline(payload: String, phid: Phid1): AltaResultado {
        val clave = repository.qrKey.value
        if (clave == null || clave.publicKey.isBlank()) {
            _mensaje.value = R.string.camareros_qr_sin_clave
            return AltaResultado.QR_INVALIDO
        }
        if (!QrVerificador.verificar(payload, clave.publicKey)) {
            _mensaje.value = R.string.camareros_qr_rechazado
            return AltaResultado.QR_INVALIDO
        }
        if (!repository.altaCamarero(phid.camareroId, phid.credencialId)) {
            _mensaje.value = R.string.camareros_ya_existe
            return AltaResultado.YA_EXISTE
        }
        repository.registrarAltaPendiente(AltaPendiente(camareroId = phid.camareroId, payload = payload))
        _mensaje.value = null
        return AltaResultado.OK
    }

    /** Cachea la clave pública Ed25519 de Identity (best-effort, solo online). */
    fun refrescarClaveQr() {
        if (!repository.identityConfig.value.conectado || !isOnline.value) return
        viewModelScope.launch {
            val key = IdentityCamareroClient.clavePublicaQr() ?: return@launch
            repository.guardarClaveQr(
                QrKey(keyId = key.keyId, publicKey = key.publicKey, algorithm = key.algorithm.ifBlank { "Ed25519" })
            )
        }
    }

    /** Sube las altas offline pendientes a Identity (membresia) y las limpia. */
    fun sincronizarAltasPendientes() {
        if (!repository.identityConfig.value.conectado || !isOnline.value) return
        viewModelScope.launch {
            val pendientes = repository.altasPendientes.value.toList()
            pendientes.forEach { alta ->
                if (IdentityNegocioClient.altaPorQr(alta.payload, "staff")) {
                    repository.eliminarAltaPendiente(alta.camareroId)
                }
            }
        }
    }

    /** Revoca localmente y, si hay Identity, refleja la revocación en el server. */
    fun revocar(id: String) {
        repository.revocarCamarero(id)
        if (repository.identityConfig.value.conectado) {
            viewModelScope.launch { IdentityNegocioClient.revocarMiembro(id) }
        }
    }

    /** Canal email: valida el email en Identity y crea la invitación (envía el correo). */
    fun invitarPorEmail(email: String) {
        val e = email.trim()
        if (e.isEmpty()) {
            _mensaje.value = R.string.camareros_email_vacio
            return
        }
        if (!repository.identityConfig.value.conectado) {
            _mensaje.value = R.string.camareros_sin_identity
            return
        }
        _trabajando.value = true
        viewModelScope.launch {
            val camarero = IdentityNegocioClient.buscarCamareroPorEmail(e)
            if (camarero == null) {
                _trabajando.value = false
                _mensaje.value = R.string.camareros_email_no_encontrado
                return@launch
            }
            // Bar recoge la info de la cuenta (no la edita): guarda nombre/email
            // si el camarero aún no está en la lista blanca.
            val nombreCompleto = listOf(camarero.nombre, camarero.apellidos)
                .filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            if (repository.camareros.value.none { it.id == camarero.id }) {
                repository.altaCamarero(camarero.id, null, nombreCompleto, camarero.email.ifBlank { null })
            }
            val invitacion = IdentityNegocioClient.crearInvitacion(e, "staff")
            _trabajando.value = false
            if (invitacion == null) {
                _mensaje.value = R.string.camareros_invitacion_error
            } else {
                repository.registrarInvitacion(
                    Invitacion(
                        id = invitacion.id,
                        email = invitacion.email,
                        rol = invitacion.rol,
                        estado = InvitacionEstado.PENDIENTE,
                        expiraEn = invitacion.expiraEn,
                    )
                )
                _mensaje.value = R.string.camareros_invitacion_enviada
            }
        }
    }

    /** Revoca una invitación local y en Identity (si conectado). */
    fun revocarInvitacion(id: String) {
        if (repository.revocarInvitacionLocal(id)) {
            viewModelScope.launch { IdentityNegocioClient.revocarInvitacion(id) }
        }
    }

    /** Espejo: trae los miembros ACTIVA de Identity y los añade a la lista blanca local. */
    fun sincronizar() {
        if (!repository.identityConfig.value.conectado) return
        _trabajando.value = true
        viewModelScope.launch {
            refrescarClaveQr()
            val miembros = IdentityNegocioClient.listarMiembros()
                .filter { it.estado.equals("activa", ignoreCase = true) }
                .map { it.camareroId }
            repository.sincronizarMiembros(miembros)
            sincronizarAltasPendientes()
            _trabajando.value = false
            _mensaje.value = R.string.camareros_sincronizados
        }
    }
}
