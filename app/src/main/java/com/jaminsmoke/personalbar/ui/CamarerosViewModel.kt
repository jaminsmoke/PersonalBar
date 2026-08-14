package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.QrParser
import com.jaminsmoke.personalbar.lan.IdentityClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Resultado del alta por QR. */
enum class AltaResultado { OK, QR_INVALIDO, YA_EXISTE }

/** ViewModel de la sección Camareros: lista blanca del establecimiento (v0.1). */
class CamarerosViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val camareros: StateFlow<List<Camarero>> = repository.camareros

    fun altaPorQr(payload: String): AltaResultado {
        val phid = QrParser.parsear(payload) ?: return AltaResultado.QR_INVALIDO
        val creado = repository.altaCamarero(phid.camareroId, phid.credencialId)
        if (!creado) return AltaResultado.YA_EXISTE
        // Best-effort: crea la membresía en Identity si está configurada (v0.1: no-op).
        val establecimientoId = repository.establecimiento.value.idEstable
        viewModelScope.launch {
            IdentityClient.altaMiembro(establecimientoId, phid.camareroId, "staff")
        }
        return AltaResultado.OK
    }

    fun revocar(id: String) {
        repository.revocarCamarero(id)
    }
}
