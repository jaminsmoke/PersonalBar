package com.jaminsmoke.personalbar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Cuenta del establecimiento (negocio/local). Fuente de verdad en Bar.
 * v0.1: un nodo = un establecimiento; Identity aporta la identidad externa.
 * Persistida en Room (tabla singleton, 1 fila).
 */
@Serializable
@Entity(tableName = "establecimientos")
data class Establecimiento(
    @PrimaryKey val idEstable: String,
    val nombre: String,
)

/**
 * Sala de servicio del mapa (barra, interior, terraza…). Primer nivel del layout;
 * las mesas cuelgan de una sala.
 */
@Serializable
@Entity(tableName = "salas")
data class Sala(
    @PrimaryKey val id: String,
    val nombre: String,
    val orden: Int,
)

/**
 * Mesa canónica del nodo. Identidad canónica de familia: [mesaUuid] (UUID
 * estable e inmutable, no reutilizable: QR CFC, sincronización y correlación
 * cross-repo). Identidad local [id] (clave Room; `mesa-N`); identidad de red
 * visible es [idZona] (prefijo de sala + [indiceZona], etiqueta derivada y
 * mutable), nunca el id local. [alias] es texto libre solo para presentación.
 */
@Serializable
@Entity(tableName = "mesas")
data class Mesa(
    @PrimaryKey val id: String,
    /**
     * UUID canónico e inmutable de familia (QR/CFC/sync con Identity y Commander).
     * Se asigna una sola vez al crear la mesa y nunca se reutiliza al borrar
     * y recrear. Vacío (``) en mesas migradas antes de v18 hasta el backfill
     * de [RoomBarRepository], que lo rellena una vez y lo persiste.
     */
    val mesaUuid: String = "",
    val salaId: String,
    val indiceZona: Int,
    val numero: Int = 0,
    val alias: String? = null,
    val forma: MesaForma = MesaForma.CUADRADA,
    val capacidad: Int = 4,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val girada: Boolean = false,
    val bloqueada: Boolean = false,
    val reservaActivaId: String? = null,
) {
    /** ID dentro de la sala, p. ej. "B1" para Barra 1. Requiere el nombre de la sala. */
    fun idZona(nombreSala: String): String = "${zonaPrefijo(nombreSala)}$indiceZona"

    /** Nombre visible: alias del usuario si existe; si no, el ID de zona (B1, T2…). */
    fun nombreVisible(nombreSala: String): String = alias ?: idZona(nombreSala)
}

/**
 * Zona de una sala: área rectangular del board (nivel intermedio Sala → Zona → Mesa).
 *
 * Color de paleta fija ([ZonaColor], token de espacio físico fuera del theme) y
 * camarero asignado opcional ([camareroId] null = sin asignar; referencia a
 * `Camarero.id` sin FK — Bar solo asigna, no edita la cuenta). La pertenencia de
 * mesas se deriva por intersección geométrica ([zonaContieneMesa]): NO se toca
 * `Mesa.idZona` ni el contrato `Ronda.mesaId`. [posX]/[posY]/[ancho]/[alto] en el
 * canvas de la sala (mismas unidades que `Mesa.posX/posY`).
 */
@Serializable
@Entity(tableName = "zonas")
data class Zona(
    @PrimaryKey val id: String,
    val salaId: String,
    val nombre: String,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val ancho: Float = 0f,
    val alto: Float = 0f,
    val color: ZonaColor = ZonaColor.AZUL,
    val camareroId: String? = null,
)

/** Producto del catálogo canónico del nodo. La categoría deriva el destino. */
@Serializable
@Entity(tableName = "productos")
data class Producto(
    @PrimaryKey val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double = 0.0,
    val disponible: Boolean = true,
    /** Subfamilia visual dentro de la categoría (p. ej. «Zero», «Light»); null = sin subfamilia. */
    val subfamilia: String? = null,
    /** El producto admite nota libre en la línea (p. ej. «sin cebolla»). */
    val permiteNota: Boolean = false,
    /** Copy público del plato (web/carta Identity); null = no se pinta. Máx. 800. */
    val descripcion: String? = null,
)

