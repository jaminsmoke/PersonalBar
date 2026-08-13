package com.jaminsmoke.personalbar

import android.app.Application
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.InMemoryBarRepository
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.lan.BarLanServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application del nodo de sala. Arranca el servidor LAN en [onCreate] y expone el
 * repositorio (fuente de verdad). El FGS «Sala activa» es un ítem separado.
 */
class PersonalBarApp : Application() {

    val lanServer: BarLanServer by lazy { BarLanServer() }

    /** Fuente de verdad del nodo (en memoria en v0.1; Room será otra implementación). */
    val repository: BarRepository by lazy { demoRepository() }

    private val _roomActive = MutableStateFlow(false)
    val roomActive: StateFlow<Boolean> = _roomActive.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        _roomActive.value = lanServer.startServer()
    }

    override fun onTerminate() {
        lanServer.stopServer()
        _roomActive.value = false
        super.onTerminate()
    }

    companion object {
        @Volatile
        private var instance: PersonalBarApp? = null

        fun get(): PersonalBarApp =
            instance ?: error("PersonalBarApp not initialized")
    }
}

/** Semilla demo v0.1: catálogo, mesas y dos rondas para poblar las colas. */
private fun demoRepository(): BarRepository {
    val catalogo = listOf(
        Producto(id = "cana", nombre = "Caña", categoria = "Bebida"),
        Producto(id = "tinto-verano", nombre = "Tinto de verano", categoria = "Bebida"),
        Producto(id = "croquetas", nombre = "Croquetas", categoria = "Comida"),
        Producto(id = "tostada", nombre = "Tostada con tomate", categoria = "Comida"),
    )
    val mesas = listOf(
        Mesa(zona = "Terraza", indiceZona = 3),
        Mesa(zona = "Terraza", indiceZona = 7),
    )
    val repo = InMemoryBarRepository(catalogoInicial = catalogo, mesasIniciales = mesas)
    repo.crearRonda(
        Ronda(
            id = "r1",
            mesaId = "T3",
            numero = 1,
            camarero = "Lucía",
            lineas = listOf(
                Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 2),
                Linea(productoId = "tinto-verano", nombreProducto = "Tinto de verano", cantidad = 1),
                Linea(productoId = "croquetas", nombreProducto = "Croquetas", cantidad = 1),
                Linea(productoId = "tostada", nombreProducto = "Tostada con tomate", cantidad = 2),
            ),
        )
    )
    repo.crearRonda(
        Ronda(
            id = "r2",
            mesaId = "T7",
            numero = 2,
            camarero = "Marcos",
            lineas = listOf(
                Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 3),
            ),
        )
    )
    return repo
}
