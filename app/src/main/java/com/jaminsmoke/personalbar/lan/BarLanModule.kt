package com.jaminsmoke.personalbar.lan

import com.jaminsmoke.personalbar.data.BarRepository
import com.jaminsmoke.personalbar.data.Establecimiento
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.SalaEvent
import com.jaminsmoke.personalbar.data.Ticket
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Json compartido entre ContentNegotiation y los eventos SSE. */
val LanJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

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
)

/** Módulo Ktor del nodo de sala: /health, contrato /v1 y SSE /v1/eventos. */
fun Application.barModule(repository: BarRepository) {
    install(ContentNegotiation) { json(LanJson) }
    install(SSE)

    routing {
        get("/health") {
            call.respondText(
                HealthPayload.json(establecimiento = repository.establecimiento.value.nombre),
                ContentType.Application.Json,
            )
        }

        post("/v1/rondas") {
            val ronda = call.receive<Ronda>()
            val creada = repository.crearRonda(ronda)
            val tickets = (repository.bebidaQueue.value + repository.comidaQueue.value)
                .filter { it.rondaId == ronda.id }
            val status = if (creada) HttpStatusCode.Created else HttpStatusCode.OK
            call.respond(status, tickets)
        }

        post("/v1/tickets/{id}/listo") {
            val id = call.parameters["id"]
            when {
                id.isNullOrBlank() -> call.respond(HttpStatusCode.BadRequest)
                repository.marcarListo(id) -> call.respond(HttpStatusCode.OK)
                else -> call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/v1/tickets/{id}/servido") {
            val id = call.parameters["id"]
            when {
                id.isNullOrBlank() -> call.respond(HttpStatusCode.BadRequest)
                repository.marcarServido(id) -> call.respond(HttpStatusCode.OK)
                else -> call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/v1/estado") {
            call.respond(
                EstadoResponse(
                    version = BarLanConfig.VERSION,
                    establecimiento = repository.establecimiento.value,
                    salas = repository.salas.value,
                    bebida = repository.bebidaQueue.value,
                    comida = repository.comidaQueue.value,
                    servidos = repository.servidos.value,
                    mesas = repository.mesas.value,
                )
            )
        }

        sse("/v1/eventos") {
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
