package com.jaminsmoke.personalbar.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementación Room del seam [BarRepository] (escritura-through).
 *
 * El **cerebro** es un [InMemoryBarRepository] interno (lógica, colas, idempotencia,
 * secuencias — no se duplica); cada mutación que devuelve `true` persiste el dominio
 * afectado a Room en un scope de escritura **serializado** (un solo hilo: el último
 * en ejecutar escribe el estado final correcto).
 *
 * Carga inicial en el constructor (síncrona, volumen pequeño): si la BD está vacía
 * (primera instalación) siembra la semilla inicial (establecimiento y catálogo por
 * defecto) y lo persiste; si no, reconstruye el
 * estado completo desde Room (colas y servidos se derivan de `estado`+`destino`).
 */
class RoomBarRepository(
    private val db: AppDatabase,
    establecimientoInicial: Establecimiento = Establecimiento("local-1", "Mi local"),
    salasIniciales: List<Sala> = emptyList(),
    catalogoInicial: List<Producto> = emptyList(),
    mesasIniciales: List<Mesa> = emptyList(),
    rondasDemo: List<Ronda> = emptyList(),
) : BarRepository {

    private val dao = db.barDao()
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val writesPendientes = java.util.concurrent.atomic.AtomicInteger(0)

    private val inner: InMemoryBarRepository = runBlocking {
        val salasBd = dao.getSalas()
        if (salasBd.isEmpty()) {
            // Primera instalación: sembrar la semilla inicial y persistirla.
            val seed = InMemoryBarRepository(
                establecimientoInicial = establecimientoInicial,
                salasIniciales = salasIniciales,
                catalogoInicial = catalogoInicial,
                mesasIniciales = mesasIniciales,
            )
            rondasDemo.forEach { seed.crearRonda(it) }
            persistAll(seed, dao)
            seed
        } else {
            val tickets = dao.getTickets()
            // Backfill de `numeroCola` (BD v1): los tickets sin número reciben la
            // secuencia por destino en orden estable (rondaId); los recogidos se ignoran.
            val (renumerados, siguienteCola) = backfillNumeroCola(tickets)
            if (renumerados != tickets) {
                dao.replaceTickets(renumerados)
            }
            // Migración de layout: con el canvas horizontal (2600×2000), las mesas
            // persistidas fuera de los nuevos límites se reubican al grid más cercano.
            val mesasBd = dao.getMesas()
            val fuera = mesasBd.any { m ->
                val (w, h) = mesaDims(m.forma, m.girada)
                m.posX < CELL_F || m.posY < CELL_F ||
                    m.posX + w > ZONA_ANCHO - CELL_F || m.posY + h > ZONA_ALTO - CELL_F
            }
            val mesasIniciales = if (fuera) {
                val reparadas = normalizarMesasEnGrid(mesasBd)
                val list = mesasBd.map { m ->
                    reparadas[m.id]?.let { (x, y) -> m.copy(posX = x, posY = y) } ?: m
                }
                dao.replaceMesas(list)
                list
            } else {
                mesasBd
            }
            val sesionBd = dao.getSesionNegocio()
            InMemoryBarRepository(
                establecimientoInicial = dao.getEstablecimiento() ?: establecimientoInicial,
                salasIniciales = salasBd,
                catalogoInicial = dao.getProductos(),
                mesasIniciales = mesasIniciales,
                rondasIniciales = dao.getRondas(),
                bebidaInicial = renumerados.filter { it.destino == Destino.BARRA && it.estado != TicketEstado.RECOGIDO },
                comidaInicial = renumerados.filter { it.destino == Destino.COCINA && it.estado != TicketEstado.RECOGIDO },
                servidosIniciales = renumerados.filter { it.estado == TicketEstado.RECOGIDO },
                reservasIniciales = dao.getReservas(),
                camarerosIniciales = dao.getCamareros(),
                invitacionesIniciales = dao.getInvitaciones(),
                identityConfigInicial = identityConfigConSesion(dao.getIdentityConfig(), sesionBd),
                siguienteColaInicial = siguienteCola,
                qrKeyInicial = dao.getQrKey(),
                altasPendientesIniciales = dao.getAltasPendientes(),
                jornadasIniciales = dao.getJornadas(),
                serviciosPendientesIniciales = dao.getServiciosPendientes(),
                operacionesCatalogoIniciales = dao.getOperacionesCatalogo(),
                revisionesProductoIniciales = dao.getProductosSync().associate { it.aggregateId to it.revision },                catalogoSyncDesdeInicial = dao.getCatalogoSyncEstado()?.desdeRevision ?: 0,
                horarioInicial = dao.getHorario(),
                gruposModificadorIniciales = dao.getGruposModificador(),
                opcionesModificadorIniciales = dao.getOpcionesModificador(),
                productoGrupoIniciales = dao.getProductoGrupo(),
            )
        }
    }

    // ── StateFlows (delegados al cerebro) ────────────────────────────────────

    override val establecimiento: StateFlow<Establecimiento> get() = inner.establecimiento
    override val salas: StateFlow<List<Sala>> get() = inner.salas
    override val mesas: StateFlow<List<Mesa>> get() = inner.mesas
    override val reservas: StateFlow<List<Reserva>> get() = inner.reservas
    override val bebidaQueue: StateFlow<List<Ticket>> get() = inner.bebidaQueue
    override val comidaQueue: StateFlow<List<Ticket>> get() = inner.comidaQueue
    override val servidos: StateFlow<List<Ticket>> get() = inner.servidos
    override val rondas: StateFlow<List<Ronda>> get() = inner.rondas
    override val catalogo: StateFlow<List<Producto>> get() = inner.catalogo
    override val camareros: StateFlow<List<Camarero>> get() = inner.camareros
    override val deServicio: StateFlow<List<Camarero>> get() = inner.deServicio
    override val conectados: StateFlow<Int> get() = inner.conectados
    override val identityConfig: StateFlow<IdentityConfig> get() = inner.identityConfig
    override val invitaciones: StateFlow<List<Invitacion>> get() = inner.invitaciones
    override val qrKey: StateFlow<QrKey?> get() = inner.qrKey
    override val altasPendientes: StateFlow<List<AltaPendiente>> get() = inner.altasPendientes
    override val jornadas: StateFlow<List<JornadaLocal>> get() = inner.jornadas
    override val serviciosPendientes: StateFlow<List<ServicioPendiente>> get() = inner.serviciosPendientes
    override val operacionesCatalogo: StateFlow<List<OperacionCatalogo>> get() = inner.operacionesCatalogo
    override val revisionesProducto: StateFlow<Map<String, Int>> get() = inner.revisionesProducto
    override val catalogoSyncDesde: StateFlow<Int> get() = inner.catalogoSyncDesde

    override val horario: StateFlow<List<HorarioLocal>> get() = inner.horario
    override val gruposModificador: StateFlow<List<GrupoModificador>> get() = inner.gruposModificador
    override val opcionesModificador: StateFlow<List<OpcionModificador>> get() = inner.opcionesModificador
    override val productoGrupo: StateFlow<List<ProductoGrupo>> get() = inner.productoGrupo

    override fun resumenJornadas(desde: Long?, hasta: Long?): JornadasResumen = inner.resumenJornadas(desde, hasta)

    override fun guardarHorario(horario: List<HorarioLocal>) {
        inner.guardarHorario(horario)
        writeScope.launch { dao.replaceHorario(horario) }
    }
    override val eventos: SharedFlow<SalaEvent> get() = inner.eventos

    // ── Rondas / tickets ─────────────────────────────────────────────────────

    override fun crearRonda(ronda: Ronda): Boolean {
        val ok = inner.crearRonda(ronda)
        if (ok) persist { dao.replaceRondas(inner.rondas.value); dao.replaceTickets(ticketsActuales()) }
        return ok
    }

    override fun crearProducto(nombre: String, categoria: String, precio: Double, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val ok = inner.crearProducto(nombre, categoria, precio, subfamilia, permiteNota, descripcion)
        if (ok) persist {
            dao.replaceProductos(inner.catalogo.value)
            dao.replaceOperacionesCatalogo(inner.operacionesCatalogo.value)
        }
        return ok
    }

    override fun editarProducto(id: String, nombre: String, categoria: String, precio: Double, disponible: Boolean, subfamilia: String?, permiteNota: Boolean, descripcion: String?): Boolean {
        val ok = inner.editarProducto(id, nombre, categoria, precio, disponible, subfamilia, permiteNota, descripcion)
        if (ok) persist {
            dao.replaceProductos(inner.catalogo.value)
            dao.replaceOperacionesCatalogo(inner.operacionesCatalogo.value)
        }
        return ok
    }

    override fun borrarProducto(id: String): Boolean {
        val ok = inner.borrarProducto(id)
        if (ok) persist {
            dao.replaceProductos(inner.catalogo.value)
            dao.replaceOperacionesCatalogo(inner.operacionesCatalogo.value)
            dao.replaceProductoGrupo(inner.productoGrupo.value)
        }
        return ok
    }

    override fun crearGrupoModificador(nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val ok = inner.crearGrupoModificador(nombre, multiple, obligatorio)
        if (ok) persist { dao.replaceGruposModificador(inner.gruposModificador.value) }
        return ok
    }

    override fun editarGrupoModificador(id: String, nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean {
        val ok = inner.editarGrupoModificador(id, nombre, multiple, obligatorio)
        if (ok) persist { dao.replaceGruposModificador(inner.gruposModificador.value) }
        return ok
    }

    override fun borrarGrupoModificador(id: String): Boolean {
        val ok = inner.borrarGrupoModificador(id)
        if (ok) persist {
            dao.replaceGruposModificador(inner.gruposModificador.value)
            dao.replaceOpcionesModificador(inner.opcionesModificador.value)
            dao.replaceProductoGrupo(inner.productoGrupo.value)
        }
        return ok
    }

    override fun crearOpcionModificador(grupoId: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val ok = inner.crearOpcionModificador(grupoId, nombre, deltaPrecio, alias)
        if (ok) persist { dao.replaceOpcionesModificador(inner.opcionesModificador.value) }
        return ok
    }

    override fun editarOpcionModificador(id: String, nombre: String, deltaPrecio: Double, alias: String): Boolean {
        val ok = inner.editarOpcionModificador(id, nombre, deltaPrecio, alias)
        if (ok) persist { dao.replaceOpcionesModificador(inner.opcionesModificador.value) }
        return ok
    }

    override fun borrarOpcionModificador(id: String): Boolean {
        val ok = inner.borrarOpcionModificador(id)
        if (ok) persist { dao.replaceOpcionesModificador(inner.opcionesModificador.value) }
        return ok
    }

    override fun guardarGrupoConOpciones(
        grupoId: String?,
        nombre: String,
        multiple: Boolean,
        obligatorio: Boolean,
        opciones: List<OpcionModificadorBorrador>,
    ): Boolean {
        val ok = inner.guardarGrupoConOpciones(grupoId, nombre, multiple, obligatorio, opciones)
        if (ok) persist {
            dao.replaceGruposModificador(inner.gruposModificador.value)
            dao.replaceOpcionesModificador(inner.opcionesModificador.value)
        }
        return ok
    }

    override fun asignarGrupoProducto(productoId: String, grupoId: String): Boolean {
        val ok = inner.asignarGrupoProducto(productoId, grupoId)
        if (ok) persist { dao.replaceProductoGrupo(inner.productoGrupo.value) }
        return ok
    }

    override fun desasignarGrupoProducto(productoId: String, grupoId: String): Boolean {
        val ok = inner.desasignarGrupoProducto(productoId, grupoId)
        if (ok) persist { dao.replaceProductoGrupo(inner.productoGrupo.value) }
        return ok
    }


    override fun marcarPreparado(ticketId: String, preparadoPor: String): Boolean {
        val ok = inner.marcarPreparado(ticketId, preparadoPor)
        if (ok) persist { dao.replaceTickets(ticketsActuales()) }
        return ok
    }

    override fun marcarRecogido(ticketId: String): Boolean {
        val ok = inner.marcarRecogido(ticketId)
        if (ok) persist {
            dao.replaceTickets(ticketsActuales())
            // Recoger el último ticket de una ronda encola «ronda servida» (libro de
            // oficio): persistirlo para que sobreviva al reinicio.
            dao.replaceServiciosPendientes(inner.serviciosPendientes.value)
        }
        return ok
    }

    // ── Salas / mesas ────────────────────────────────────────────────────────

    override fun reemplazarLayout(salas: List<Sala>, mesas: List<Mesa>) {
        inner.reemplazarLayout(salas, mesas)
        persist { dao.replaceSalas(inner.salas.value); dao.replaceMesas(inner.mesas.value) }
    }

    override fun renombrarEstablecimiento(nombre: String): Boolean {
        val ok = inner.renombrarEstablecimiento(nombre)
        if (ok) persist { dao.upsertEstablecimiento(inner.establecimiento.value) }
        return ok
    }

    override fun crearSala(nombre: String): Boolean {
        val ok = inner.crearSala(nombre)
        if (ok) persist { dao.replaceSalas(inner.salas.value) }
        return ok
    }

    override fun renombrarSala(salaId: String, nombre: String): Boolean {
        val ok = inner.renombrarSala(salaId, nombre)
        if (ok) persist { dao.replaceSalas(inner.salas.value) }
        return ok
    }

    override fun eliminarSala(salaId: String): Boolean {
        val ok = inner.eliminarSala(salaId)
        if (ok) persist { dao.replaceSalas(inner.salas.value) }
        return ok
    }

    override fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean {
        val ok = inner.crearMesa(salaId, forma, capacidad, alias)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean {
        val ok = inner.editarMesa(mesaId, alias, capacidad, forma)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun borrarMesa(mesaId: String): Boolean {
        val ok = inner.borrarMesa(mesaId)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean {
        val ok = inner.moverMesa(mesaId, posX, posY)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun girarMesa(mesaId: String): Boolean {
        val ok = inner.girarMesa(mesaId)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    // ── Reservas / bloqueos ──────────────────────────────────────────────────

    override fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean {
        val ok = inner.reservar(mesaId, nombre, paraEpoch)
        if (ok) persist { dao.replaceReservas(inner.reservas.value); dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun cancelarReserva(mesaId: String): Boolean {
        val ok = inner.cancelarReserva(mesaId)
        if (ok) persist { dao.replaceReservas(inner.reservas.value); dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun bloquearMesa(mesaId: String): Boolean {
        val ok = inner.bloquearMesa(mesaId)
        if (ok) persist { dao.replaceReservas(inner.reservas.value); dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    override fun desbloquearMesa(mesaId: String): Boolean {
        val ok = inner.desbloquearMesa(mesaId)
        if (ok) persist { dao.replaceMesas(inner.mesas.value) }
        return ok
    }

    // ── Camareros / Identity ─────────────────────────────────────────────────

    override fun altaCamarero(camareroId: String, credencialId: String?, nombre: String?, email: String?): Boolean {
        val ok = inner.altaCamarero(camareroId, credencialId, nombre, email)
        if (ok) persist { dao.replaceCamareros(inner.camareros.value) }
        return ok
    }

    override fun revocarCamarero(camareroId: String): Boolean {
        val ok = inner.revocarCamarero(camareroId)
        if (ok) persist {
            dao.replaceCamareros(inner.camareros.value)
            dao.replaceJornadas(inner.jornadas.value)
        }
        return ok
    }

    override fun ponerDeServicio(camareroId: String): Boolean {
        val ok = inner.ponerDeServicio(camareroId)
        if (ok) persist { dao.replaceCamareros(inner.camareros.value) }
        return ok
    }

    override fun quitarDeServicio(camareroId: String): Boolean {
        val ok = inner.quitarDeServicio(camareroId)
        if (ok) persist { dao.replaceCamareros(inner.camareros.value) }
        return ok
    }

    override fun iniciarSesion(camareroId: String): Boolean {
        val ok = inner.iniciarSesion(camareroId)
        if (ok) persist {
            dao.replaceCamareros(inner.camareros.value)
            dao.replaceJornadas(inner.jornadas.value)
        }
        return ok
    }

    override fun cortarSesion(camareroId: String): Boolean {
        val ok = inner.cortarSesion(camareroId)
        if (ok) persist {
            dao.replaceCamareros(inner.camareros.value)
            dao.replaceJornadas(inner.jornadas.value)
        }
        return ok
    }

    override fun registrarHeartbeat(camareroId: String): Boolean = inner.registrarHeartbeat(camareroId)

    override fun tieneSesionActiva(camareroId: String): Boolean = inner.tieneSesionActiva(camareroId)

    override fun cortarSesionesVencidas(timeoutMs: Long): Int {
        val cortadas = inner.cortarSesionesVencidas(timeoutMs)
        if (cortadas > 0) persist { dao.replaceCamareros(inner.camareros.value) }
        return cortadas
    }

    override fun setIdentityConfig(config: IdentityConfig) {
        inner.setIdentityConfig(config)
        persist { dao.upsertIdentityConfig(inner.identityConfig.value) }
    }

    override fun registrarInvitacion(invitacion: Invitacion) {
        inner.registrarInvitacion(invitacion)
        persist { dao.replaceInvitaciones(inner.invitaciones.value) }
    }

    override fun revocarInvitacionLocal(invitacionId: String): Boolean {
        val ok = inner.revocarInvitacionLocal(invitacionId)
        if (ok) persist { dao.replaceInvitaciones(inner.invitaciones.value) }
        return ok
    }

    override fun sincronizarInvitaciones(invitaciones: List<Invitacion>) {
        inner.sincronizarInvitaciones(invitaciones)
        persist { dao.replaceInvitaciones(inner.invitaciones.value) }
    }

    override fun sincronizarMiembros(camareroIds: List<String>) {
        inner.sincronizarMiembros(camareroIds)
        persist { dao.replaceCamareros(inner.camareros.value) }
    }

    override fun guardarClaveQr(key: QrKey) {
        inner.guardarClaveQr(key)
        persist { dao.upsertQrKey(key) }
    }

    override fun registrarAltaPendiente(alta: AltaPendiente) {
        inner.registrarAltaPendiente(alta)
        persist { dao.insertAltaPendiente(alta) }
    }

    override fun eliminarAltaPendiente(camareroId: String) {
        inner.eliminarAltaPendiente(camareroId)
        persist { dao.deleteAltaPendiente(camareroId) }
    }

    override fun registrarServicioPendiente(servicio: ServicioPendiente) {
        inner.registrarServicioPendiente(servicio)
        persist { dao.replaceServiciosPendientes(inner.serviciosPendientes.value) }
    }

    override fun eliminarServicioPendiente(eventoId: String) {
        inner.eliminarServicioPendiente(eventoId)
        persist { dao.replaceServiciosPendientes(inner.serviciosPendientes.value) }
    }

    override fun eliminarOperacionCatalogo(operationId: String) {
        inner.eliminarOperacionCatalogo(operationId)
        persist { dao.replaceOperacionesCatalogo(inner.operacionesCatalogo.value) }
    }

    override fun actualizarRevisionProducto(aggregateId: String, revision: Int) {
        inner.actualizarRevisionProducto(aggregateId, revision)
        persist { dao.replaceProductosSync(revisionesARows()) }
    }

    override fun quitarRevisionProducto(aggregateId: String) {
        inner.quitarRevisionProducto(aggregateId)
        persist { dao.replaceProductosSync(revisionesARows()) }
    }

    override fun encolarSeedCatalogo() {
        inner.encolarSeedCatalogo()
        persist { dao.replaceOperacionesCatalogo(inner.operacionesCatalogo.value) }
    }

    override fun aplicarCambiosCatalogo(cambios: List<CambioRemoto>, revisionActual: Int) {
        inner.aplicarCambiosCatalogo(cambios, revisionActual)
        persist {
            dao.replaceProductos(inner.catalogo.value)
            dao.replaceProductosSync(revisionesARows())
            dao.upsertCatalogoSyncEstado(CatalogoSyncEstado(desdeRevision = inner.catalogoSyncDesde.value))
        }
    }

    override fun fijarCursorCatalogo(revision: Int) {
        inner.fijarCursorCatalogo(revision)
        persist { dao.upsertCatalogoSyncEstado(CatalogoSyncEstado(desdeRevision = inner.catalogoSyncDesde.value)) }
    }

    private fun revisionesARows(): List<ProductoSync> =
        inner.revisionesProducto.value.map { (id, revision) -> ProductoSync(id, revision) }

    // ── Persistencia ─────────────────────────────────────────────────────────

    private fun ticketsActuales(): List<Ticket> =
        inner.bebidaQueue.value + inner.comidaQueue.value + inner.servidos.value

    /**
     * Asigna `numeroCola` por destino a los tickets sin número (migración v1→v2),
     * en el orden de [BarDao.getTickets] (estable por rondaId). Devuelve la lista
     * renumerada y la secuencia siguiente por destino (para continuar el turno).
     */
    private fun backfillNumeroCola(tickets: List<Ticket>): Pair<List<Ticket>, Map<Destino, Int>> {
        val secuencia = mutableMapOf<Destino, Int>()
        val renumerados = tickets.map { t ->
            if (t.numeroCola > 0) {
                secuencia[t.destino] = maxOf(secuencia[t.destino] ?: 0, t.numeroCola)
                t
            } else if (t.estado == TicketEstado.RECOGIDO) {
                t
            } else {
                val n = (secuencia[t.destino] ?: 0) + 1
                secuencia[t.destino] = n
                t.copy(numeroCola = n)
            }
        }
        return renumerados to secuencia
    }

    /** Lanza la escritura en el scope serializado; errores se loguean (estado en memoria sigue mandando). */
    private fun persist(block: suspend () -> Unit) {
        writesPendientes.incrementAndGet()
        writeScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "Persistencia Room fallida (estado en memoria intacto)", t)
            } finally {
                writesPendientes.decrementAndGet()
            }
        }
    }

    /**
     * Espera a que las escrituras pendientes terminen. Solo para tests
     * (recarga determinista); en producción la persistencia es best-effort.
     */
    @VisibleForTesting
    suspend fun awaitPersistencia() {
        while (writesPendientes.get() > 0) {
            kotlinx.coroutines.delay(10)
        }
    }

    private companion object {
        const val TAG = "RoomBarRepository"

        /**
         * `identity_config.conectado` solo puede ser true si hay sesión de negocio
         * persistida con token (Recuérdame). Un login sin «Recuérdame» dejaba
         * `conectado=true` pegado en Room sin sesión real → al reiniciar los gates
         * creían que había sesión. Sin token, se fuerza `conectado=false`.
         */
        fun identityConfigConSesion(
            config: IdentityConfig?,
            sesion: SesionNegocio?,
        ): IdentityConfig {
            val base = config ?: IdentityConfig()
            val haySesion = sesion?.token != null
            return if (haySesion) base else base.copy(conectado = false)
        }

        /** Escribe el estado completo de [seed] a la BD (primera instalación). */
        suspend fun persistAll(seed: InMemoryBarRepository, dao: BarDao) {
            dao.upsertEstablecimiento(seed.establecimiento.value)
            dao.replaceSalas(seed.salas.value)
            dao.replaceMesas(seed.mesas.value)
            dao.replaceProductos(seed.catalogo.value)
            dao.replaceRondas(seed.rondas.value)
            dao.replaceTickets(
                seed.bebidaQueue.value + seed.comidaQueue.value + seed.servidos.value
            )
            dao.replaceReservas(seed.reservas.value)
            dao.replaceCamareros(seed.camareros.value)
            dao.replaceInvitaciones(seed.invitaciones.value)
            dao.upsertIdentityConfig(seed.identityConfig.value)
            dao.replaceHorario(seed.horario.value)
            dao.replaceGruposModificador(seed.gruposModificador.value)
            dao.replaceOpcionesModificador(seed.opcionesModificador.value)
            dao.replaceProductoGrupo(seed.productoGrupo.value)
        }

    }
}
