package com.jaminsmoke.personalbar

import android.app.Application
import androidx.room.Room
import com.jaminsmoke.personalbar.data.AppDatabase
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.IdentityConfig
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.RoomBarRepository
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.SesionEstado
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.apiValor
import com.jaminsmoke.personalbar.data.sesionEstadoDe
import com.jaminsmoke.personalbar.lan.BarLanServer
import com.jaminsmoke.personalbar.lan.Conectividad
import com.jaminsmoke.personalbar.lan.IdentityCuentaNegocio
import com.jaminsmoke.personalbar.lan.IdentityNegocioClient
import com.jaminsmoke.personalbar.lan.toInvitacion
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
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

    // ── Sesión de negocio (modelo de sesión) ─────────────────────────────────
    // Vive en el proceso (no en un ViewModel) para que la revalidación funcione
    // también con la app en segundo plano (FGS «Local activo» mantiene el proceso).
    // [SesionViewModel] es la fachada de UI sobre estos flujos.

    private val _sesion = MutableStateFlow<SesionNegocio?>(null)
    val sesion: StateFlow<SesionNegocio?> = _sesion.asStateFlow()

    /** Bytes del logo descargado de Identity (null = sin logo o aún no cargado). */
    private val _logoBytes = MutableStateFlow<ByteArray?>(null)
    val logoBytes: StateFlow<ByteArray?> = _logoBytes.asStateFlow()

    /** Estado derivado de la sesión (consume el gate de la raíz y el header). */
    private val _sesionEstado = MutableStateFlow(SesionEstado.SIN_SESION)
    val sesionEstado: StateFlow<SesionEstado> = _sesionEstado.asStateFlow()

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
        // Observar la sesión y derivar su estado (SIN_SESION → VALIDA/CADUCADA/INVALIDA).
        sessionScope.launch {
            _sesion.collect { _sesionEstado.value = sesionEstadoDe(it, System.currentTimeMillis()) }
        }
        // Restaurar la sesión persistida («Recuérdame») con su validez local.
        restaurarSesion()
        // El nodo ya no arranca aquí: lo arranca/para BarLanService (FGS «Local activo»).
    }

    /**
     * Scope del timeout de sesión. Vive ligado al **proceso** (no al FGS): aunque el
     * FGS no arranque (degradación), el nodo en proceso sigue cortando jornadas
     * sin heartbeat. Se arranca/para con el ciclo del nodo ([startLocal]/[stopLocal]).
     */
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sessionJob: Job? = null

    private fun actualizarSesionEstado() {
        _sesionEstado.value = sesionEstadoDe(_sesion.value, System.currentTimeMillis())
    }

    /** Scope del timer de revalidación de la sesión (24 h). Vive ligado al proceso;
     *  se arranca/para con el ciclo del nodo ([startLocal]/[stopLocal]), igual que
     *  el timeout de sesiones: con el FGS activo el proceso sigue vivo en segundo
     *  plano y la revalidación continúa. */
    private val revalidacionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var revalidacionJob: Job? = null

    /** Scope del proyector de oficio (libro de oficio → Identity). Vive ligado al
     *  proceso; se arranca/para con el ciclo del nodo ([startLocal]/[stopLocal]). */
    private val proyeccionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var proyeccionJob: Job? = null

    /** Arranca el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun startLocal(): Boolean {
        val ok = lanServer.startServer()
        _roomActive.value = lanServer.isRunning
        startSessionTimeout()
        startProyeccionOficio()
        startRevalidacionSesion()
        return ok
    }

    /** Para el nodo LAN y sincroniza [roomActive]. Lo invoca BarLanService. */
    fun stopLocal() {
        stopProyeccionOficio()
        stopRevalidacionSesion()
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

    /**
     * Proyector del libro de oficio: drena la cola persistente de eventos de
     * servicio hacia Identity cuando la cuenta de negocio está vinculada.
     * Éxito → borra la fila (idempotencia por `evento_id` en el server); fallo
     * → la deja para el siguiente intento. Nunca bloquea el camino LAN de
     * rondas/colas: la emisión es asíncrona y best-effort.
     */
    private fun startProyeccionOficio() {
        proyeccionJob?.cancel()
        proyeccionJob = proyeccionScope.launch {
            while (isActive) {
                delay(OFICIO_PROYECCION_INTERVALO_MS)
                if (!IdentityNegocioClient.conectado) continue
                val pendientes = repository.serviciosPendientes.value.toList()
                pendientes.forEach { servicio ->
                    if (IdentityNegocioClient.registrarServicio(
                            camareroId = servicio.camareroId,
                            eventoId = servicio.eventoId,
                            tipo = servicio.tipo,
                            cantidad = servicio.cantidad,
                        )
                    ) {
                        repository.eliminarServicioPendiente(servicio.eventoId)
                    }
                }
            }
        }
    }

    private fun stopProyeccionOficio() {
        proyeccionJob?.cancel()
        proyeccionJob = null
    }

    // ── Sesión de negocio: restauración, alta y validez ─────────────────────

    /** Restaura la sesión persistida («Recuérdame») y su validez local. No revalida
     *  aquí: el timer ([startRevalidacionSesion]) lo hace al arrancar el nodo. */
    fun restaurarSesion() {
        sessionScope.launch {
            val guardada = db.barDao().getSesionNegocio()
            if (guardada?.token != null) {
                hidratarIdentity(guardada)
                _sesion.value = guardada
                _sesionEstado.value = sesionEstadoDe(guardada, System.currentTimeMillis())
                sessionScope.launch { _logoBytes.value = IdentityNegocioClient.obtenerLogo() }
                sincronizarDesdeIdentity()
            }
        }
    }

    /** Sesión tras login/registro exitoso: hidrata Identity, la persiste (solo con
     *  «Recuérdame») y dispara logo + mirror. [SesionViewModel] construye la sesión
     *  con `validaHasta = now + 7d`. */
    fun setSesion(sesion: SesionNegocio, recordar: Boolean) {
        hidratarIdentity(sesion)
        _sesion.value = sesion
        _sesionEstado.value = sesionEstadoDe(sesion, System.currentTimeMillis())
        sessionScope.launch {
            if (recordar) db.barDao().upsertSesionNegocio(sesion)
            else db.barDao().clearSesionNegocio()
        }
        sessionScope.launch { _logoBytes.value = IdentityNegocioClient.obtenerLogo() }
        sincronizarDesdeIdentity()
    }

    /** Cierra la sesión (logout): desconecta Identity y limpia sesión + flag local. */
    fun cerrarSesion() {
        // Conservar `baseUrl` (config estática): si `desconectar()` la anulase, el
        // siguiente `loginNegocio` fallaría (IdentityHttp devuelve -1 con baseUrl null).
        IdentityNegocioClient.desconectarConservandoBaseUrl()
        _sesion.value = null
        _sesionEstado.value = SesionEstado.SIN_SESION
        _logoBytes.value = null
        repository.setIdentityConfig(IdentityConfig())
        sessionScope.launch { db.barDao().clearSesionNegocio() }
    }

    /**
     * Revalida la sesión contra el VPS (`GET /v1/auth/negocio/me`): 200 → renueva
     * `validaHasta = now + 7d`; 401 → invalida (`validaHasta = 0`, conserva los datos
     * para diagnóstico); red caída → no toca nada (la sesión caduca sola a los 7 días).
     */
    fun revalidar() {
        val sesion = _sesion.value ?: return
        if (sesion.token == null) return
        sessionScope.launch {
            when (IdentityNegocioClient.revalidarToken()) {
                IdentityNegocioClient.RevalidacionResultado.OK -> {
                    val renovada = sesion.copy(validaHasta = System.currentTimeMillis() + SESION_VALIDEZ_MS)
                    _sesion.value = renovada
                    _sesionEstado.value = sesionEstadoDe(renovada, System.currentTimeMillis())
                    db.barDao().upsertSesionNegocio(renovada)
                }
                IdentityNegocioClient.RevalidacionResultado.REVOCADA -> {
                    // «Logout técnico»: la cuenta ya no vale. Se desconecta el cliente
                    // (el proyector de oficio deja de emitir porque `conectado` pasa a
                    // false) y se limpia `identity_config.conectado`, pero se conserva
                    // `sesion_negocio` con `validaHasta = 0` (diagnóstico) y la cola de
                    // `servicios_pendientes` (idempotente, drena con la siguiente cuenta).
                    val invalida = sesion.copy(validaHasta = 0L)
                    _sesion.value = invalida
                    _sesionEstado.value = SesionEstado.INVALIDA
                    db.barDao().upsertSesionNegocio(invalida)
                    IdentityNegocioClient.desconectarConservandoBaseUrl()
                    repository.setIdentityConfig(IdentityConfig())
                }
                IdentityNegocioClient.RevalidacionResultado.SIN_RED -> Unit // caduca sola
            }
        }
    }

    /** Timer de revalidación: al arrancar el nodo y cada 24 h, si hay sesión con token. */
    private fun startRevalidacionSesion() {
        revalidacionJob?.cancel()
        revalidacionJob = revalidacionScope.launch {
            while (isActive) {
                revalidar()
                delay(SESION_REVALIDACION_INTERVALO_MS)
            }
        }
    }

    private fun stopRevalidacionSesion() {
        revalidacionJob?.cancel()
        revalidacionJob = null
    }

    /** Rehidrata el [IdentityNegocioClient] con la sesión guardada en Room. */
    private fun hidratarIdentity(sesion: SesionNegocio) {
        IdentityNegocioClient.negocioToken = sesion.token
        IdentityNegocioClient.establecimientoUuid = sesion.establecimientoUuid
        IdentityNegocioClient.cuentaNegocio = sesion.nombreMostrar?.let { nombre ->
            IdentityCuentaNegocio(
                email = sesion.email.orEmpty(),
                nombreMostrar = nombre,
                tipoEstablecimiento = sesion.tipo?.apiValor(),
                logoUrl = sesion.logoUrl,
                dataOrigin = sesion.dataOrigin,
            )
        }
    }

    /**
     * Re-pulla desde Identity (fuente de verdad) el layout y los camareros al iniciar
     * o restaurar sesión: SQLite hace mirror. El layout reemplaza el local; los
     * camareros ACTIVA se sincronizan a la lista blanca.
     */
    private fun sincronizarDesdeIdentity() {
        sessionScope.launch {
            IdentityNegocioClient.obtenerLayout()?.let { (salas, mesas) ->
                if (salas.isNotEmpty() || mesas.isNotEmpty()) {
                    repository.reemplazarLayout(salas, mesas)
                }
            }
            val miembros = IdentityNegocioClient.listarMiembros()
                .filter { it.estado.equals("activa", ignoreCase = true) }
                .map { it.camareroId }
            repository.sincronizarMiembros(miembros)
            // Espejo de invitaciones: el estado (incluida `expirada`) lo deriva Identity.
            val invitaciones = IdentityNegocioClient.listarInvitaciones().map { it.toInvitacion() }
            repository.sincronizarInvitaciones(invitaciones)
        }
    }

    override fun onTerminate() {
        stopLocal()
        sessionScope.cancel()
        proyeccionScope.cancel()
        super.onTerminate()
    }

    companion object {

        /** Cada cuánto se revisa el timeout (5 s). */
        const val SESSION_CHECK_INTERVAL_MS: Long = 5_000L

        /** Cada cuánto intenta drenar la cola de oficio (10 s). */
        const val OFICIO_PROYECCION_INTERVALO_MS: Long = 10_000L

        /** Cada cuánto se revalida la sesión contra el VPS (24 h). */
        const val SESION_REVALIDACION_INTERVALO_MS: Long = 24 * 60 * 60 * 1000L

        /** Validez de la sesión local tras un contacto exitoso con el VPS (7 días). */
        const val SESION_VALIDEZ_MS: Long = 7 * 24 * 60 * 60 * 1000L

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
