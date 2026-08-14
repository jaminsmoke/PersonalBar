package com.jaminsmoke.personalbar

import android.app.Application
import androidx.room.Room
import com.jaminsmoke.personalbar.data.AppDatabase
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.RoomBarRepository
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.lan.BarLanServer
import com.jaminsmoke.personalbar.lan.Conectividad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application del nodo de sala. Arranca el servidor LAN en [onCreate] y expone el
 * repositorio (fuente de verdad, persistida en Room). El FGS «Local activo» es un ítem separado.
 */
class PersonalBarApp : Application() {

    val lanServer: BarLanServer by lazy { BarLanServer(repository) }

    /** Detector de conectividad (para degradar acciones online sin red). */
    val conectividad: Conectividad by lazy { Conectividad(applicationContext) }

    /** Base de datos Room del nodo (expuesta para el DAO de sesión y el repositorio). */
    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "personalbar.db",
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build()
    }

    /**
     * Fuente de verdad del nodo. En primera instalación siembra las salas generales
     * por defecto (Barra, Interior, Terraza) con 4 mesas cada una y el catálogo
     * canónico; las colas parten vacías (sin rondas demo). El dueño puede editar
     * salas/mesas y la carta se gestionará cuando exista el editor de catálogo.
     */
    val repository: BarRepository by lazy {
        RoomBarRepository(
            db = db,
            salasIniciales = salasPorDefecto(),
            mesasIniciales = mesasPorDefecto(),
            catalogoInicial = catalogoPorDefecto(),
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

/** Salas generales por defecto (editables por el dueño). */
private fun salasPorDefecto(): List<Sala> = listOf(
    Sala(id = "sala-barra", nombre = "Barra", orden = 1),
    Sala(id = "sala-interior", nombre = "Interior", orden = 2),
    Sala(id = "sala-terraza", nombre = "Terraza", orden = 3),
)

/** 4 mesas por defecto en cada sala (editables). IDs `mesa-N` secuenciales para no colisionar con la secuencia del repo. */
internal fun mesasPorDefecto(): List<Mesa> {
    val salas = listOf("sala-barra", "sala-interior", "sala-terraza")
    val mesas = mutableListOf<Mesa>()
    var numero = 0
    var seq = 0
    for ((orden, salaId) in salas.withIndex()) {
        for (indice in 1..4) {
            numero++
            seq++
            mesas += Mesa(
                id = "mesa-$seq",
                salaId = salaId,
                indiceZona = indice,
                numero = numero,
                forma = if (indice <= 2) MesaForma.REDONDA else MesaForma.CUADRADA,
                capacidad = if (indice <= 2) 2 else 4,
                posX = posicionX(indice),
                posY = posicionY(orden),
            )
        }
    }
    return mesas
}

/** Cada sala ocupa una banda horizontal del canvas 2600×2000; las 4 mesas se espacian en fila. */
private fun posicionX(indice: Int): Float = 120f + (indice - 1) * 480f

private fun posicionY(salaOrden: Int): Float = 120f + salaOrden * 640f

/**
 * Catálogo canónico por defecto del nodo (v0.1). No es «datos demo»: mientras no
 * exista el editor de carta, el nodo necesita al menos un producto de Bebida y otro
 * de Comida para partir las rondas por destino. Se sustituirá por el catálogo
 * gestionado cuando aterrice el editor (ítem de seguimiento).
 */
private fun catalogoPorDefecto(): List<Producto> = listOf(
    Producto(id = "cana", nombre = "Caña", categoria = "Bebida"),
    Producto(id = "tinto-verano", nombre = "Tinto de verano", categoria = "Bebida"),
    Producto(id = "croquetas", nombre = "Croquetas", categoria = "Comida"),
    Producto(id = "tostada", nombre = "Tostada con tomate", categoria = "Comida"),
)
