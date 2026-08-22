package com.jaminsmoke.personalbar.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.MesaVisualStatus
import com.jaminsmoke.personalbar.data.Reserva
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.Zona
import com.jaminsmoke.personalbar.data.ZonaColor
import com.jaminsmoke.personalbar.data.derivarEstadoMesas
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.MesaCfcItem
import com.jaminsmoke.personalbar.lan.LayoutBackup
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ViewModel del mapa de mesas: fuente de verdad del layout en Bar. */
class MapaViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val mesas: StateFlow<List<Mesa>> = repository.mesas
    val salas: StateFlow<List<Sala>> = repository.salas
    val zonas: StateFlow<List<Zona>> = repository.zonas
    val camareros: StateFlow<List<Camarero>> = repository.camareros
    val rondas: StateFlow<List<Ronda>> = repository.rondas
    val bebida: StateFlow<List<Ticket>> = repository.bebidaQueue
    val comida: StateFlow<List<Ticket>> = repository.comidaQueue

    private data class SalaDinamica(
        val rondas: List<Ronda>,
        val bebida: List<Ticket>,
        val comida: List<Ticket>,
        val reservas: List<Reserva>,
    )

    /** Estado visual derivado por mesa (id → LIBRE/OCUPADA/EN_COCINA/RESERVADA/BLOQUEADA). */
    val estados: StateFlow<Map<String, MesaVisualStatus>> = combine(
        repository.mesas,
        repository.salas,
        combine(repository.rondas, repository.bebidaQueue, repository.comidaQueue, repository.reservas) { r, b, c, res ->
            SalaDinamica(r, b, c, res)
        },
    ) { mesas, salas, din ->
        derivarEstadoMesas(mesas, salas, din.rondas, din.bebida, din.comida, din.reservas)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _salaSeleccionada = MutableStateFlow<String?>(null)
    val salaSeleccionada: StateFlow<String?> = _salaSeleccionada.asStateFlow()

    fun setSala(id: String?) { _salaSeleccionada.value = id }

    // ── Salas ──
    fun crearSala(nombre: String): Boolean =
        repository.crearSala(nombre).also { if (it) respaldar() }

    fun renombrarSala(id: String, nombre: String): Boolean =
        repository.renombrarSala(id, nombre).also { if (it) respaldar() }

    fun eliminarSala(id: String): Boolean =
        repository.eliminarSala(id).also { if (it) respaldar() }

    // ── Mesas ──
    fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean =
        repository.crearMesa(salaId, forma, capacidad, alias).also { if (it) respaldar() }

    fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean =
        repository.editarMesa(mesaId, alias, capacidad, forma).also { if (it) respaldar() }

    fun borrarMesa(mesaId: String): Boolean =
        repository.borrarMesa(mesaId).also { if (it) respaldar() }

    fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean =
        repository.moverMesa(mesaId, posX, posY).also { if (it) respaldar() }

    fun girarMesa(mesaId: String): Boolean =
        repository.girarMesa(mesaId).also { if (it) respaldar() }

    // ── Zonas ──
    fun crearZona(
        salaId: String,
        nombre: String,
        color: ZonaColor,
        posX: Float,
        posY: Float,
        ancho: Float,
        alto: Float,
        camareroId: String?,
    ): Boolean =
        repository.crearZona(salaId, nombre, color, posX, posY, ancho, alto, camareroId).also { if (it) respaldar() }

    fun editarZona(zonaId: String, nombre: String, color: ZonaColor, camareroId: String?): Boolean =
        repository.editarZona(zonaId, nombre, color, camareroId).also { if (it) respaldar() }

    fun moverZona(zonaId: String, posX: Float, posY: Float): Boolean =
        repository.moverZona(zonaId, posX, posY).also { if (it) respaldar() }

    fun redimensionarZona(zonaId: String, ancho: Float, alto: Float): Boolean =
        repository.redimensionarZona(zonaId, ancho, alto).also { if (it) respaldar() }

    fun borrarZona(zonaId: String): Boolean =
        repository.borrarZona(zonaId).also { if (it) respaldar() }

    fun asignarCamareroZona(zonaId: String, camareroId: String?): Boolean =
        repository.asignarCamareroZona(zonaId, camareroId).also { if (it) respaldar() }

    /** Respalda el layout en Identity y sincroniza mesas CFC (best-effort). */
    private fun respaldar() {
        viewModelScope.launch {
            LayoutBackup.respaldar(repository)
            sincronizarMesasCfcBestEffort()
        }
    }

    /**
     * Sincroniza el conjunto de mesas públicas con Identity tras cada mutación
     * del layout. Best-effort: si no hay sesión o falla, se ignora silenciosamente.
     */
    private suspend fun sincronizarMesasCfcBestEffort() {
        if (!IdentityNegocioClient.conectado) return
        val salasMap = repository.salas.value.associateBy { it.id }
        val mesasActuales = repository.mesas.value
        val items = mesasActuales.map { mesa ->
            val salaNombre = salasMap[mesa.salaId]?.nombre ?: ""
            MesaCfcItem(
                mesaUuid = mesa.mesaUuid,
                etiqueta = mesa.nombreVisible(salaNombre),
            )
        }
        IdentityNegocioClient.sincronizarMesasCfc(items)
    }

    // ── Reservas / bloqueos ──
    fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean = repository.reservar(mesaId, nombre, paraEpoch)
    fun cancelarReserva(mesaId: String): Boolean = repository.cancelarReserva(mesaId)
    fun bloquearMesa(mesaId: String): Boolean = repository.bloquearMesa(mesaId)
    fun desbloquearMesa(mesaId: String): Boolean = repository.desbloquearMesa(mesaId)
}
