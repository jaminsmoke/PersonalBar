package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/**
 * Evento de sala emitido por el repositorio y re-enviado a Commander por SSE.
 * `tipo` es el nombre del evento SSE (`ticket.preparado` / `ticket.recogido`).
 *
 * v1 (autodescriptivo): además de `ticketId` y `preparadoPor`, lleva
 * [mesaId] (idZona de red, p. ej. "T3"), [camarero] (quién pidió la ronda),
 * un [resumen] legible de las líneas y el [ticket] completo (ronda, destino,
 * estado, cola, líneas). Los campos nuevos son opcionales (default) para que
 * un Commander antiguo decodifique eventos nuevos y uno nuevo decodifique
 * eventos antiguos (backward-compatible).
 */
@Serializable
data class SalaEvent(
    val version: Int = VERSION,
    val tipo: String,
    val ticketId: String,
    val preparadoPor: String? = null,
    val mesaId: String? = null,
    val camarero: String? = null,
    val resumen: String = "",
    val ticket: Ticket? = null,
) {
    companion object {
        const val VERSION = 1
        const val TIPO_PREPARADO = "ticket.preparado"
        const val TIPO_RECOGIDO = "ticket.recogido"

        fun preparado(ticket: Ticket, mesaId: String?, camarero: String?) = SalaEvent(
            tipo = TIPO_PREPARADO,
            ticketId = ticket.id,
            preparadoPor = ticket.preparadoPor,
            mesaId = mesaId,
            camarero = camarero,
            resumen = resumenLineas(ticket.lineas),
            ticket = ticket,
        )

        fun recogido(ticket: Ticket, mesaId: String?, camarero: String?) = SalaEvent(
            tipo = TIPO_RECOGIDO,
            ticketId = ticket.id,
            preparadoPor = ticket.preparadoPor,
            mesaId = mesaId,
            camarero = camarero,
            resumen = resumenLineas(ticket.lineas),
            ticket = ticket,
        )
    }
}

/** Resumen legible de las líneas de un ticket: «2× Caña, 1× Croquetas». */
fun resumenLineas(lineas: List<Linea>): String =
    lineas.joinToString(", ") { "${it.cantidad}× ${it.nombreProducto}" }