/** Línea de una ronda/ticket: producto + cantidad. */
@Serializable
data class Linea(
    val productoId: String,
    val nombreProducto: String,
    val cantidad: Int,
    val estado: LineaEstado = LineaEstado.PENDIENTE,
    /** Nota libre del camarero (snapshot; solo si el producto la permite). */
    val nota: String? = null,
    /** Modificadores elegidos (snapshot en claro: grupo/opción/delta). */
    val modificadores: List<ModificadorLinea> = emptyList(),
)

/**
 * Modificador elegido en una línea (snapshot para historial y expo). Es el
 * espejo en claro de `ModificadorRondaLan` del contrato Commander: grupo/opción
 * van como nombres (no ids) y [delta] es el delta de precio de la opción.
 * [grupoId]/[opcionId] son **internos** de Bar (resueltos al recibir la ronda
 * por nombre): no viajan por la red y se serializan solo si ≠ "".
 */
@Serializable
data class ModificadorLinea(
    val grupo: String = "",
    val opcion: String = "",
    val delta: Double = 0.0,
    val grupoId: String = "",
    val opcionId: String = "",
)

/**
 * Grupo de modificadores de carta (p. ej. «Punto», «Extras»). Es la fuente del
 * contrato LAN `gruposModificador`; los modificadores son locales del nodo por
 * ahora (no sincronizan con Identity).
 */
@Serializable
@Entity(tableName = "grupos_modificador")
data class GrupoModificador(
    @PrimaryKey val id: String,
    val nombre: String,
    val multiple: Boolean = false,
    val obligatorio: Boolean = false,
)

/** Opción de un [GrupoModificador] (p. ej. «Al punto», con delta y alias de voz). */
@Serializable
@Entity(tableName = "opciones_modificador")
data class OpcionModificador(
    @PrimaryKey val id: String,
    val grupoId: String,
    val nombre: String,
    val deltaPrecio: Double = 0.0,
    val alias: String = "",
)

/** Asignación de un [GrupoModificador] a un [Producto] (N:M, tabla puente). */
@Serializable
@Entity(tableName = "producto_grupo", primaryKeys = ["productoId", "grupoId"])
data class ProductoGrupo(
    val productoId: String,
    val grupoId: String,
)

/**
 * Ticket de expo: una ronda partida por destino. Preparado/recogido es por ticket
 * (no por mesa): las cañas pueden estar preparadas sin la pizza. [preparadoPor]
 * es el camarero (lista blanca) que elaboró el ticket, simétrico a `Ronda.camarero`.
 * Las líneas se persisten como JSON (TypeConverter).
 */
@Serializable
@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey val id: String,
    val rondaId: String,
    val destino: Destino,
    val estado: TicketEstado = TicketEstado.PENDIENTE,
    val preparadoPor: String? = null,
    /**
     * Id de cola visible/hablable por destino en el turno («Cola 1 Bebida»).
     * Monótono: al recoger Cola 1, Cola 2 no pasa a 1 (ancla estable para voz/tacto).
     */
    val numeroCola: Int = 0,
    val lineas: List<Linea>,
)

/**
 * Ronda enviada por Commander: la unidad que Bar parte en tickets BARRA/COCINA.
 * Las líneas se persisten como JSON (TypeConverter).
 */
@Serializable
@Entity(tableName = "rondas")
data class Ronda(
    @PrimaryKey val id: String,
    val mesaId: String,          // idZona ("T3")
    val numero: Int,             // número de ronda de la mesa
    val camarero: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val lineas: List<Linea>,
)

/** Reserva (hold comercial) sobre una mesa. [mesaId] referencia [Mesa.id] local. */
@Serializable
@Entity(tableName = "reservas")
data class Reserva(
    @PrimaryKey val id: String,
    val mesaId: String,
    val nombre: String,
    val paraEpoch: Long? = null,
    val creadaEn: Long = System.currentTimeMillis(),
    val canceladaEn: Long? = null,
)

