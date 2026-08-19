package com.jaminsmoke.personalbar.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de verdad del nodo. Una implementación en memoria basta para v0.1;
 * Room será otra implementación del mismo seam sin tocar UI/ViewModel.
 */
interface BarRepository {
    /** Cuenta del establecimiento (un nodo = un establecimiento en v0.1). */
    val establecimiento: StateFlow<Establecimiento>

    /** @return true si se renombró; false si el nombre queda vacío. */
    fun renombrarEstablecimiento(nombre: String): Boolean

    /** Salas del mapa (primer nivel del layout). */
    val salas: StateFlow<List<Sala>>

    /** Mesas canónicas del nodo. */
    val mesas: StateFlow<List<Mesa>>

    /** Reservas (holds comerciales) activas/canceladas. */
    val reservas: StateFlow<List<Reserva>>

    /** Cola de bebida (tickets BARRA en PENDIENTE/LISTO). */
    val bebidaQueue: StateFlow<List<Ticket>>

    /** Cola de comida (tickets COCINA en PENDIENTE/LISTO). */
    val comidaQueue: StateFlow<List<Ticket>>

    /** Tickets completados (recogidos hoy, servidos mañana): salen de la cola y se acumulan aquí. */
    val servidos: StateFlow<List<Ticket>>

    /** Rondas recibidas. */
    val rondas: StateFlow<List<Ronda>>    /** Catálogo canónico del nodo. */
    val catalogo: StateFlow<List<Producto>>

    /** Grupos de modificadores de carta (locales del nodo; no sincronizan con Identity). */
    val gruposModificador: StateFlow<List<GrupoModificador>>

    /** Opciones de los grupos de modificadores. */
    val opcionesModificador: StateFlow<List<OpcionModificador>>

    /** Asignación N:M producto ↔ grupo de modificadores. */
    val productoGrupo: StateFlow<List<ProductoGrupo>>

    /** Lista blanca de camareros del establecimiento (mirror de Identity). */
    val camareros: StateFlow<List<Camarero>>

    /** Camareros ACTIVA marcados «de servicio» en el puesto (varios a la vez). */
    val deServicio: StateFlow<List<Camarero>>

    /**
     * Dispositivos vivos en la sala: camareros con sesión de trabajo activa cuyo
     * heartbeat no ha vencido ([HEARTBEAT_TIMEOUT_MS]). Refleja los Commanders que
     * «ven» el nodo ahora mismo.
     */
    val conectados: StateFlow<Int>

    /** Configuración de la conexión con Identity (v0.1 in-memory). */
    val identityConfig: StateFlow<IdentityConfig>

    /** Invitaciones por email creadas desde Bar (registro local; la verdad vive en Identity). */
    val invitaciones: StateFlow<List<Invitacion>>

    /** Eventos de sala (listo/servido) para re-enviar por SSE a Commander. */
    val eventos: SharedFlow<SalaEvent>

    /** @return true si la ronda se procesó; false si ya existía (idempotente). */
    fun crearRonda(ronda: Ronda): Boolean

    /** @return true si se creó el producto con id UUID; false si el nombre o la categoría están vacíos. */
    fun crearProducto(nombre: String, categoria: String, precio: Double, subfamilia: String? = null, permiteNota: Boolean = false, descripcion: String? = null): Boolean

    /** @return true si se actualizó; false si no existe o los campos quedan vacíos. No cambia el id. */
    fun editarProducto(id: String, nombre: String, categoria: String, precio: Double, disponible: Boolean, subfamilia: String? = null, permiteNota: Boolean = false, descripcion: String? = null): Boolean

    /** @return true si se borró; false si no existe. */
    fun borrarProducto(id: String): Boolean

