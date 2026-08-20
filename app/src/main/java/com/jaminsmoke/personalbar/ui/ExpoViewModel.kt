package com.jaminsmoke.personalbar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CartaModificadores
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Linea
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Ticket

import com.jaminsmoke.personalbar.data.TicketEstado
import com.jaminsmoke.personalbar.lan.BarLanService
import com.jaminsmoke.personalbar.ui.voz.OrdenColaVoz
import com.jaminsmoke.personalbar.ui.voz.VozColaParser
import com.jaminsmoke.personalbar.ui.voz.VozRecognizer
import com.jaminsmoke.personalbar.ui.voz.mensajeErrorVoz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Proyección de ticket para la expo: une Ticket + Ronda (mesa, número, camarero, preparador). */
data class ExpoTicket(
    val id: String,
    val mesa: String,
    val ronda: Int,
    val camarero: String?,
    val preparadoPor: String?,
    val estado: TicketEstado,
    val numeroCola: Int,
    val destino: Destino,
    val lineas: List<String>,
)

data class ExpoUiState(
    val drinkQueue: List<ExpoTicket> = emptyList(),
    val foodQueue: List<ExpoTicket> = emptyList(),
    val roomActive: Boolean = false,
    /** FGS arrancado: false = nodo activo pero sin servicio en primer plano (degradado). */
    val fgsOk: Boolean = true,
    /** Error de arranque del nodo (id de recurso; null = sin error). Lo pinta el chip del header. */
    val lanError: Int? = null,
    val camareros: List<Camarero> = emptyList(),
    /** Camareros ACTIVA marcados de servicio en el puesto (varios a la vez). */
    val deServicio: List<Camarero> = emptyList(),
    /** El que prepara ahora (último chip pulsado); sin él no se marca Preparado. */
    val enMano: Camarero? = null,
    /** Commanders vivos en la sala (sesión activa + heartbeat fresco). */
    val conectados: Int = 0,
)

/** Base intermedia del combine (evita el overload de 6 flows). */
private data class ColasBase(
    val bebida: List<Ticket>,
    val comida: List<Ticket>,
    val rondas: List<Ronda>,
    val active: Boolean,
    val camareros: List<Camarero>,
)

class ExpoViewModel : ViewModel() {
    private val repository: BarRepository = PersonalBarApp.get().repository

    private val _uiState = MutableStateFlow(ExpoUiState())
    val uiState: StateFlow<ExpoUiState> = _uiState.asStateFlow()

    /** Quién prepara ahora («en mano»): último chip pulsado de los de servicio. */
    private val _enMano = MutableStateFlow<Camarero?>(null)
    val enMano: StateFlow<Camarero?> = _enMano.asStateFlow()

    /** Voz: true mientras el reconocedor escucha. */
    private val _escuchando = MutableStateFlow(false)
    val escuchando: StateFlow<Boolean> = _escuchando.asStateFlow()

    /** Voz: último texto parcial reconocido (feedback visual). */
    private val _parcial = MutableStateFlow<String?>(null)
    val parcial: StateFlow<String?> = _parcial.asStateFlow()

    /** Voz: mensaje de feedback (OK / error), texto ya resuelto. */
    private val _mensajeVoz = MutableStateFlow<String?>(null)
    val mensajeVoz: StateFlow<String?> = _mensajeVoz.asStateFlow()

    private val recognizer: VozRecognizer = VozRecognizer(PersonalBarApp.get())

