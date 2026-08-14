package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Sala
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel de Ajustes: establecimiento y CRUD de salas. */
class AjustesViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    val establecimiento: StateFlow<Establecimiento> = repository.establecimiento
    val salas: StateFlow<List<Sala>> = repository.salas

    /** Último mensaje de feedback como id de recurso de string (null = sin mensaje). */
    private val _mensaje = MutableStateFlow<Int?>(null)
    val mensaje: StateFlow<Int?> = _mensaje.asStateFlow()

    fun crearSala(nombre: String) {
        _mensaje.value =
            if (repository.crearSala(nombre)) R.string.ajustes_sala_creada
            else R.string.ajustes_error_nombre
    }

    fun renombrarSala(id: String, nombre: String) {
        _mensaje.value =
            if (repository.renombrarSala(id, nombre)) R.string.ajustes_sala_renombrada
            else R.string.ajustes_error_nombre
    }

    fun eliminarSala(id: String) {
        _mensaje.value =
            if (repository.eliminarSala(id)) R.string.ajustes_sala_eliminada
            else R.string.ajustes_error_eliminar
    }
}