/** Estado de una invitación por email (mirror local; la aceptación vive en Identity Web). */
@Serializable
enum class InvitacionEstado { PENDIENTE, ACEPTADA, REVOCADA, RECHAZADA, EXPIRADA }

/**
 * Estado desde el catálogo de Identity (`pendiente|aceptada|revocada|rechazada|expirada`);
 * null si el valor es desconocido (respuestas de versiones futuras).
 */
fun invitacionEstadoDesdeApi(valor: String?): InvitacionEstado? = when (valor) {
    "pendiente" -> InvitacionEstado.PENDIENTE
    "aceptada" -> InvitacionEstado.ACEPTADA
    "revocada" -> InvitacionEstado.REVOCADA
    "rechazada" -> InvitacionEstado.RECHAZADA
    "expirada" -> InvitacionEstado.EXPIRADA
    else -> null
}

/**
 * Invitación por email creada desde Bar (registro local; la verdad vive en Identity).
 * La aceptación ocurre en Identity Web (magic-link), fuera de Bar.
 */
@Serializable
@Entity(tableName = "invitaciones")
data class Invitacion(
    @PrimaryKey val id: String,
    val email: String,
    val rol: String = "staff",
    val estado: InvitacionEstado = InvitacionEstado.PENDIENTE,
    val expiraEn: String? = null,
    val creadaEn: Long = System.currentTimeMillis(),
)

/** Configuración de la conexión con Identity (persistida en Room, tabla singleton). */
@Entity(tableName = "identity_config")
data class IdentityConfig(
    @PrimaryKey val id: String = "local",
    val conectado: Boolean = false,
    val baseUrl: String? = null,
    val establecimientoUuid: String? = null,
    val error: String? = null,
)

/** Tipo de establecimiento (categoría del negocio), pedido en el registro. Catálogo 1:1 con Identity. */
@Serializable
enum class TipoEstablecimiento { BAR, RESTAURANTE, CAFETERIA, PUB, COPAS }

/** Valor canónico del catálogo de Identity para un tipo (snake_case, sin tildes). */
fun TipoEstablecimiento.apiValor(): String = when (this) {
    TipoEstablecimiento.BAR -> "bar"
    TipoEstablecimiento.RESTAURANTE -> "restaurante"
    TipoEstablecimiento.CAFETERIA -> "cafeteria"
    TipoEstablecimiento.PUB -> "pub"
    TipoEstablecimiento.COPAS -> "copas"
}

/** Tipo desde el valor del catálogo de Identity; null si no coincide. */
fun tipoDesdeApi(valor: String?): TipoEstablecimiento? = when (valor) {
    "bar" -> TipoEstablecimiento.BAR
    "restaurante" -> TipoEstablecimiento.RESTAURANTE
    "cafeteria" -> TipoEstablecimiento.CAFETERIA
    "pub" -> TipoEstablecimiento.PUB
    "copas" -> TipoEstablecimiento.COPAS
    else -> null
}

/**
 * Sesión de la cuenta de negocio en el puesto de Bar (tabla singleton).
 * Se persiste solo si el usuario marcó «Recuérdame» en el login; si no,
 * vive en memoria y se pierde al reiniciar. [tipo] y [logoUrl] se
 * sincronizan contra Identity (fuente canónica del perfil de negocio).
 */
@Entity(tableName = "sesion_negocio")
data class SesionNegocio(
    @PrimaryKey val id: String = "local",
    val token: String? = null,
    val email: String? = null,
    val nombreMostrar: String? = null,
    val establecimientoUuid: String? = null,
    val tipo: TipoEstablecimiento? = null,
    val logoUrl: String? = null,
    /** Procedencia canónica de Identity (`real|test|demo`) para diagnóstico offline. */
    val dataOrigin: String? = null,
    /**
     * Hasta cuándo es válida la sesión local (epoch ms) para operar offline.
     * Se renueva (+7 días) en cada contacto exitoso con el VPS (login o
     * revalidación). `null` = sin validez conocida (sesiones previas a v10:
     * no operan offline hasta el primer contacto); `0` = inválida (401 del
     * server: revocada/borrada). Migración Room v10.
     */
    val validaHasta: Long? = null,
)