    init {
        val base = combine(
            repository.bebidaQueue,
            repository.comidaQueue,
            repository.rondas,
            PersonalBarApp.get().roomActive,
            repository.camareros,
        ) { bebida, comida, rondas, active, camareros ->
            ColasBase(bebida, comida, rondas, active, camareros)
        }
        viewModelScope.launch {
            combine(base, repository.deServicio, _enMano, PersonalBarApp.get().fgsOk, repository.conectados) { b, deServicio, enMano, fgsOk, conectados ->
                val rondasPorId = b.rondas.associateBy { it.id }
                ExpoUiState(
                    drinkQueue = b.bebida.map { it.toExpoTicket(rondasPorId) },
                    foodQueue = b.comida.map { it.toExpoTicket(rondasPorId) },
                    roomActive = b.active,
                    fgsOk = fgsOk,
                    camareros = b.camareros.filter { it.estado == CamareroEstado.ACTIVA },
                    deServicio = deServicio,
                    enMano = enMano,
                    conectados = conectados,
                )
            }.combine(PersonalBarApp.get().lanError) { state, lanError ->
                state.copy(lanError = lanError)
            }.collect { state ->
                _uiState.value = state
                // Tras arranque/recarga (Room) el «en mano» no se persiste: si hay
                // camareros de servicio y nadie tiene el ticket en mano, el primero
                // de la lista lo toma (coherente con la sesión múltiple del puesto).
                if (state.enMano == null && state.deServicio.isNotEmpty()) {
                    _enMano.value = state.deServicio.first()
                }
            }
        }
    }

    /** Arranca/para el nodo (service + server) según el estado actual. */
    fun toggleLocal() {
        val app = PersonalBarApp.get()
        if (app.roomActive.value) {
            BarLanService.stop(app)
        } else {
            BarLanService.start(app)
        }
    }

    /**
     * Alterna «de servicio» del camarero en el puesto (añadir/quitar, no sustituir).
     * Al ponerlo de servicio queda además como «en mano» (el que prepara ahora).
     */
    fun alternarDeServicio(camareroId: String) {
        val camarero = repository.camareros.value.firstOrNull { it.id == camareroId } ?: return
        if (camarero.deServicio) {
            repository.quitarDeServicio(camareroId)
            if (_enMano.value?.id == camareroId) _enMano.value = null
        } else {
            repository.ponerDeServicio(camareroId)
            _enMano.value = repository.camareros.value.firstOrNull { it.id == camareroId }
        }
    }

    /** Fija el «en mano» (último chip pulsado) sin cambiar la lista de servicio. */
    fun seleccionarEnMano(camareroId: String) {
        _enMano.value = repository.camareros.value.firstOrNull { it.id == camareroId }
    }

    fun clearPreparador() {
        _enMano.value = null
    }

    /** Marca el ticket como preparado por el que está «en mano». Sin él, no-op. */
    fun marcarPreparado(ticketId: String) {
        val preparador = _enMano.value ?: return
        repository.marcarPreparado(ticketId, preparador.nombre ?: preparador.id.take(8))
    }

    /** Marca el ticket como recogido (sale de la cola). */
    fun marcarRecogido(ticketId: String) {
        repository.marcarRecogido(ticketId)
    }

    // ── Voz en colas ────────────────────────────────────────────────────────

    init {
        recognizer.onResultado = { texto -> procesarOrden(texto) }
        recognizer.onParcial = { parcial -> _parcial.value = parcial }
        recognizer.onError = { error ->
            _escuchando.value = false
            _mensajeVoz.value = mensajeErrorVoz(PersonalBarApp.get(), error)
        }
    }

    /** Empieza a escuchar la orden de cola por voz. */
    fun empezarEscucha() {
        _mensajeVoz.value = null
        _parcial.value = null
        _escuchando.value = true
        recognizer.empezar()
    }

    /** Para la escucha en curso (botón o al salir). */
    fun detenerEscucha() {
        recognizer.detener()
        _escuchando.value = false
    }

    /** Permiso de micrófono denegado: informa sin entrar en escucha. */
    fun notificarPermisoDenegado() {
        _mensajeVoz.value = PersonalBarApp.get().getString(R.string.voz_permiso_denegado)
    }

    /** Procesa el texto reconocido: parsea, resuelve el ticket y ejecuta la mutación. */
    private fun procesarOrden(texto: String) {
        _escuchando.value = false
        when (val orden = VozColaParser.parsear(texto)) {
            is OrdenColaVoz.Preparado -> ejecutarPreparado(orden)
            is OrdenColaVoz.Recogido -> ejecutarRecogido(orden)
            OrdenColaVoz.NoEntendido -> _mensajeVoz.value =
                PersonalBarApp.get().getString(R.string.voz_no_entendido)
        }
    }

