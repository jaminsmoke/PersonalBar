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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
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

    /** FGS «Local activo» arrancado correctamente (false = nodo degradado sin FGS). */
    private val _fgsOk = MutableStateFlow(true)
    val fgsOk: StateFlow<Boolean> = _fgsOk.asStateFlow()

    /** Lo actualiza BarLanService tras intentar el arranque del FGS. */
    fun setFgsOk(ok: Boolean) {
        _fgsOk.value = ok
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // El nodo ya no arranca aquí: lo arranca/para BarLanService (FGS «Local activo»).
    }

    /**
     * Scope del timeout de sesión. Vive ligado al **proceso** (no al FGS): aunque el
     * FGS no arranque (degradación), el nodo en proceso sigue cortando jornadas
     * sin heartbeat. Se arranca/para con el ciclo del nodo ([startLocal]/[stopLocal]).
     */
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionJob: Job? = null

    /** Arranca el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun startLocal(): Boolean {
        val ok = lanServer.startServer()
        _roomActive.value = lanServer.isRunning
        startSessionTimeout()
        return ok
    }

    /** Para el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun stopLocal() {
        stopSessionTimeout()
        lanServer.stopServer()
        _roomActive.value = false
    }

    /** Timer que auto-inactiva las sesiones sin heartbeat dentro del timeout. */
    private fun startSessionTimeout() {
        sessionJob?.cancel()
        sessionJob = sessionScope.launch {
            while (isActive) {
                delay(SESSION_CHECK_INTERVAL_MS)
                repository.cortarSesionesVencidas(BarRepository.HEARTBEAT_TIMEOUT_MS)
            }
        }
    }

    private fun stopSessionTimeout() {
        sessionJob?.cancel()
        sessionJob = null
    }

    override fun onTerminate() {
        stopLocal()
        sessionScope.cancel()
        super.onTerminate()
    }

    companion object {

        /** Cada cuánto se revisa el timeout (5 s). */
        const val SESSION_CHECK_INTERVAL_MS: Long = 5_000L

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
