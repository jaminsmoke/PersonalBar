package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.lan.LayoutBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel de Ajustes: establecimiento y CRUD de salas. */
class AjustesViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento
    val salas: StateFlow<List<Sala>> = repository.salas

    /** Último mensaje de feedback como id de recurso de string (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    fun crearSala(nombre: String) {
        val ok = repository.crearSala(nombre)
        _mensaje.value = if (ok) R.string.ajustes_sala_creada else R.string.ajustes_error_nombre
        if (ok) respaldar()
    }

    fun renombrarSala(id: String, nombre: String) {
        val ok = repository.renombrarSala(id, nombre)
        _mensaje.value = if (ok) R.string.ajustes_sala_renombrada else R.string.ajustes_error_nombre
        if (ok) respaldar()
    }

    fun eliminarSala(id: String) {
        val ok = repository.eliminarSala(id)
        _mensaje.value = if (ok) R.string.ajustes_sala_eliminada else R.string.ajustes_error_eliminar
        if (ok) respaldar()
    }

    /** Respalda el layout en Identity (best-effort, si conectado). */
    private fun respaldar() {
        viewModelScope.launch { LayoutBackup.respaldar(repository) }
    }
}
