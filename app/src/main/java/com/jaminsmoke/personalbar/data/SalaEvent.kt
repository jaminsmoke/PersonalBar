package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/**
 * Evento de sala emitido por el repositorio y re-enviado a Commander por SSE.
 * `tipo` es el nombre del evento SSE (`ticket.preparado` / `ticket.recogido`);
 * [preparadoPor] viaja en el evento de preparado (quién lo elaboró).
 */
@Serializable
data class SalaEvent(
    val tipo: String,
    val ticketId: String,
    val preparadoPor: String? = null,
) {
    companion object {
        const val TIPO_PREPARADO = "ticket.preparado"
        const val TIPO_RECOGIDO = "ticket.recogido"

        fun preparado(ticketId: String, preparadoPor: String) = SalaEvent(TIPO_PREPARADO, ticketId, preparadoPor)
        fun recogido(ticketId: String) = SalaEvent(TIPO_RECOGIDO, ticketId)
    }
}
