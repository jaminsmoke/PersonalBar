package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.CandadoComandas
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.Producto
import com.jaminsmoke.personalbar.data.QrParser
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.SalaEvent
import com.jaminsmoke.personalbar.data.JornadasResumen
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.Zona
import com.jaminsmoke.personalbar.data.convertirLayout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.intercept
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Json compartido entre ContentNegotiation y los eventos SSE. */
val LanJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Cuerpo de `POST /v1/tickets/{id}/preparado`: quién lo preparó (cuenta de camarero). */
@Serializable
data class PreparadoRequest(
    @SerialName("preparado_por") val preparadoPor: String,
)

/** Respuesta de `POST /v1/sesion/iniciar`: jornada concedida + token de sesión LAN (v0.2). */
@Serializable
data class SesionIniciarResponse(
    @SerialName("sesionActiva") val sesionActiva: Boolean,
    /**
     * Credencial de sesión firmada (HMAC, `phbar1.*`). Las rutas privadas
     * la exigen en `Authorization: Bearer`; SSE en `?token=`. Aditivo para
     * Commander: un nodo 0.1 no lo emite y el cliente lo ignora.
     */
    val token: String? = null,
)

/** Cuerpo de `POST /v1/heartbeat`: latido del Commander con su id de Identity. */
@Serializable
data class HeartbeatRequest(
    @SerialName("camareroId") val camareroId: String = "",
)

/** Estado completo del nodo para `GET /v1/estado` (polling de Commander). */
@Serializable
data class EstadoResponse(
    val version: String,
    val establecimiento: Establecimiento,
    val salas: List<Sala>,
    val bebida: List<Ticket>,
    val comida: List<Ticket>,
    val servidos: List<Ticket>,
    val mesas: List<Mesa>,
    /** Zonas del layout; campo aditivo para clientes LAN antiguos. */
    val zonas: List<Zona> = emptyList(),
)/** Carta/catálogo canónico para `GET /v1/carta` (Commander espeja ids de producto). */
@Serializable
data class CartaResponse(
    /**
     * Esquema del contrato de carta. 2 = ids UUID (migración v11→v12). 1 era
     * ids slug (legacy): Commander reconstruye su espejo al detectar el cambio
     * (re-apunta `codigoBar` por nombre sin borrar líneas históricas).
     */
    val schema: Int = CARTA_SCHEMA,
    val productos: List<ProductoCarta>,
    val gruposModificador: List<GrupoModificadorCarta> = emptyList(),
) {
    companion object {
        const val CARTA_SCHEMA: Int = 2
    }
}

/** Producto del contrato `GET /v1/carta` (con los grupos de modificadores asignados). */
@Serializable
data class ProductoCarta(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val disponible: Boolean,
    val subfamilia: String? = null,
    val permiteNota: Boolean = false,
    val grupos: List<String> = emptyList(),
)

/** Grupo de modificadores del contrato `GET /v1/carta` (espejo de Commander `GrupoModificadorLan`). */
@Serializable
data class GrupoModificadorCarta(
    val id: String,
    val nombre: String,
    val multiple: Boolean = false,
    val obligatorio: Boolean = false,
    val opciones: List<OpcionModificadorCarta> = emptyList(),
)

/** Opción de un grupo de modificadores (espejo de Commander `OpcionModificadorLan`). */
@Serializable
data class OpcionModificadorCarta(
    val id: String,
    val nombre: String,
    val deltaPrecio: Double = 0.0,
    val alias: String = "",
)


/** Resumen de jornadas para `GET /v1/sesion/jornadas` (Commander pinta el panel). */
@Serializable
data class JornadasResponse(
    val resumen: JornadasResumen,
)

/** Principal autenticado: el camarero derivado del token de sesión LAN. */
data class CamareroSesion(val camareroId: String) : Principal

/**
 * Módulo Ktor del nodo de sala: /health, contrato /v1 y SSE /v1/eventos.
 *
 * Auth v0.2 ([secretoSesion] HMAC): `POST /v1/sesion/iniciar` emite un token
 * firmado y todas las rutas privadas lo exigen en `Authorization: Bearer`;
 * SSE lo exige en `?token=` (EventSource no envía headers). Si [secretoSesion]
 * está vacío, la auth queda deshabilitada y toda ruta privada responde 401.
 */