    private fun ejecutarPreparado(orden: OrdenColaVoz.Preparado) {
        val app = PersonalBarApp.get()
        val ticket = resolverTicket(orden.numeroCola, orden.destino, TicketEstado.PENDIENTE)
            ?: run { _mensajeVoz.value = app.getString(R.string.voz_ticket_no_encontrado); return }
        val preparador = if (orden.nombre == null) {
            _enMano.value ?: run {
                _mensajeVoz.value = app.getString(R.string.voz_sin_sesion)
                return
            }
        } else {
            resolverPreparador(orden.nombre) ?: run {
                _mensajeVoz.value = app.getString(R.string.voz_preparador_no_reconocido)
                return
            }
        }
        repository.marcarPreparado(ticket.id, preparador.nombre ?: preparador.id.take(8))
        _mensajeVoz.value = app.getString(R.string.voz_ok_preparado)
    }

    private fun ejecutarRecogido(orden: OrdenColaVoz.Recogido) {
        val app = PersonalBarApp.get()
        val ticket = resolverTicket(orden.numeroCola, orden.destino, TicketEstado.PREPARADO)
            ?: run { _mensajeVoz.value = app.getString(R.string.voz_estado_ilegal); return }
        repository.marcarRecogido(ticket.id)
        _mensajeVoz.value = app.getString(R.string.voz_ok_recogido)
    }

    /** Resuelve un ticket por (numeroCola, destino) dentro de la cola activa con el estado dado. */
    private fun resolverTicket(numeroCola: Int, destino: Destino, estado: TicketEstado): Ticket? {
        val cola = when (destino) {
            Destino.BARRA -> repository.bebidaQueue.value
            Destino.COCINA -> repository.comidaQueue.value
        }
        return cola.firstOrNull { it.numeroCola == numeroCola && it.estado == estado }
    }

    /**
     * Resuelve el nombre hablado contra la lista blanca ACTIVA: compara contra el
     * nombre (si existe) o el prefijo corto del id (`id.take(8)`), normalizando ambos.
     */
    private fun resolverPreparador(nombreHablado: String): Camarero? {
        val norm = VozColaParser.normalizar(nombreHablado)
        return repository.camareros.value.firstOrNull { c ->
            c.estado == CamareroEstado.ACTIVA &&
                ((c.nombre != null && VozColaParser.normalizar(c.nombre) == norm) ||
                    VozColaParser.normalizar(c.id.take(8)) == norm)
        }
    }

    override fun onCleared() {
        recognizer.destruir()
    }
}

private fun Ticket.toExpoTicket(rondas: Map<String, Ronda>): ExpoTicket {
    val ronda = rondas[rondaId]
    return ExpoTicket(
        id = id,
        mesa = ronda?.mesaId ?: "—",
        ronda = ronda?.numero ?: 0,
        camarero = ronda?.camarero,
        preparadoPor = preparadoPor,
        estado = estado,
        numeroCola = numeroCola,
        destino = destino,        lineas = lineas.map { formatoLineaExpo(it) },
    )
}

/**
 * Línea de expo en claro: `cantidad × nombre · opción · nota`. El delta de la
 * opción se muestra solo cuando ≠ 0 («+0,50 €»). Dos variantes de un mismo SKU
 * (distinto modificador) ya llegan como dos líneas separadas desde Commander.
 */
internal fun formatoLineaExpo(linea: Linea): String {
    val extras = mutableListOf<String>()
    linea.modificadores.forEach { m ->
        val nombre = m.opcion.trim()
        if (nombre.isEmpty()) return@forEach
        extras.add(if (m.delta != 0.0) "$nombre +${CartaModificadores.formatoDelta(m.delta)}" else nombre)
    }
    linea.nota?.trim()?.takeIf { it.isNotEmpty() }?.let { extras.add(it) }
    val base = "${linea.cantidad}x ${linea.nombreProducto}"
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" · ")}"
}