    /** @return true si se creó el grupo; false si el nombre queda vacío. */
    fun crearGrupoModificador(nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean

    /** @return true si se actualizó; false si no existe o el nombre queda vacío. */
    fun editarGrupoModificador(id: String, nombre: String, multiple: Boolean, obligatorio: Boolean): Boolean

    /** @return true si se borró el grupo (y sus opciones y asignaciones); false si no existe. */
    fun borrarGrupoModificador(id: String): Boolean

    /** @return true si se creó la opción; false si el grupo no existe o el nombre queda vacío. */
    fun crearOpcionModificador(grupoId: String, nombre: String, deltaPrecio: Double, alias: String): Boolean

    /** @return true si se actualizó; false si no existe o el nombre queda vacío. */
    fun editarOpcionModificador(id: String, nombre: String, deltaPrecio: Double, alias: String): Boolean

    /** @return true si se borró; false si no existe. */
    fun borrarOpcionModificador(id: String): Boolean

    /** @return true si se asignó; false si el producto o el grupo no existen. */
    fun asignarGrupoProducto(productoId: String, grupoId: String): Boolean

    /** @return true si se quitó la asignación; false si no existía. */
    fun desasignarGrupoProducto(productoId: String, grupoId: String): Boolean

    /** @return true si el ticket estaba PENDIENTE y se marcó PREPARADO con el preparador. */
    fun marcarPreparado(ticketId: String, preparadoPor: String): Boolean

    /** @return true si el ticket estaba PREPARADO y pasó a RECOGIDO (sale de la cola). */
    fun marcarRecogido(ticketId: String): Boolean

    /** Reemplaza el layout local (salas + mesas) por el espejo de Identity (recuperación). */
    fun reemplazarLayout(salas: List<Sala>, mesas: List<Mesa>)

    /** @return true si se creó la sala; false si el nombre ya existe o está vacío. */
    fun crearSala(nombre: String): Boolean

    /** @return true si se renombró; false si no existe o el nombre ya está en uso. */
    fun renombrarSala(salaId: String, nombre: String): Boolean

    /** @return true si se eliminó; false si no existe o tiene mesas. */
    fun eliminarSala(salaId: String): Boolean

    /** @return true si se creó la mesa (auto-posicionada en celda libre). */
    fun crearMesa(salaId: String, forma: MesaForma, capacidad: Int, alias: String?): Boolean

    /** @return true si se actualizó la configuración de la mesa. */
    fun editarMesa(mesaId: String, alias: String?, capacidad: Int, forma: MesaForma): Boolean

    /** @return true si se borró; false si no existe o tiene reserva activa. */
    fun borrarMesa(mesaId: String): Boolean

    /** @return true si se movió la mesa a (posX, posY). */
    fun moverMesa(mesaId: String, posX: Float, posY: Float): Boolean

    /** @return true si se giró (re-encuadrando en celda libre si hace falta). */
    fun girarMesa(mesaId: String): Boolean

    /** @return true si se reservó; false si ocupada/bloqueada/ya reservada o nombre vacío. */
    fun reservar(mesaId: String, nombre: String, paraEpoch: Long?): Boolean

    /** @return true si se canceló la reserva activa de la mesa. */
    fun cancelarReserva(mesaId: String): Boolean

    /** @return true si se bloqueó; false si ocupada o inexistente. */
    fun bloquearMesa(mesaId: String): Boolean

    /** @return true si se desbloqueó; false si inexistente. */
    fun desbloquearMesa(mesaId: String): Boolean

    /**
     * @return true si se dio de alta; false si ya existe activa.
     * [nombre]/[email] se rellenan desde Identity si están disponibles
     * (Bar no edita datos de la cuenta; solo los recoge).
     */
    fun altaCamarero(camareroId: String, credencialId: String?, nombre: String? = null, email: String? = null): Boolean

    /** @return true si se revocó; false si no existe. */
    fun revocarCamarero(camareroId: String): Boolean

    /** @return true si el camarero ACTIVA existe y pasa a estar de servicio. */
    fun ponerDeServicio(camareroId: String): Boolean

    /** @return true si el camarero estaba de servicio y se quita (no revoca). */
    fun quitarDeServicio(camareroId: String): Boolean

    /** Actualiza la configuración de la conexión con Identity (estado de la UI). */
    fun setIdentityConfig(config: IdentityConfig)

    /** Registra una invitación creada en Identity (pendiente). */
    fun registrarInvitacion(invitacion: Invitacion)

    /** @return true si la invitación local existe y se marcó revocada. */
    fun revocarInvitacionLocal(invitacionId: String): Boolean

    /** Espejo: reemplaza el mirror local de invitaciones por el listado de Identity (la verdad vive allí). */
    fun sincronizarInvitaciones(invitaciones: List<Invitacion>)

    /** Espejo: asegura que cada id (miembros ACTIVA de Identity) esté en la lista blanca local. */
    fun sincronizarMiembros(camareroIds: List<String>)

    /** Clave pública Ed25519 de Identity cacheada (para verificar QRs offline). */
    val qrKey: StateFlow<QrKey?>

    /** Guarda/actualiza la clave pública de Identity (cache). */
    fun guardarClaveQr(key: QrKey)

    /** Altas offline pendientes de subir a Identity. */
    val altasPendientes: StateFlow<List<AltaPendiente>>

    /** Registra un alta offline pendiente de sync. */
    fun registrarAltaPendiente(alta: AltaPendiente)

    /** Elimina un alta pendiente (tras subirla a Identity). */
    fun eliminarAltaPendiente(camareroId: String)

    // ── Libro de oficio (historial de jornada + proyección a Identity) ───────

    /** Intervalos de jornada local (abiertos/cerrados), por camarero. */
    val jornadas: StateFlow<List<JornadaLocal>>

    /**
     * Resumen de jornadas para el periodo [desde]..[hasta] (epoch ms; null = sin
     * límite): intervalos crudos + horas trabajadas y **mesas distintas** servidas
     * por camarero (mesa con al menos un ticket RECOGIDO en el periodo; el periodo
     * se acota por `Ronda.creadoEn`, aproximación v0.1). Lo consume el GET LAN
     * `/v1/sesion/jornadas` y la vista del puesto.
     */
    fun resumenJornadas(desde: Long?, hasta: Long?): JornadasResumen

    /** Horario del establecimiento (local): una fila por día; vacío = sin configurar. */
    val horario: StateFlow<List<HorarioLocal>>

    /** Reemplaza el horario completo (persistido en Room; offline como layout/carta). */
    fun guardarHorario(horario: List<HorarioLocal>)

    /** Eventos de servicio pendientes de subir a Identity (cola persistente). */
    val serviciosPendientes: StateFlow<List<ServicioPendiente>>

    /** Encola un evento de servicio (idempotente por `eventoId`; PK de la cola). */
    fun registrarServicioPendiente(servicio: ServicioPendiente)

    /** Elimina un evento de la cola (tras subirlo a Identity). */
    fun eliminarServicioPendiente(eventoId: String)

    // ── Sync de catálogo (outbox → Identity) ─────────────────────────────────

    /** Operaciones de catálogo pendientes de subir a Identity (outbox persistente). */
    val operacionesCatalogo: StateFlow<List<OperacionCatalogo>>

    /** Revisión canónica por producto (aggregateId → revision), mirror del sync. */
    val revisionesProducto: StateFlow<Map<String, Int>>

    /** Elimina una operación del outbox (tras entregarla a Identity). */
    fun eliminarOperacionCatalogo(operationId: String)

    /** Guarda/actualiza la revisión canónica de un producto (respuesta del server). */
    fun actualizarRevisionProducto(aggregateId: String, revision: Int)

    /** Quita la revisión de un producto (archivado en el server). */
    fun quitarRevisionProducto(aggregateId: String)

    /** Cursor del pull de deltas (revisión global del establecimiento ya vista). */
    val catalogoSyncDesde: StateFlow<Int>

    /**
     * Encola una operación `crear` por cada producto local aún sin sincronizar
     * (seed inicial). Idempotente: no re-encola productos con revisión canónica
     * o con una operación ya pendiente en el outbox.
     */
    fun encolarSeedCatalogo()

    /** Aplica deltas del server (`GET /sync/cambios`) y avanza el cursor a [revisionActual]. */
    fun aplicarCambiosCatalogo(cambios: List<CambioRemoto>, revisionActual: Int)

    /** Fija el cursor global sin aplicar cambios (decisión de seed o divergencia). */
    fun fijarCursorCatalogo(revision: Int)

    // ── Sesión de trabajo (jornada concedida por Bar) ────────────────────────

    /** @return true si el camarero ACTIVA pasa a sesión activa (Bar concede la jornada). */
    fun iniciarSesion(camareroId: String): Boolean

    /** @return true si tenía sesión activa y se corta (emite `sesion.cortada`). */
    fun cortarSesion(camareroId: String): Boolean

    /** @return true si la sesión sigue activa y se refresca `lastSeen`; false si no hay sesión (→ 403). */
    fun registrarHeartbeat(camareroId: String): Boolean

    /** @return true si el camarero tiene sesión activa en este momento. */
    fun tieneSesionActiva(camareroId: String): Boolean

    /** Corta las sesiones sin heartbeat dentro de [timeoutMs]; devuelve cuántas cortó. */
    fun cortarSesionesVencidas(timeoutMs: Long): Int

    companion object {
        /**
         * Sin heartbeat dentro de este tiempo, la sesión de trabajo se corta
         * (auto-inactivación por salida de LAN). Es la misma referencia temporal
         * que define «conectado» en [InMemoryBarRepository.refrescarConectados].
         */
        const val HEARTBEAT_TIMEOUT_MS: Long = 30_000L
    }
}