fun Application.barModule(repository: BarRepository, secretoSesion: String = "") {
    install(ContentNegotiation) { json(LanJson) }
    install(SSE)
    install(Authentication) {
        bearer("sesion-lan") {
            authenticate { credencial ->
                val camareroId = NodoSesion.verificar(credencial.token, secretoSesion)
                camareroId?.let { CamareroSesion(it) }
            }
        }
    }

    routing {
        get("/health") {
            call.respondText(
                HealthPayload.json(
                    establecimiento = repository.establecimiento.value.nombre,
                    establecimientoId = repository.identityConfig.value.establecimientoUuid,
                ),
                ContentType.Application.Json,
            )
        }

        post("/v1/sesion") {
            val qr = runCatching { call.receive<SesionRequest>().qr }
                .getOrNull()?.trim().orEmpty()
            when (val resultado = SesionConsulta.evaluar(
                qr = qr,
                camareros = repository.camareros.value,
                qrKey = repository.qrKey.value,
            )) {
                is SesionConsulta.Resultado.QrInvalido ->
                    call.respond(HttpStatusCode.BadRequest)
                is SesionConsulta.Resultado.Ok ->
                    call.respond(HttpStatusCode.OK, resultado.respuesta)
            }
        }

        post("/v1/sesion/iniciar") {
            val qr = runCatching { call.receive<SesionRequest>().qr }
                .getOrNull()?.trim().orEmpty()
            val phid = QrParser.parsear(qr)
            when {
                phid == null -> call.respond(HttpStatusCode.BadRequest)
                repository.iniciarSesion(phid.camareroId) -> {
                    val token = NodoSesion.emitir(phid.camareroId, secretoSesion)
                    call.respond(
                        HttpStatusCode.OK,
                        SesionIniciarResponse(sesionActiva = true, token = token),
                    )
                }
                else -> call.respond(HttpStatusCode.Forbidden)
            }
        }

        post("/v1/sesion/cortar") {
            // v0.2: el camarero puede cortar con el token (Bearer) o con el QR (compat).
            // Esta ruta es pública a propósito (Commander viejo corta con QR sin token):
            // el Bearer se verifica manualmente en lugar de usar el plugin de auth.
            val camareroAutenticado = call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")
                ?.trim()
                ?.let { NodoSesion.verificar(it, secretoSesion) }
            val phid = runCatching { call.receive<SesionRequest>().qr }
                .getOrNull()?.trim()?.let { QrParser.parsear(it) }
            val camareroId = camareroAutenticado ?: phid?.camareroId
            when {
                camareroId == null -> call.respond(HttpStatusCode.BadRequest)
                repository.cortarSesion(camareroId) -> call.respond(HttpStatusCode.OK)
                else -> call.respond(HttpStatusCode.NotFound)
            }
        }

        authenticate("sesion-lan") {

            post("/v1/heartbeat") {
                // v0.2: el camarero sale del token, nunca del body (no suplantable).
                val camareroId = call.principal<CamareroSesion>()?.camareroId
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                when {
                    repository.registrarHeartbeat(camareroId) -> call.respond(HttpStatusCode.OK)
                    else -> call.respond(HttpStatusCode.Forbidden)
                }
            }

            post("/v1/rondas") {
                val camareroId = call.principal<CamareroSesion>()?.camareroId
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val ronda = call.receive<Ronda>()
                if (!CandadoComandas.admitida(ronda, repository.camareros.value, camareroId)) {
                    call.respond(HttpStatusCode.Forbidden)
                } else {
                    val creada = repository.crearRonda(ronda)
                    val tickets = (repository.bebidaQueue.value + repository.comidaQueue.value)
                        .filter { it.rondaId == ronda.id }
                    val status = if (creada) HttpStatusCode.Created else HttpStatusCode.OK
                    call.respond(status, tickets)
                }
            }

            post("/v1/tickets/{id}/preparado") {
                val camareroId = call.principal<CamareroSesion>()?.camareroId
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"]
                if (id.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val preparadoPor = runCatching { call.receive<PreparadoRequest>().preparadoPor }
                        .getOrNull()?.trim().orEmpty()
                    when {
                        preparadoPor.isEmpty() -> call.respond(HttpStatusCode.BadRequest)
                        // v0.2: solo el camarero autenticado puede marcar su propio trabajo.
                        preparadoPor != repository.camareros.value
                            .firstOrNull { it.id == camareroId }?.nombre
                            -> call.respond(HttpStatusCode.Forbidden)
                        repository.marcarPreparado(id, preparadoPor) -> call.respond(HttpStatusCode.OK)
                        else -> call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            post("/v1/tickets/{id}/recogido") {
                val id = call.parameters["id"]
                when {
                    id.isNullOrBlank() -> call.respond(HttpStatusCode.BadRequest)
                    repository.marcarRecogido(id) -> call.respond(HttpStatusCode.OK)
                    else -> call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/v1/estado") {
                // El layout canónico vive en el canvas horizontal de Bar; al exportar se
                // convierten las posiciones al canvas 2000×2600 de Commander (escala
                // uniforme + centrado). El repositorio conserva las posiciones canónicas.
                val mesas = repository.mesas.value
                val convertidas = convertirLayout(mesas)
                val zonas = repository.zonas.value
                val zonaConvertidas = convertirRectangulos(zonas)
                call.respond(
                    EstadoResponse(
                        version = BarLanConfig.VERSION,
                        establecimiento = repository.establecimiento.value,
                        salas = repository.salas.value,
                        bebida = repository.bebidaQueue.value,
                        comida = repository.comidaQueue.value,
                        servidos = repository.servidos.value,
                        mesas = mesas.map { m ->
                            convertidas[m.id]?.let { (x, y) -> m.copy(posX = x, posY = y) } ?: m
                        },
                        zonas = zonas.map { z ->
                            zonaConvertidas[z.id] ?: z
                        },
                    )
                )
            }

            get("/v1/carta") {
                val asignaciones = repository.productoGrupo.value
                val grupos = repository.gruposModificador.value
                val opciones = repository.opcionesModificador.value
                call.respond(
                    CartaResponse(
                        productos = repository.catalogo.value.map { p ->
                            ProductoCarta(
                                id = p.id,
                                nombre = p.nombre,
                                categoria = p.categoria,
                                precio = p.precio,
                                disponible = p.disponible,
                                subfamilia = p.subfamilia,
                                permiteNota = p.permiteNota,
                                grupos = asignaciones.filter { it.productoId == p.id }.map { it.grupoId },
                            )
                        },
                        gruposModificador = grupos.map { g ->
                            GrupoModificadorCarta(
                                id = g.id,
                                nombre = g.nombre,
                                multiple = g.multiple,
                                obligatorio = g.obligatorio,
                                opciones = opciones.filter { it.grupoId == g.id }.map { o ->
                                    OpcionModificadorCarta(
                                        id = o.id,
                                        nombre = o.nombre,
                                        deltaPrecio = o.deltaPrecio,
                                        alias = o.alias,
                                    )
                                },
                            )
                        },
                    )
                )
            }

            get("/v1/sesion/jornadas") {
                // Historial de jornadas + resumen por camarero (horas y mesas distintas
                // servidas) del periodo. `desde`/`hasta` son epoch ms opcionales.
                val desde = call.request.queryParameters["desde"]?.toLongOrNull()
                val hasta = call.request.queryParameters["hasta"]?.toLongOrNull()
                call.respond(JornadasResponse(resumen = repository.resumenJornadas(desde, hasta)))
            }
        }

        route("/v1/eventos") {
            // EventSource no puede enviar headers → el token va en query param.
            // Se valida ANTES del handler sse (que ya inicia la respuesta SSE).
            intercept(ApplicationCallPipeline.Call) {
                val token = call.request.queryParameters["token"].orEmpty()
                if (NodoSesion.verificar(token, secretoSesion) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    finish()
                }
            }
            sse {
                repository.eventos.collect { evento ->
                    send(
                        ServerSentEvent(
                            data = LanJson.encodeToString(evento),
                            event = evento.tipo,
                        )
                    )
                }
            }
        }
    }
}

/**
 * Convierte los rectángulos de zonas al canvas de Commander usando la misma
 * transformación uniforme que las mesas.
 */
private fun convertirRectangulos(zonas: List<Zona>): Map<String, Zona> {
    if (zonas.isEmpty()) return emptyMap()
    val puntos = zonas.flatMap { z ->
        listOf(
            Mesa(id = "${z.id}:origen", salaId = z.salaId, indiceZona = 0, posX = z.posX, posY = z.posY, forma = MesaForma.CUADRADA),
            Mesa(id = "${z.id}:fin", salaId = z.salaId, indiceZona = 0, posX = z.posX + z.ancho, posY = z.posY + z.alto, forma = MesaForma.CUADRADA),
        )
    }
    val convertidos = convertirLayout(puntos)
    return zonas.associate { z ->
        val origen = convertidos["${z.id}:origen"]
        val fin = convertidos["${z.id}:fin"]
        z.id to if (origen != null && fin != null) {
            z.copy(posX = origen.first, posY = origen.second, ancho = fin.first - origen.first, alto = fin.second - origen.second)
        } else z
    }
}
