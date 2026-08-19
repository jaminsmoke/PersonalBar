package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.GrupoModificador
import com.jaminsmoke.personalbar.data.OpcionModificador
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.ProductoGrupo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel del editor de carta: catálogo + grupos de modificadores + CRUD con feedback de error. */
class CartaViewModel : ViewModel() {

    private val repository: BarRepository = PersonalBarApp.get().repository

    /** Catálogo canónico del nodo (StateFlow del repositorio). */
    val catalogo: StateFlow<List<Producto>> = repository.catalogo

    /** Outbox de carta pendiente de Identity (banner «publicando en la web»). */
    val operacionesCatalogo = repository.operacionesCatalogo

    /** Grupos de modificadores (locales del nodo). */
    val gruposModificador: StateFlow<List<GrupoModificador>> = repository.gruposModificador

    /** Opciones de los grupos de modificadores. */
    val opcionesModificador: StateFlow<List<OpcionModificador>> = repository.opcionesModificador

    /** Asignación N:M producto ↔ grupo. */
    val productoGrupo: StateFlow<List<ProductoGrupo>> = repository.productoGrupo

    /** Error como recurso string (null = sin error). */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    /** @return true si se creó; false si los campos obligatorios quedan vacíos. */
    fun crear(nombre: String, categoria: String, precio: Double, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val ok = repository.crearProducto(nombre, categoria, precio, subfamilia, permiteNota, descripcion)
        _error.value = if (ok) null else R.string.carta_error_nombre_categoria
        return ok
    }

    /** @return true si se actualizó; false si no existe o los campos quedan vacíos. */
    fun editar(id: String, nombre: String, categoria: String, precio: Double, disponible: Boolean, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val ok = repository.editarProducto(id, nombre, categoria, precio, disponible, subfamilia, permiteNota, descripcion)
        _error.value = if (ok) null else R.string.carta_error_nombre_categoria
        return ok
    }

    /** Borra el producto (físico; las líneas históricas guardan `nombreProducto` copiado). */
    fun borrar(id: String) {
        repository.borrarProducto(id)
    }

    // ── Grupos de modificadores ─────────────────────────────────────────────

    fun crearGrupo(nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val ok = repository.crearGrupoModificador(nombre, multiple, obligatorio)
        _error.value = if (ok) null else R.string.carta_error_grupo_vacio
        return ok
    }

    fun editarGrupo(id: String, nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val ok = repository.editarGrupoModificador(id, nombre, multiple, obligatorio)
        _error.value = if (ok) null else R.string.carta_error_grupo_vacio
        return ok
    }

    fun borrarGrupo(id: String) {
        repository.borrarGrupoModificador(id)
    }

    fun crearOpcion(grupoId: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val ok = repository.crearOpcionModificador(grupoId, nombre, deltaPrecio, alias)
        _error.value = if (ok) null else R.string.carta_error_opcion_vacia
        return ok
    }

    fun editarOpcion(id: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val ok = repository.editarOpcionModificador(id, nombre, deltaPrecio, alias)
        _error.value = if (ok) null else R.string.carta_error_opcion_vacia
        return ok
    }

    fun borrarOpcion(id: String) {
        repository.borrarOpcionModificador(id)
    }

    fun asignarGrupo(productoId: String, grupoId: String) {
        repository.asignarGrupoProducto(productoId, grupoId)
    }

    fun desasignarGrupo(productoId: String, grupoId: String) {
        repository.desasignarGrupoProducto(productoId, grupoId)
    }

    fun clearError() {
        _error.value = null
    }
}
