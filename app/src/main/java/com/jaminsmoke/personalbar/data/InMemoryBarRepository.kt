package com.jaminsmoke.personalbar.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Máximo sufijo numérico de ids `prefijo-N`; 0 si no hay ninguno (para no colisionar con el seed). */
private fun maxNumSuffix(prefijo: String, ids: Collection<String>): Int =
    ids.mapNotNull { it.removePrefix("$prefijo-").toIntOrNull() }.maxOrNull() ?: 0

/**
 * Implementación en memoria del repositorio: es el **cerebro** del nodo (lógica,
 * colas, idempotencia, secuencias). Room no duplica esta lógica: [RoomBarRepository]
 * envuelve una instancia y persiste su estado tras cada mutación.
 * Acepta estado inicial completo (recarga desde Room) sin efectos secundarios.
 */
class InMemoryBarRepository(
    establecimientoInicial: Establecimiento = Establecimiento("local-1", "Mi local"),
    salasIniciales: List<Sala> = emptyList(),
    catalogoInicial: List<Producto> = emptyList(),
    mesasIniciales: List<Mesa> = emptyList(),
    zonasIniciales: List<Zona> = emptyList(),
    rondasIniciales: List<Ronda> = emptyList(),
    bebidaInicial: List<Ticket> = emptyList(),
    comidaInicial: List<Ticket> = emptyList(),
    servidosIniciales: List<Ticket> = emptyList(),
    reservasIniciales: List<Reserva> = emptyList(),
    camarerosIniciales: List<Camarero> = emptyList(),
    invitacionesIniciales: List<Invitacion> = emptyList(),
    identityConfigInicial: IdentityConfig = IdentityConfig(),
    siguienteColaInicial: Map<Destino, Int> = emptyMap(),
    qrKeyInicial: QrKey? = null,
    altasPendientesIniciales: List<AltaPendiente> = emptyList(),
    jornadasIniciales: List<JornadaLocal> = emptyList(),
    horarioInicial: List<HorarioLocal> = emptyList(),
    serviciosPendientesIniciales: List<ServicioPendiente> = emptyList(),
    operacionesCatalogoIniciales: List<OperacionCatalogo> = emptyList(),
    revisionesProductoIniciales: Map<String, Int> = emptyMap(),
    catalogoSyncDesdeInicial: Int = 0,
    gruposModificadorIniciales: List<GrupoModificador> = emptyList(),
    opcionesModificadorIniciales: List<OpcionModificador> = emptyList(),
    productoGrupoIniciales: List<ProductoGrupo> = emptyList(),
) : BarRepository {

    private val rondasRecibidas = ConcurrentHashMap.newKeySet<String>().also { it.addAll(rondasIniciales.map { r -> r.id }) }
    private var salaSeq = maxNumSuffix("sala", salasIniciales.map { it.id })
    private var mesaSeq = maxNumSuffix("mesa", mesasIniciales.map { it.id })
    private var zonaSeq = maxNumSuffix("zona", zonasIniciales.map { it.id })
    private var reservaSeq = maxNumSuffix("reserva", reservasIniciales.map { it.id })

    /** Último heartbeat recibido por camarero (epoch millis). Solo para sesiones activas. */
    private val lastSeen = ConcurrentHashMap<String, Long>()

    private val _establecimiento = MutableStateFlow(establecimientoInicial)
    private val _salas = MutableStateFlow(salasIniciales)
    private val _mesas = MutableStateFlow(mesasIniciales)
    private val _zonas = MutableStateFlow(zonasIniciales)
    private val _reservas = MutableStateFlow(reservasIniciales)
    private val _bebidaQueue = MutableStateFlow(bebidaInicial)
    private val _comidaQueue = MutableStateFlow(comidaInicial)
    private val _servidos = MutableStateFlow(servidosIniciales)
    private val _rondas = MutableStateFlow(rondasIniciales)
    private val _catalogo = MutableStateFlow(catalogoInicial)
    private val _camareros = MutableStateFlow(camarerosIniciales)
    private val _identityConfig = MutableStateFlow(identityConfigInicial)
    private val _invitaciones = MutableStateFlow(invitacionesIniciales)
    private val _qrKey = MutableStateFlow(qrKeyInicial)
    private val _altasPendientes = MutableStateFlow(altasPendientesIniciales)
    private val _jornadas = MutableStateFlow(jornadasIniciales)
    private val _serviciosPendientes = MutableStateFlow(serviciosPendientesIniciales)
    private val _operacionesCatalogo = MutableStateFlow(operacionesCatalogoIniciales)
    private val _revisionesProducto = MutableStateFlow(revisionesProductoIniciales)
    private val _catalogoSyncDesde = MutableStateFlow(catalogoSyncDesdeInicial)
    private val _horario = MutableStateFlow(horarioInicial)
    private val _gruposModificador = MutableStateFlow(gruposModificadorIniciales)
    private val _opcionesModificador = MutableStateFlow(opcionesModificadorIniciales)
    private val _productoGrupo = MutableStateFlow(productoGrupoIniciales)
    private val _eventos = MutableSharedFlow<SalaEvent>(extraBufferCapacity = 16)

    /** Derivado síncrono de [Camarero.deServicio]: se recalcula en cada mutación de camareros. */
    private val _deServicio = MutableStateFlow(
        camarerosIniciales.filter { it.estado == CamareroEstado.ACTIVA && it.deServicio }
    )

    /** Nº de dispositivos vivos (sesión activa + heartbeat fresco). Se recalcula en cada mutación. */
    private val _conectados = MutableStateFlow(0)

    override val establecimiento: StateFlow<Establecimiento> = _establecimiento.asStateFlow()
    override val salas: StateFlow<List<Sala>> = _salas.asStateFlow()
    override val mesas: StateFlow<List<Mesa>> = _mesas.asStateFlow()
    override val zonas: StateFlow<List<Zona>> = _zonas.asStateFlow()
    override val reservas: StateFlow<List<Reserva>> = _reservas.asStateFlow()
    override val bebidaQueue: StateFlow<List<Ticket>> = _bebidaQueue.asStateFlow()
    override val comidaQueue: StateFlow<List<Ticket>> = _comidaQueue.asStateFlow()
    override val servidos: StateFlow<List<Ticket>> = _servidos.asStateFlow()
    override val rondas: StateFlow<List<Ronda>> = _rondas.asStateFlow()
    override val catalogo: StateFlow<List<Producto>> = _catalogo.asStateFlow()
    override val camareros: StateFlow<List<Camarero>> = _camareros.asStateFlow()
    override val deServicio: StateFlow<List<Camarero>> = _deServicio.asStateFlow()
    override val conectados: StateFlow<Int> = _conectados.asStateFlow()
    override val identityConfig: StateFlow<IdentityConfig> = _identityConfig.asStateFlow()
    override val invitaciones: StateFlow<List<Invitacion>> = _invitaciones.asStateFlow()
    override val qrKey: StateFlow<QrKey?> = _qrKey.asStateFlow()
    override val altasPendientes: StateFlow<List<AltaPendiente>> = _altasPendientes.asStateFlow()
    override val jornadas: StateFlow<List<JornadaLocal>> = _jornadas.asStateFlow()
    override val serviciosPendientes: StateFlow<List<ServicioPendiente>> = _serviciosPendientes.asStateFlow()
    override val operacionesCatalogo: StateFlow<List<OperacionCatalogo>> = _operacionesCatalogo.asStateFlow()
    override val revisionesProducto: StateFlow<Map<String, Int>> = _revisionesProducto.asStateFlow()
    override val catalogoSyncDesde: StateFlow<Int> = _catalogoSyncDesde.asStateFlow()
    override val horario: StateFlow<List<HorarioLocal>> = _horario.asStateFlow()
    override val gruposModificador: StateFlow<List<GrupoModificador>> = _gruposModificador.asStateFlow()
    override val opcionesModificador: StateFlow<List<OpcionModificador>> = _opcionesModificador.asStateFlow()
    override val productoGrupo: StateFlow<List<ProductoGrupo>> = _productoGrupo.asStateFlow()

    override val eventos: SharedFlow<SalaEvent> = _eventos.asSharedFlow()

    override fun guardarHorario(horario: List<HorarioLocal>) {
        _horario.value = horario.sortedBy { it.diaSemana }
    }

    override fun resumenJornadas(desde: Long?, hasta: Long?): JornadasResumen {
        val ahora = System.currentTimeMillis()
        val enPeriodo: (Long) -> Boolean = { t ->
            (desde == null || t >= desde) && (hasta == null || t <= hasta)
        }
        val intervalos = _jornadas.value
            .filter { enPeriodo(it.inicio) }
            .map { JornadaIntervalo(camareroId = it.camareroId, inicio = it.inicio, fin = it.fin) }
        val camarerosDelPeriodo = intervalos.map { it.camareroId }.distinct()
        // Mesas servidas por camarero: mesa (idZona) con al menos un ticket RECOGIDO
        // cuya ronda entró en el periodo (aproximación v0.1: se acota por
        // `Ronda.creadoEn`) y fue pedida por ese camarero (`Ronda.camarero`).
        val mesasPorCamarero = _servidos.value
            .filter { it.estado == TicketEstado.RECOGIDO }
            .mapNotNull { rondaDe(it.rondaId) }
            .filter { enPeriodo(it.creadoEn) && it.camarero != null }
            .groupBy({ it.camarero!! }, { it.mesaId })
            .mapValues { (_, mesas) -> mesas.distinct().size }
        val porCamarero = camarerosDelPeriodo.map { camareroId ->
            val horas = intervalos
                .filter { it.camareroId == camareroId }
                .sumOf { (it.fin ?: ahora) - it.inicio }
            ResumenCamarero(
                camareroId = camareroId,
                horasMs = horas,
                mesasDistintas = mesasPorCamarero[camareroId] ?: 0,
            )
        }
        return JornadasResumen(intervalos = intervalos, porCamarero = porCamarero)
    }

    private fun rondaDe(rondaId: String): Ronda? = _rondas.value.firstOrNull { it.id == rondaId }

    // ── Rondas / tickets ──────────────────────────────────────────────────────

    /** Siguiente id de cola por destino (monótono en el turno; no compacta al recoger). */
    private val siguienteColaPorDestino = siguienteColaInicial.toMutableMap()

    override fun crearRonda(ronda: Ronda): Boolean {
        if (!rondasRecibidas.add(ronda.id)) return false
        val enriquecida = ronda.copy(lineas = resolverIdsModificadores(ronda.lineas))
        _rondas.update { it + enriquecida }
        val tickets = RondaSplitter.split(enriquecida, _catalogo.value.associateBy { it.id })
            .map { t -> t.copy(numeroCola = siguienteCola(t.destino)) }
        _bebidaQueue.update { it + tickets.filter { t -> t.destino == Destino.BARRA } }
        _comidaQueue.update { it + tickets.filter { t -> t.destino == Destino.COCINA } }
        return true
    }

    /**
     * Rellena los ids internos (`grupoId`/`opcionId`) de los modificadores de cada
     * línea resolviendo por nombre contra el catálogo actual. Best-effort: si un
     * nombre no resuelve (renombrado/borrado), el id queda vacío y no bloquea la ronda.
     */
    private fun resolverIdsModificadores(lineas: List<Linea>): List<Linea> {
        val gruposPorNombre = _gruposModificador.value.associateBy { normalizarNombreCamarero(it.nombre) }
        val opciones = _opcionesModificador.value
        return lineas.map { linea ->
            if (linea.modificadores.isEmpty()) {
                linea
            } else {
                linea.copy(
                    modificadores = linea.modificadores.map { m ->
                        val gid = gruposPorNombre[normalizarNombreCamarero(m.grupo)]?.id.orEmpty()
                        val oid = opciones
                            .firstOrNull {
                                it.grupoId == gid &&
                                    normalizarNombreCamarero(it.nombre) == normalizarNombreCamarero(m.opcion)
                            }
                            ?.id.orEmpty()
                        m.copy(grupoId = gid, opcionId = oid)
                    },
                )
            }
        }
    }

    private fun siguienteCola(destino: Destino): Int =
        (siguienteColaPorDestino[destino] ?: 0) + 1
            .also { siguienteColaPorDestino[destino] = it }

    // ── Productos (catálogo) ─────────────────────────────────────────────────

    /** Encola una operación de catálogo en el outbox (base_revision desde el mirror local). */
    private fun encolarOperacionCatalogo(aggregateId: String, action: String, producto: Producto?) {
        val op = OperacionCatalogo(
            operationId = UUID.randomUUID().toString(),
            aggregateId = aggregateId,
            action = action,
            baseRevision = _revisionesProducto.value[aggregateId] ?: 0,
            nombre = producto?.nombre,
            categoria = producto?.categoria,
            destino = producto?.categoria?.let { destinoSyncDesdeCategoria(it) },
            precioCentimos = producto?.let { (it.precio.coerceAtLeast(0.0) * 100).roundToInt() },
            moneda = "EUR",
            disponible = producto?.disponible ?: true,
            descripcion = producto?.descripcion,
        )
        _operacionesCatalogo.update { it + op }
    }

    override fun crearProducto(nombre: String, categoria: String, precio: Double, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val n = nombre.trim()
        val c = categoria.trim()
        if (n.isEmpty() || c.isEmpty()) return false
        val producto = Producto(
            id = UUID.randomUUID().toString(),
            nombre = n,
            categoria = c,
            precio = precio.coerceAtLeast(0.0),
            subfamilia = subfamilia?.trim()?.takeIf { it.isNotEmpty() },
            permiteNota = permiteNota,
            descripcion = normalizarDescripcionProducto(descripcion),
        )
        _catalogo.update { it + producto }
        if (producto.esPublicableEnWeb()) {
            encolarOperacionCatalogo(producto.id, "crear", producto)
        }
        return true
    }

    override fun editarProducto(id: String, nombre: String, categoria: String, precio: Double, disponible: Boolean, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val n = nombre.trim()
        val c = categoria.trim()
        if (n.isEmpty() || c.isEmpty()) return false
        val existente = _catalogo.value.firstOrNull { it.id == id } ?: return false
        val editado = existente.copy(
            nombre = n,
            categoria = c,
            precio = precio.coerceAtLeast(0.0),
            disponible = disponible,
            subfamilia = subfamilia?.trim()?.takeIf { it.isNotEmpty() },
            permiteNota = permiteNota,
            descripcion = normalizarDescripcionProducto(descripcion),
        )
        _catalogo.update { list -> list.map { if (it.id == id) editado else it } }
        val sincronizado = id in _revisionesProducto.value
        when {
            sincronizado -> encolarOperacionCatalogo(id, "actualizar", editado)
            editado.esPublicableEnWeb() -> encolarOperacionCatalogo(id, "crear", editado)
        }
        return true
    }

    override fun borrarProducto(id: String): Boolean {
        val antes = _catalogo.value.size
        _catalogo.update { it.filterNot { p -> p.id == id } }
        val borrado = _catalogo.value.size < antes
        if (borrado) {
            _productoGrupo.update { it.filterNot { a -> a.productoId == id } }
            val teniaCrearPendiente = _operacionesCatalogo.value.any { it.aggregateId == id && it.action == "crear" }
            _operacionesCatalogo.update { ops -> ops.filterNot { it.aggregateId == id && it.action == "crear" } }
            if (id in _revisionesProducto.value) {
                encolarOperacionCatalogo(id, "archivar", null)
            } else if (teniaCrearPendiente) {
                // Nunca llegó al server: basta con quitar el crear del outbox.
            }
        }
        return borrado
    }

    // ── Grupos de modificadores (carta) ─────────────────────────────────────

    override fun crearGrupoModificador(nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        val grupo = GrupoModificador(id = UUID.randomUUID().toString(), nombre = n, multiple = multiple, obligatorio = obligatorio)
        _gruposModificador.update { it + grupo }
        return true
    }

    override fun editarGrupoModificador(id: String, nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_gruposModificador.value.none { it.id == id }) return false
        _gruposModificador.update { list -> list.map { if (it.id == id) it.copy(nombre = n, multiple = multiple, obligatorio = obligatorio) else it } }
        return true
    }

    override fun borrarGrupoModificador(id: String): Boolean {
        val antes = _gruposModificador.value.size
        _gruposModificador.update { it.filterNot { g -> g.id == id } }
        val borrado = _gruposModificador.value.size < antes
        if (borrado) {
            _opcionesModificador.update { it.filterNot { o -> o.grupoId == id } }
            _productoGrupo.update { it.filterNot { a -> a.grupoId == id } }
        }
        return borrado
    }

    override fun crearOpcionModificador(grupoId: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_gruposModificador.value.none { it.id == grupoId }) return false
        val opcion = OpcionModificador(
            id = UUID.randomUUID().toString(),
            grupoId = grupoId,
            nombre = n,
            deltaPrecio = deltaPrecio,
            alias = alias.trim(),
        )
        _opcionesModificador.update { it + opcion }
        return true
    }

    override fun editarOpcionModificador(id: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_opcionesModificador.value.none { it.id == id }) return false
        _opcionesModificador.update { list -> list.map { if (it.id == id) it.copy(nombre = n, deltaPrecio = deltaPrecio, alias = alias.trim()) else it } }
        return true
    }

    override fun borrarOpcionModificador(id: String): Boolean {
        val antes = _opcionesModificador.value.size
        _opcionesModificador.update { it.filterNot { o -> o.id == id } }
        return _opcionesModificador.value.size < antes
    }

    override fun guardarGrupoConOpciones(
        grupoId: String?,
        nombre: String,
        multiple: Boolean,
        obligatorio: Boolean,
        opciones: List<OpcionModificadorBorrador>,
    ): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        val gid: String
        if (grupoId == null) {
            gid = UUID.randomUUID().toString()
            _gruposModificador.update { it + GrupoModificador(gid, n, multiple, obligatorio) }
        } else {
            if (_gruposModificador.value.none { it.id == grupoId }) return false
            _gruposModificador.update { list ->
                list.map { if (it.id == grupoId) it.copy(nombre = n, multiple = multiple, obligatorio = obligatorio) else it }
            }
            gid = grupoId
        }
        // Reemplaza las opciones del grupo: conserva el id de las existentes,
        // genera id para las nuevas y elimina las que ya no vienen.
        val nuevas = opciones.mapNotNull { borrador ->
            val on = borrador.nombre.trim()
            if (on.isEmpty()) {
                null
            } else {
                OpcionModificador(
                    id = borrador.id.ifBlank { UUID.randomUUID().toString() },
                    grupoId = gid,
                    nombre = on,
                    deltaPrecio = borrador.deltaPrecio,
                    alias = borrador.alias.trim(),
                )
            }
        }
        _opcionesModificador.update { list -> list.filterNot { it.grupoId == gid } + nuevas }
        return true
    }

    override fun asignarGrupoProducto(productoId: String, grupoId: String): Boolean {
        if (_catalogo.value.none { it.id == productoId }) return false
        if (_gruposModificador.value.none { it.id == grupoId }) return false
        if (_productoGrupo.value.any { it.productoId == productoId && it.grupoId == grupoId }) return false
        _productoGrupo.update { it + ProductoGrupo(productoId, grupoId) }
        return true
    }

    override fun desasignarGrupoProducto(productoId: String, grupoId: String): Boolean {
        val antes = _productoGrupo.value.size
        _productoGrupo.update { it.filterNot { a -> a.productoId == productoId && a.grupoId == grupoId } }
        return _productoGrupo.value.size < antes
    }


    override fun marcarPreparado(ticketId: String, preparadoPor: String): Boolean {
        val pendiente = _bebidaQueue.value.any { it.id == ticketId && it.estado == TicketEstado.PENDIENTE } ||
            _comidaQueue.value.any { it.id == ticketId && it.estado == TicketEstado.PENDIENTE }
        if (!pendiente) return false
        transformTicket(ticketId) { it.copy(estado = TicketEstado.PREPARADO, preparadoPor = preparadoPor) }
        val ticket = _bebidaQueue.value.firstOrNull { it.id == ticketId }
            ?: _comidaQueue.value.firstOrNull { it.id == ticketId }
            ?: return true
        _eventos.tryEmit(SalaEvent.preparado(ticket, mesaIdDe(ticket), camareroDe(ticket)))
        return true
    }

    override fun marcarRecogido(ticketId: String): Boolean {
        val ticket = _bebidaQueue.value.firstOrNull { it.id == ticketId }
            ?: _comidaQueue.value.firstOrNull { it.id == ticketId }
            ?: return false
        if (ticket.estado != TicketEstado.PREPARADO) return false
        val recogido = ticket.copy(estado = TicketEstado.RECOGIDO)
        _bebidaQueue.update { it.filterNot { t -> t.id == ticketId } }
        _comidaQueue.update { it.filterNot { t -> t.id == ticketId } }
        _servidos.update { it + recogido }
        _eventos.tryEmit(SalaEvent.recogido(recogido, mesaIdDe(recogido), camareroDe(recogido)))
        encolarRondaServidaSiCompleta(ticket.rondaId)
        return true
    }

    /**
     * Si la ronda quedó completa (todos sus tickets RECOGIDO), encola el evento
     * «ronda servida» acreditado al camarero que pidió. Resolución estricta por
     * nombre normalizado contra la lista blanca: solo si hay **exactamente uno**
     * ACTIVA se emite; si no resuelve, no se emite (el libro no adivina).
     */
    private fun encolarRondaServidaSiCompleta(rondaId: String) {
        val enColas = (_bebidaQueue.value + _comidaQueue.value).any { it.rondaId == rondaId }
        if (enColas) return
        // Salvaguarda: solo cuenta como servida si la ronda tiene tickets ya recogidos
        // (una ronda sin tickets no genera evento fantasma).
        if (_servidos.value.none { it.rondaId == rondaId }) return
        val ronda = _rondas.value.firstOrNull { it.id == rondaId } ?: return
        val camareroId = resolverCamareroActivo(ronda.camarero) ?: return
        _serviciosPendientes.update { lista ->
            val pendiente = ServicioPendiente(
                eventoId = "servicio:$rondaId",
                camareroId = camareroId,
                tipo = "ronda_servida",
            )
            val idx = lista.indexOfFirst { it.eventoId == pendiente.eventoId }
            if (idx >= 0) lista.toMutableList().also { it[idx] = pendiente } else lista + pendiente
        }
    }

    /** Camarero ACTIVA cuyo nombre normalizado coincide con [nombre]; null si no resuelve a exactamente uno. */
    private fun resolverCamareroActivo(nombre: String?): String? {
        val norm = normalizarNombreCamarero(nombre)
        if (norm.isEmpty()) return null
        val coinciden = _camareros.value.filter {
            it.estado == CamareroEstado.ACTIVA && normalizarNombreCamarero(it.nombre) == norm
        }
        return coinciden.singleOrNull()?.id
    }

    /** Ronda origen de un ticket (para resolver la mesa y el camarero que pidió). */
    private fun rondaDe(ticket: Ticket): Ronda? = _rondas.value.firstOrNull { it.id == ticket.rondaId }

    /** Id de zona de red de la mesa ("T3"); ya vive en `Ronda.mesaId`, sin cruzar salas. */
    private fun mesaIdDe(ticket: Ticket): String? = rondaDe(ticket)?.mesaId

    /** Camarero que pidió la ronda ("quién lo pidió"), simétrico a `preparadoPor`. */
    private fun camareroDe(ticket: Ticket): String? = rondaDe(ticket)?.camarero

    private fun transformTicket(ticketId: String, transform: (Ticket) -> Ticket): Boolean {
        val enBebida = _bebidaQueue.value.any { it.id == ticketId }
        val enComida = _comidaQueue.value.any { it.id == ticketId }
        if (enBebida) {
            _bebidaQueue.update { list -> list.map { if (it.id == ticketId) transform(it) else it } }
        }
        if (enComida) {
            _comidaQueue.update { list -> list.map { if (it.id == ticketId) transform(it) else it } }
        }
        return enBebida || enComida
    }

    // ── Establecimiento (perfil) ─────────────────────────────────────────────

    override fun renombrarEstablecimiento(nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        _establecimiento.value = _establecimiento.value.copy(nombre = n)
        return true
    }

    // ── Salas ─────────────────────────────────────────────────────────────────

    override fun reemplazarLayout(salas: List<Sala>, mesas: List<Mesa>) {
        _salas.value = salas
        _mesas.value = mesas
        salaSeq = maxNumSuffix("sala", salas.map { it.id })
        mesaSeq = maxNumSuffix("mesa", mesas.map { it.id })
    }

    override fun crearSala(nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_salas.value.any { it.nombre.equals(n, ignoreCase = true) }) return false
        val orden = (_salas.value.maxOfOrNull { it.orden } ?: 0) + 1
        val sala = Sala(id = "sala-${++salaSeq}", nombre = n, orden = orden)
        _salas.update { it + sala }
        return true
    }

    override fun renombrarSala(salaId: String, nombre: String): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_salas.value.any { it.id != salaId && it.nombre.equals(n, ignoreCase = true) }) return false
        if (_salas.value.none { it.id == salaId }) return false
        _salas.update { list -> list.map { if (it.id == salaId) it.copy(nombre = n) else it } }
        return true
    }

    override fun eliminarSala(salaId: String): Boolean {
        if (_mesas.value.any { it.salaId == salaId }) return false
        val antes = _salas.value.size
        _salas.update { it.filterNot { s -> s.id == salaId } }
        return _salas.value.size < antes
    }

    // ── Mesas ─────────────────────────────────────────────────────────────────

    override fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean {
        if (_salas.value.none { it.id == salaId }) return false
        val cap = capacidad.coerceIn(1, 99)
        val a = alias?.trim()?.ifBlank { null }
        val indice = (_mesas.value.filter { it.salaId == salaId }.maxOfOrNull { it.indiceZona } ?: 0) + 1
        val numero = (_mesas.value.maxOfOrNull { it.numero } ?: 0) + 1
        val mesasSala = _mesas.value.filter { it.salaId == salaId }
        val (w, h) = mesaDims(forma, girada = false)
        val candidata = mesasSala.maxByOrNull { it.posX }?.let {
            val (lastW, _) = mesaDims(it.forma, it.girada)
            (it.posX + lastW + CELL_F) to it.posY
        } ?: (CELL_F to CELL_F)
        val ocupadas = mesasSala.map {
            val (ow, oh) = mesaDims(it.forma, it.girada)
            listOf(it.posX, it.posY, ow, oh)
        }
        val (px, py) = findNearestFreeCell(candidata.first, candidata.second, w, h, ocupadas)
        val mesa = Mesa(
            id = "mesa-${++mesaSeq}",
            salaId = salaId,
            indiceZona = indice,
            numero = numero,
            alias = a,
            forma = forma,
            capacidad = cap,
            posX = px,
            posY = py,
        )
        _mesas.update { it + mesa }
        return true
    }

    override fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        val cap = capacidad.coerceIn(1, 99)
        val a = alias?.trim()?.ifBlank { null }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(alias = a, capacidad = cap, forma = forma) else it } }
        return true
    }

    override fun borrarMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        if (mesa.reservaActivaId != null) return false
        _mesas.update { list ->
            list.filterNot { it.id == mesaId }.map { m ->
                if (m.salaId == mesa.salaId && m.indiceZona > mesa.indiceZona) m.copy(indiceZona = m.indiceZona - 1) else m
            }
        }
        return true
    }

    override fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(posX = posX, posY = posY) else it } }
        return true
    }

    override fun girarMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val nuevoGiro = !mesa.girada
        val (w, h) = mesaDims(mesa.forma, nuevoGiro)
        val ocupadas = _mesas.value.filter { it.salaId == mesa.salaId && it.id != mesaId }.map {
            val (ow, oh) = mesaDims(it.forma, it.girada)
            listOf(it.posX, it.posY, ow, oh)
        }
        val (x, y) = findNearestFreeCell(mesa.posX, mesa.posY, w, h, ocupadas)
        _mesas.update { list ->
            list.map { if (it.id == mesaId) it.copy(girada = nuevoGiro, posX = x, posY = y) else it }
        }
        return true
    }

    // ── Zonas (agrupación espacial de sala) ─────────────────────────────────

    override fun crearZona(
        salaId: String,
        nombre: String,
        color: ZonaColor,
        posX: Float,
        posY: Float,
        ancho: Float,
        alto: Float,
        camareroId: String?,
    ): Boolean {
        if (_salas.value.none { it.id == salaId }) return false
        val n = nombre.trim()
        if (n.isEmpty()) return false
        val (x, y, w, h) = encajarZona(posX, posY, ancho, alto)
        val zona = Zona(
            id = "zona-${++zonaSeq}",
            salaId = salaId,
            nombre = n,
            posX = x,
            posY = y,
            ancho = w,
            alto = h,
            color = color,
            camareroId = camareroId?.takeIf { esCamareroActivo(it) },
        )
        _zonas.update { it + zona }
        return true
    }

    override fun editarZona(zonaId: String, nombre: String, color: ZonaColor, camareroId: String?): Boolean {
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (_zonas.value.none { it.id == zonaId }) return false
        val nuevoCamarero = camareroId?.let {
            if (!esCamareroActivo(it)) return false
            it
        }
        _zonas.update { list ->
            list.map { if (it.id == zonaId) it.copy(nombre = n, color = color, camareroId = nuevoCamarero) else it }
        }
        return true
    }

    override fun moverZona(zonaId: String, posX: Float, posY: Float): Boolean {
        val zona = _zonas.value.firstOrNull { it.id == zonaId } ?: return false
        val (x, y, w, h) = encajarZona(posX, posY, zona.ancho, zona.alto)
        _zonas.update { list -> list.map { if (it.id == zonaId) it.copy(posX = x, posY = y) else it } }
        return true
    }

    override fun redimensionarZona(zonaId: String, ancho: Float, alto: Float): Boolean {
        val zona = _zonas.value.firstOrNull { it.id == zonaId } ?: return false
        val (x, y, w, h) = encajarZona(zona.posX, zona.posY, ancho, alto)
        _zonas.update { list -> list.map { if (it.id == zonaId) it.copy(posX = x, posY = y, ancho = w, alto = h) else it } }
        return true
    }

    override fun borrarZona(zonaId: String): Boolean {
        val antes = _zonas.value.size
        _zonas.update { it.filterNot { z -> z.id == zonaId } }
        return _zonas.value.size < antes
    }

    override fun asignarCamareroZona(zonaId: String, camareroId: String?): Boolean {
        val zona = _zonas.value.firstOrNull { it.id == zonaId } ?: return false
        val nuevoCamarero = camareroId?.let {
            if (!esCamareroActivo(it)) return false
            it
        }
        _zonas.update { list -> list.map { if (it.id == zonaId) it.copy(camareroId = nuevoCamarero) else it } }
        return true
    }

    /**
     * Encaja el rectángulo de la zona en el canvas del board: tamaño mínimo una
     * celda, máximo el board completo, y posición dentro de los límites.
     */
    private fun encajarZona(
        posX: Float,
        posY: Float,
        ancho: Float,
        alto: Float,
    ): FloatArray {
        val w = ancho.coerceIn(CELL_F, ZONA_ANCHO)
        val h = alto.coerceIn(CELL_F, ZONA_ALTO)
        val x = posX.coerceIn(0f, ZONA_ANCHO - w)
        val y = posY.coerceIn(0f, ZONA_ALTO - h)
        return floatArrayOf(x, y, w, h)
    }

    /** true si el camarero existe y está ACTIVA en la lista blanca. */
    private fun esCamareroActivo(camareroId: String): Boolean =
        _camareros.value.any { it.id == camareroId && it.estado == CamareroEstado.ACTIVA }

    // ── Reservas / bloqueos ───────────────────────────────────────────────────

    override fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val n = nombre.trim()
        if (n.isEmpty()) return false
        if (mesa.bloqueada || mesa.reservaActivaId != null || mesaOcupada(mesa)) return false
        val reserva = Reserva(id = "reserva-${++reservaSeq}", mesaId = mesaId, nombre = n, paraEpoch = paraEpoch)
        _reservas.update { it + reserva }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(reservaActivaId = reserva.id) else it } }
        return true
    }

    override fun cancelarReserva(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        val rid = mesa.reservaActivaId ?: return false
        _reservas.update { list -> list.map { if (it.id == rid) it.copy(canceladaEn = System.currentTimeMillis()) else it } }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(reservaActivaId = null) else it } }
        return true
    }

    override fun bloquearMesa(mesaId: String): Boolean {
        val mesa = _mesas.value.firstOrNull { it.id == mesaId } ?: return false
        if (mesaOcupada(mesa)) return false
        mesa.reservaActivaId?.let { rid ->
            _reservas.update { list -> list.map { if (it.id == rid) it.copy(canceladaEn = System.currentTimeMillis()) else it } }
        }
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(bloqueada = true, reservaActivaId = null) else it } }
        return true
    }

    override fun desbloquearMesa(mesaId: String): Boolean {
        if (_mesas.value.none { it.id == mesaId }) return false
        _mesas.update { list -> list.map { if (it.id == mesaId) it.copy(bloqueada = false) else it } }
        return true
    }

    /** True si la mesa tiene tickets abiertos (ronda sin servir), es decir, está OCUPADA/EN_COCINA. */
    private fun mesaOcupada(mesa: Mesa): Boolean {
        val nombreSala = _salas.value.firstOrNull { it.id == mesa.salaId }?.nombre.orEmpty()
        val idZona = mesa.idZona(nombreSala)
        val rondaPorId = _rondas.value.associateBy { it.id }
        val abiertos = _bebidaQueue.value + _comidaQueue.value
        return abiertos.any { rondaPorId[it.rondaId]?.mesaId == idZona }
    }

    // ── Camareros (lista blanca) ──────────────────────────────────────────────

    override fun altaCamarero(camareroId: String, credencialId: String?, nombre: String?, email: String?): Boolean {
        val existente = _camareros.value.firstOrNull { it.id == camareroId }
        if (existente != null && existente.estado == CamareroEstado.ACTIVA) return false
        val camarero = Camarero(
            id = camareroId,
            credencialId = credencialId,
            nombre = nombre ?: existente?.nombre,
            email = email ?: existente?.email,
        )
        _camareros.update { list ->
            val idx = list.indexOfFirst { it.id == camareroId }
            if (idx >= 0) list.toMutableList().also { it[idx] = camarero } else list + camarero
        }
        refrescarDeServicio()
        return true
    }

    override fun revocarCamarero(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(estado = CamareroEstado.REVOCADA, deServicio = false, sesionActiva = false) else it }
        }
        lastSeen.remove(camareroId)
        if (camarero.sesionActiva) {
            cerrarJornadaAbierta(camareroId)
            _eventos.tryEmit(SalaEvent.cortada(camareroId))
        }
        refrescarDeServicio()
        refrescarConectados()
        return true
    }

    // ── Sesión de trabajo ────────────────────────────────────────────────────

    override fun iniciarSesion(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        if (camarero.estado != CamareroEstado.ACTIVA) return false
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(sesionActiva = true) else it }
        }
        lastSeen[camareroId] = System.currentTimeMillis()
        abrirJornada(camareroId)
        refrescarConectados()
        return true
    }

    override fun cortarSesion(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        if (!camarero.sesionActiva) return false
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(sesionActiva = false) else it }
        }
        lastSeen.remove(camareroId)
        cerrarJornadaAbierta(camareroId)
        _eventos.tryEmit(SalaEvent.cortada(camareroId))
        refrescarConectados()
        return true
    }

    /** Abre un intervalo de jornada si el camarero no tiene ya uno abierto (id estable por inicio). */
    private fun abrirJornada(camareroId: String) {
        if (_jornadas.value.any { it.camareroId == camareroId && it.fin == null }) return
        val inicio = System.currentTimeMillis()
        val jornada = JornadaLocal(id = "jornada:$camareroId:$inicio", camareroId = camareroId, inicio = inicio)
        _jornadas.update { it + jornada }
    }

    /** Cierra la jornada abierta del camarero (fin = ahora); no-op si no hay ninguna. */
    private fun cerrarJornadaAbierta(camareroId: String) {
        val ahora = System.currentTimeMillis()
        _jornadas.update { lista ->
            lista.map {
                if (it.camareroId == camareroId && it.fin == null) it.copy(fin = ahora) else it
            }
        }
    }

    override fun registrarHeartbeat(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        if (!camarero.sesionActiva) return false
        lastSeen[camareroId] = System.currentTimeMillis()
        refrescarConectados()
        return true
    }

    override fun tieneSesionActiva(camareroId: String): Boolean =
        _camareros.value.firstOrNull { it.id == camareroId }?.sesionActiva == true

    override fun cortarSesionesVencidas(timeoutMs: Long): Int {
        val ahora = System.currentTimeMillis()
        var cortadas = 0
        _camareros.value.filter { it.sesionActiva }.forEach { camarero ->
            val visto = lastSeen[camarero.id] ?: return@forEach
            if (ahora - visto > timeoutMs) {
                if (cortarSesion(camarero.id)) cortadas++
            }
        }
        refrescarConectados()
        return cortadas
    }

    /** Recalcula [conectados]: camareros con sesión activa y heartbeat dentro del timeout. */
    private fun refrescarConectados() {
        val ahora = System.currentTimeMillis()
        _conectados.value = _camareros.value.count { camarero ->
            camarero.sesionActiva &&
                ahora - (lastSeen[camarero.id] ?: 0L) <= BarRepository.HEARTBEAT_TIMEOUT_MS
        }
    }

    override fun ponerDeServicio(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        if (camarero.estado != CamareroEstado.ACTIVA) return false
        if (camarero.deServicio) return true
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(deServicio = true) else it }
        }
        refrescarDeServicio()
        return true
    }

    override fun quitarDeServicio(camareroId: String): Boolean {
        val camarero = _camareros.value.firstOrNull { it.id == camareroId } ?: return false
        if (!camarero.deServicio) return false
        _camareros.update { list ->
            list.map { if (it.id == camareroId) it.copy(deServicio = false) else it }
        }
        refrescarDeServicio()
        return true
    }

    /** Recalcula el derivado «de servicio» tras cualquier cambio en la lista blanca. */
    private fun refrescarDeServicio() {
        _deServicio.value = _camareros.value.filter { it.estado == CamareroEstado.ACTIVA && it.deServicio }
    }

    // ── Identity (config + invitaciones + espejo) ─────────────────────────────

    override fun setIdentityConfig(config: IdentityConfig) {
        _identityConfig.value = config
    }

    override fun registrarInvitacion(invitacion: Invitacion) {
        _invitaciones.update { it + invitacion }
    }

    override fun revocarInvitacionLocal(invitacionId: String): Boolean {
        if (_invitaciones.value.none { it.id == invitacionId }) return false
        _invitaciones.update { list ->
            list.map { if (it.id == invitacionId) it.copy(estado = InvitacionEstado.REVOCADA) else it }
        }
        return true
    }

    override fun sincronizarInvitaciones(invitaciones: List<Invitacion>) {
        _invitaciones.value = invitaciones
    }

    override fun sincronizarMiembros(camareroIds: List<String>) {
        camareroIds.forEach { id -> altaCamarero(id, null) }
        refrescarDeServicio()
    }

    override fun guardarClaveQr(key: QrKey) {
        _qrKey.value = key
    }

    override fun registrarAltaPendiente(alta: AltaPendiente) {
        _altasPendientes.update { list ->
            val idx = list.indexOfFirst { it.camareroId == alta.camareroId }
            if (idx >= 0) list.toMutableList().also { it[idx] = alta } else list + alta
        }
    }

    override fun eliminarAltaPendiente(camareroId: String) {
        _altasPendientes.update { it.filterNot { a -> a.camareroId == camareroId } }
    }

    // ── Libro de oficio (jornada + cola de servicios) ────────────────────────

    override fun registrarServicioPendiente(servicio: ServicioPendiente) {
        _serviciosPendientes.update { lista ->
            val idx = lista.indexOfFirst { it.eventoId == servicio.eventoId }
            if (idx >= 0) lista.toMutableList().also { it[idx] = servicio } else lista + servicio
        }
    }

    override fun eliminarServicioPendiente(eventoId: String) {
        _serviciosPendientes.update { it.filterNot { s -> s.eventoId == eventoId } }
    }

    override fun eliminarOperacionCatalogo(operationId: String) {
        _operacionesCatalogo.update { it.filterNot { op -> op.operationId == operationId } }
    }

    override fun actualizarRevisionProducto(aggregateId: String, revision: Int) {
        _revisionesProducto.update { it + (aggregateId to revision) }
    }

    override fun quitarRevisionProducto(aggregateId: String) {
        _revisionesProducto.update { it - aggregateId }
    }

    override fun encolarSeedCatalogo() {
        val pendientes = _operacionesCatalogo.value.map { it.aggregateId }.toSet()
        _catalogo.value.forEach { producto ->
            // Solo productos sin revisión canónica y sin operación ya encolada.
            if (producto.esPublicableEnWeb() &&
                producto.id !in _revisionesProducto.value &&
                producto.id !in pendientes
            ) {
                encolarOperacionCatalogo(producto.id, "crear", producto)
            }
        }
    }

    override fun aplicarCambiosCatalogo(cambios: List<CambioRemoto>, revisionActual: Int) {
        var catalogo = _catalogo.value
        var revisiones = _revisionesProducto.value
        cambios.forEach { cambio ->
            if (cambio.action == "archivar" || cambio.producto == null) {
                catalogo = catalogo.filterNot { it.id == cambio.aggregateId }
                revisiones = revisiones - cambio.aggregateId
            } else {
                val p = cambio.producto
                // Identity no conoce subfamilia/permiteNota (locales del nodo): al
                // aplicar un delta se conservan los campos locales del SKU existente.
                val existente = catalogo.firstOrNull { it.id == p.id }
                val local = existente?.copy(
                    nombre = p.nombre,
                    categoria = p.categoria,
                    precio = p.precio,
                    disponible = p.disponible,
                    descripcion = p.descripcion,
                ) ?: Producto(p.id, p.nombre, p.categoria, p.precio, p.disponible, descripcion = p.descripcion)
                catalogo = if (existente != null) {
                    catalogo.map { if (it.id == p.id) local else it }
                } else {
                    catalogo + local
                }
                revisiones = revisiones + (p.id to p.revision)
            }
        }
        _catalogo.value = catalogo
        _revisionesProducto.value = revisiones
        _catalogoSyncDesde.value = revisionActual
    }

    override fun fijarCursorCatalogo(revision: Int) {
        _catalogoSyncDesde.value = revision
    }
}
