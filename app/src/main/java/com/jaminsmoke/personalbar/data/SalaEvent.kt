package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/**
 * Evento de sala emitido por el repositorio y re-enviado a Commander por SSE.
 * `tipo` es el nombre del evento SSE (`ticket.listo` / `ticket.servido`).
 */
@Serializable
data class SalaEvent(
    val tipo: String,
    val ticketId: String,
) {
    companion object {
        const val TIPO_LISTO = "ticket.listo"
        const val TIPO_SERVIDO = "ticket.servido"

        fun listo(ticketId: String) = SalaEvent(TIPO_LISTO, ticketId)
        fun servido(ticketId: String) = SalaEvent(TIPO_SERVIDO, ticketId)
    }
}
