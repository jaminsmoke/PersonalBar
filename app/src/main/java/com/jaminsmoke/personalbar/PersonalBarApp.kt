package com.jaminsmoke.personalbar

import android.app.Application
import androidx.room.Room
import com.jaminsmoke.personalbar.data.AppDatabase
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.RoomBarRepository
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.lan.BarLanServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application del nodo de sala. Arranca el servidor LAN en [onCreate] y expone el
 * repositorio (fuente de verdad, persistida en Room). El FGS «Local activo» es un ítem separado.
 */
class PersonalBarApp : Application() {

    val lanServer: BarLanServer by lazy { BarLanServer(repository) }

    /** Base de datos Room del nodo (expuesta para el DAO de sesión y el repositorio). */
    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "personalbar.db",
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    /** Fuente de verdad del nodo: Room con el seed demo solo si la BD está vacía. */
    val repository: BarRepository by lazy {
        val demo = demoData()
        RoomBarRepository(
            db = db,
            establecimientoInicial = demo.establecimiento,
            salasIniciales = demo.salas,
            catalogoInicial = demo.catalogo,
            mesasIniciales = demo.mesas,
            rondasDemo = demo.rondas,
        )
    }

    private val _roomActive = MutableStateFlow(false)
    val roomActive: StateFlow<Boolean> = _roomActive.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // El nodo ya no arranca aquí: lo arranca/para BarLanService (FGS «Local activo»).
    }

    /** Arranca el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun startLocal(): Boolean {
        val ok = lanServer.startServer()
        _roomActive.value = lanServer.isRunning
        return ok
    }

    /** Para el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun stopLocal() {
        lanServer.stopServer()
        _roomActive.value = false
    }

    override fun onTerminate() {
        stopLocal()
        super.onTerminate()
    }

    companion object {
        @Volatile
        private var instance: PersonalBarApp? = null

        fun get(): PersonalBarApp =
            instance ?: error("PersonalBarApp not initialized")
    }
}

/** Datos de la semilla demo v0.1: se siembran solo si la BD está vacía (primera instalación). */
private fun demoData(): DemoData {
    val establecimiento = Establecimiento(idEstable = "local-1", nombre = "La Terraza")
    val salas = listOf(
        Sala(id = "sala-terraza", nombre = "Terraza", orden = 1),
        Sala(id = "sala-interior", nombre = "Interior", orden = 2),
        Sala(id = "sala-barra", nombre = "Barra", orden = 3),
    )
    val catalogo = listOf(
        Producto(id = "cana", nombre = "Caña", categoria = "Bebida"),
        Producto(id = "tinto-verano", nombre = "Tinto de verano", categoria = "Bebida"),
        Producto(id = "croquetas", nombre = "Croquetas", categoria = "Comida"),
        Producto(id = "tostada", nombre = "Tostada con tomate", categoria = "Comida"),
    )
    val mesas = listOf(
        Mesa(id = "mesa-1", salaId = "sala-terraza", indiceZona = 1, numero = 1, forma = MesaForma.REDONDA, capacidad = 2, posX = 40f, posY = 40f),
        Mesa(id = "mesa-2", salaId = "sala-terraza", indiceZona = 2, numero = 2, forma = MesaForma.REDONDA, capacidad = 2, posX = 200f, posY = 40f),
        Mesa(id = "mesa-3", salaId = "sala-terraza", indiceZona = 3, numero = 3, forma = MesaForma.CUADRADA, capacidad = 4, posX = 40f, posY = 200f),
        Mesa(id = "mesa-7", salaId = "sala-terraza", indiceZona = 7, numero = 4, forma = MesaForma.RECTANGULAR, capacidad = 8, posX = 360f, posY = 40f),
    )
    val rondas = listOf(
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
        ),
        Ronda(
            id = "r2",
            mesaId = "T7",
            numero = 2,
            camarero = "Marcos",
            lineas = listOf(
                Linea(productoId = "cana", nombreProducto = "Caña", cantidad = 3),
            ),
        ),
    )
    return DemoData(establecimiento, salas, catalogo, mesas, rondas)
}

private data class DemoData(
    val establecimiento: Establecimiento,
    val salas: List<Sala>,
    val catalogo: List<Producto>,
    val mesas: List<Mesa>,
    val rondas: List<Ronda>,
)