/**
 * Clave pública Ed25519 de Identity para verificar QRs `phid1` offline.
 * Tabla singleton (PK fija «local»); se refresca al conectar a Identity.
 * [publicKey] es la clave base64url (32 bytes) que devuelve `GET /v1/keys/qr`.
 */
@Entity(tableName = "qr_keys")
data class QrKey(
    @PrimaryKey val id: String = "local",
    val keyId: String = "",
    val publicKey: String = "",
    val algorithm: String = "Ed25519",
)

/**
 * Alta de camarero hecha offline (verificada localmente) pendiente de subir a
 * Identity (membresia) cuando vuelva la conexión. [payload] es el QR `phid1`
 * completo para reenviar a `POST /miembros/qr`.
 */
@Entity(tableName = "altas_pendientes")
data class AltaPendiente(
    @PrimaryKey val camareroId: String,
    val payload: String,
    val creadaEn: Long = System.currentTimeMillis(),
)

/** Rol de un camarero en el establecimiento. */
@Serializable
enum class RolCamarero { DUENO, STAFF }

/** Estado de un camarero en la lista blanca. */
@Serializable
enum class CamareroEstado { ACTIVA, REVOCADA }

/**
 * Intervalo de jornada local (libro de oficio, lado Bar).
 *
 * El productor abre la jornada al conceder sesión de trabajo ([BarRepository.iniciarSesion])
 * y la cierra al cortar (fin de jornada, timeout o revocación). Es **local**: el libro
 * canónico vive en Identity (camareros `:8080`); Bar lo guarda para no perder el
 * intervalo si se reinicia y para poder proyectarlo luego.
 */
@Entity(tableName = "jornadas")
data class JornadaLocal(
    @PrimaryKey val id: String,
    val camareroId: String,
    val inicio: Long,
    val fin: Long? = null,
)

/**
 * Intervalo de jornada expuesto por el resumen (LAN/vista): camarero + inicio/fin.
 * [fin] null = jornada abierta (se cuenta hasta el instante actual).
 */
@Serializable
data class JornadaIntervalo(
    val camareroId: String,
    val inicio: Long,
    val fin: Long? = null,
)

/** Horas trabajadas y mesas distintas servidas por un camarero en un periodo. */
@Serializable
data class ResumenCamarero(
    val camareroId: String,
    val horasMs: Long,
    val mesasDistintas: Int,
)

/** Resumen de jornadas para un periodo (GET LAN `/v1/sesion/jornadas` y vista del puesto). */
@Serializable
data class JornadasResumen(
    val intervalos: List<JornadaIntervalo>,
    val porCamarero: List<ResumenCamarero>,
)

/**
 * Evento de servicio pendiente de subir a Identity (cola persistente del libro de oficio).
 *
 * [eventoId] es la PK y la clave de idempotencia del server (`servicio:{rondaId}`):
 * reintentar no duplica. El proyector drena la cola cuando la cuenta de negocio está
 * vinculada; éxito borra la fila, fallo la deja para el siguiente intento.
 */
@Entity(tableName = "servicios_pendientes")
data class ServicioPendiente(
    @PrimaryKey val eventoId: String,
    val camareroId: String,
    val tipo: String,
    val cantidad: Int = 1,
    val creadaEn: Long = System.currentTimeMillis(),
)

/**
 * Horario del establecimiento (apertura/cierre por día de la semana). Tabla local
 * del puesto (Room v11): funciona offline, como layout y carta. La fuente canónica
 * vivirá en Identity negocio `:8082` (ítem cross del Server) cuando la web pública
 * lo requiera; entonces Bar publicará este local best-effort.
 * [diaSemana]: 1 = lunes … 7 = domingo (ISO-8601). [abre]/[cierra] en formato
 * `HH:mm` (24 h); ambos null = día cerrado.
 */
