package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel del editor de carta: catálogo + CRUD con feedback de error. */
class CartaViewModel : ViewModel() {

    private val repository: BarRepository = PersonalBarApp.get().repository

    /** Catálogo canónico del nodo (StateFlow del repositorio). */
    val catalogo: StateFlow<List<Producto>> = repository.catalogo

    /** Error como recurso string (null = sin error). */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    /** @return true si se creó; false si los campos obligatorios quedan vacíos. */
    fun crear(nombre: String, categoria: String, precio: Double): Boolean {
        val ok = repository.crearProducto(nombre, categoria, precio)
        _error.value = if (ok) null else R.string.carta_error_nombre_categoria
        return ok
    }

    /** @return true si se actualizó; false si no existe o los campos quedan vacíos. */
    fun editar(id: String, nombre: String, categoria: String, precio: Double, disponible: Boolean): Boolean {
        val ok = repository.editarProducto(id, nombre, categoria, precio, disponible)
        _error.value = if (ok) null else R.string.carta_error_nombre_categoria
        return ok
    }

    /** Borra el producto (físico; las líneas históricas guardan `nombreProducto` copiado). */
    fun borrar(id: String) {
        repository.borrarProducto(id)
    }

    fun clearError() {
        _error.value = null
    }
}
