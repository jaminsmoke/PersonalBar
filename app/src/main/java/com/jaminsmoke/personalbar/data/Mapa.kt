package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/** Forma de mesa. [capacidadDefecto] se usa al crear; los módulos derivan el ancho. */
@Serializable
enum class MesaForma(val capacidadDefecto: Int) {
    REDONDA(2),
    CUADRADA(4),
    RECTANGULAR(8),
    RECTANGULAR_XL(12),
}

/** Estado operativo de mesa. En Bar se deriva de las rondas/tickets (no hay ciclo de comanda). */
@Serializable
enum class MesaEstado { LIBRE, OCUPADA, EN_COCINA }

/** Estado visual de mesa (board/lista). Combina operativo + hold comercial. */
@Serializable
enum class MesaVisualStatus { LIBRE, OCUPADA, EN_COCINA, RESERVADA, BLOQUEADA }

// ── Grid del board (misma técnica que Commander) ─────────────────────────────
const val CELL_F = 40f
const val CARD_W = 120f
const val ZONA_ANCHO = 2000f
const val ZONA_ALTO = 2600f
const val MIN_BOARD_SCALE = 0.08f
const val MAX_BOARD_SCALE = 3f

/** Número de módulos cuadrados (120dp) que ocupa cada forma. */
fun mesaModulos(forma: MesaForma): Int = when (forma) {
    MesaForma.REDONDA, MesaForma.CUADRADA -> 1
    MesaForma.RECTANGULAR -> 2
    MesaForma.RECTANGULAR_XL -> 3
}

fun esRectangular(forma: MesaForma): Boolean =
    forma == MesaForma.RECTANGULAR || forma == MesaForma.RECTANGULAR_XL

/** Dimensiones reales (ancho, alto) en dp de una mesa considerando el giro. */
fun mesaDims(forma: MesaForma, girada: Boolean): Pair<Float, Float> {
    val largo = CARD_W * mesaModulos(forma)
    return if (girada && esRectangular(forma)) CARD_W to largo else largo to CARD_W
}

/** Radio de esquinas (dp) según forma. REDONDA = círculo. */
fun mesaShapeRadius(forma: MesaForma): Float = when (forma) {
    MesaForma.REDONDA -> 999f
    MesaForma.CUADRADA -> 16f
    MesaForma.RECTANGULAR -> 14f
    MesaForma.RECTANGULAR_XL -> 12f
}

/** Emoji de pestaña por nombre de sala (misma semántica que Commander). */
fun zonaEmoji(nombreSala: String): String = when {
    nombreSala.contains("Terraza", ignoreCase = true) -> "\uD83C\uDF1E"
    nombreSala.contains("Interior", ignoreCase = true) ||
        nombreSala.contains("Salón", ignoreCase = true) ||
        nombreSala.contains("Salon", ignoreCase = true) -> "\uD83C\uDFE0"
    nombreSala.contains("Barra", ignoreCase = true) ||
        nombreSala.contains("Bar", ignoreCase = true) -> "\uD83C\uDF78"
    nombreSala.contains("VIP", ignoreCase = true) ||
        nombreSala.contains("Reservado", ignoreCase = true) -> "\u2B50"
    else -> "\uD83D\uDCCD"
}

/** Prioridad: ocupada/en-cocina > bloqueada > reservada > libre (misma que Commander). */
fun mesaVisualStatus(estado: MesaEstado, bloqueada: Boolean, reservada: Boolean): MesaVisualStatus =
    when (estado) {
        MesaEstado.OCUPADA -> MesaVisualStatus.OCUPADA
        MesaEstado.EN_COCINA -> MesaVisualStatus.EN_COCINA
        MesaEstado.LIBRE -> when {
            bloqueada -> MesaVisualStatus.BLOQUEADA
            reservada -> MesaVisualStatus.RESERVADA
            else -> MesaVisualStatus.LIBRE
        }
    }

/**
 * Deriva el estado visual de cada mesa en Bar a partir de sus colas abiertas.
 * - Ticket COCINA pendiente → EN_COCINA.
 * - Cualquier otro ticket abierto (BARRA) → OCUPADA.
 * - Sin tickets abiertos → LIBRE (+ bloqueo/reserva si aplica).
 * Devuelve un map `mesa.id → MesaVisualStatus`.
 */
fun derivarEstadoMesas(
    mesas: List<Mesa>,
    salas: List<Sala>,
    rondas: List<Ronda>,
    bebida: List<Ticket>,
    comida: List<Ticket>,
    reservas: List<Reserva>,
): Map<String, MesaVisualStatus> {
    val salaPorId = salas.associateBy { it.id }
    val reservaActivaPorMesa = reservas.filter { it.canceladaEn == null }.associateBy { it.mesaId }
    val rondaPorId = rondas.associateBy { it.id }
    val abiertos = bebida + comida
    val enCocina = abiertos.filter { it.destino == Destino.COCINA }
        .mapNotNull { rondaPorId[it.rondaId]?.mesaId }
        .toSet()
    val ocupada = abiertos.mapNotNull { rondaPorId[it.rondaId]?.mesaId }.toSet()

    return mesas.associate { mesa ->
        val nombreSala = salaPorId[mesa.salaId]?.nombre.orEmpty()
        val idZona = mesa.idZona(nombreSala)
        val estado = when {
            idZona in enCocina -> MesaEstado.EN_COCINA
            idZona in ocupada -> MesaEstado.OCUPADA
            else -> MesaEstado.LIBRE
        }
        mesa.id to mesaVisualStatus(estado, mesa.bloqueada, reservaActivaPorMesa.containsKey(mesa.id))
    }
}