@Entity(tableName = "horario_local")
data class HorarioLocal(
    @PrimaryKey val diaSemana: Int,
    val abre: String? = null,
    val cierra: String? = null,
) {
    /** true si el día está abierto con horario coherente (`abre` y `cierra` presentes). */
    val abierto: Boolean get() = abre != null && cierra != null
}

/**
 * Camarero de la lista blanca del establecimiento (mirror de Identity).
 * La identidad canónica vive en Identity; Bar guarda a quién acepta en la LAN.
 * [id] es el `camarero_id` (UUID) de Identity, extraído del QR `phid1`.
 */
@Serializable
@Entity(tableName = "camareros")
data class Camarero(
    @PrimaryKey val id: String,
    val nombre: String? = null,
    val email: String? = null,
    val rol: RolCamarero = RolCamarero.STAFF,
    val estado: CamareroEstado = CamareroEstado.ACTIVA,
    val credencialId: String? = null,
    val altaEn: Long = System.currentTimeMillis(),
    /**
     * De servicio en el puesto de barra (varios a la vez; la cuenta se crea en
     * Commander, Bar solo la asigna al establecimiento). Migración Room v3.
     */
    val deServicio: Boolean = false,
    /**
     * Sesión de trabajo activa: Bar concedió la jornada (POST /v1/sesion/iniciar)
     * y aún no la cortó (fin de jornada, revocación o salida de la LAN por
     * heartbeat). Solo con esto true el camarero puede mandar comandas.
     * Distinto de [deServicio] (preparador en el puesto). Migración Room v8.
     */
    val sesionActiva: Boolean = false,
)

/**
 * Operación de catálogo pendiente de subir a Identity (outbox del sync de carta).
 * [operationId] es la PK y la clave de idempotencia del server (`POST /sync/operaciones`):
 * reintentar no duplica. El proyector drena la cola al reconectar; éxito borra la fila.
 * Los campos de payload son null cuando [action] = `archivar`.
 */
@Entity(tableName = "operaciones_catalogo")
data class OperacionCatalogo(
    @PrimaryKey val operationId: String,
    val aggregateId: String,
    val action: String,          // crear|actualizar|archivar
    val baseRevision: Int = 0,
    val nombre: String? = null,
    val categoria: String? = null,
    val destino: String? = null, // barra|cocina
    val precioCentimos: Int? = null,
    val moneda: String = "EUR",
    val disponible: Boolean = true,
    val descripcion: String? = null,
    val creadaEn: Long = System.currentTimeMillis(),
)

/**
 * Revisión canónica por producto (espejo del sync). Fuente del `base_revision` de las
 * operaciones `actualizar`/`archivar`; se actualiza con `result_snapshot.revision` de la
 * respuesta del server. Sin fila = nunca sincronizado (base_revision 0).
 */
@Entity(tableName = "producto_sync")
data class ProductoSync(
    @PrimaryKey val aggregateId: String,
    val revision: Int,
    val actualizadaEn: Long = System.currentTimeMillis(),
)

/**
 * Cursor del pull de deltas del sync de carta: última revisión global del
 * establecimiento vista por Bar (`GET /sync/cambios?desde=N`). Tabla singleton
 * (PK fija «local»). La revisión global del server es distinta de la revisión por
 * producto (`producto_sync.revision`): aquella avanza por operación aplicada.
 */
@Entity(tableName = "catalogo_sync_estado")
data class CatalogoSyncEstado(
    @PrimaryKey val id: String = "local",
    val desdeRevision: Int = 0,
)

/**
 * Estado del pull del inbox CFC (tabla de una fila): el cursor del último `seq`
 * procesado por establecimiento. Se persiste para que el poller reanude donde
 * se quedó tras reiniciar el puesto (sin re-traer el inbox completo).
 */
@Serializable
@Entity(tableName = "cfc_estado")
data class CfcEstado(
    @PrimaryKey val id: String = "local",
    val cursor: Int = 0,
)
