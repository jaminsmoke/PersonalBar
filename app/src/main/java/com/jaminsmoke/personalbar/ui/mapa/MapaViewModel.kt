package com.jaminsmoke.personalbar.ui.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.MesaVisualStatus
import com.jaminsmoke.personalbar.data.Reserva
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.derivarEstadoMesas
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
    fun crearSala(nombre: String): Boolean = repository.crearSala(nombre)
    fun renombrarSala(id: String, nombre: String): Boolean = repository.renombrarSala(id, nombre)
    fun eliminarSala(id: String): Boolean = repository.eliminarSala(id)

    // ── Mesas ──
    fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean =
        repository.crearMesa(salaId, forma, capacidad, alias)

    fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean =
        repository.editarMesa(mesaId, alias, capacidad, forma)

    fun borrarMesa(mesaId: String): Boolean = repository.borrarMesa(mesaId)
    fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean = repository.moverMesa(mesaId, posX, posY)
    fun girarMesa(mesaId: String): Boolean = repository.girarMesa(mesaId)

    // ── Reservas / bloqueos ──
    fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean = repository.reservar(mesaId, nombre, paraEpoch)
    fun cancelarReserva(mesaId: String): Boolean = repository.cancelarReserva(mesaId)
    fun bloquearMesa(mesaId: String): Boolean = repository.bloquearMesa(mesaId)
    fun desbloquearMesa(mesaId: String): Boolean = repository.desbloquearMesa(mesaId)
}
