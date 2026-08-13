package com.jaminsmoke.personalbar.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Matemática pura del board (sin Compose ni Android). Portada de `MesasBoard.kt`
 * de Commander para compartir exactamente la misma técnica de grid/cámara/colisión.
 * Las posiciones usan `Pair<Float, Float>` = (x, y); la UI lo convierte a dp/Offset.
 */

/** Escala necesaria para encajar por completo el grid, conservando un margen visible. */
fun calcularEscalaAjuste(
    viewportW: Float,
    viewportH: Float,
    contentW: Float,
    contentH: Float,
    padding: Float = 0f,
): Float {
    if (viewportW <= 0f || viewportH <= 0f || contentW <= 0f || contentH <= 0f) return 1f
    val availableW = (viewportW - padding * 2f).coerceAtLeast(1f)
    val availableH = (viewportH - padding * 2f).coerceAtLeast(1f)
    return minOf(availableW / contentW, availableH / contentH)
        .coerceIn(MIN_BOARD_SCALE, MAX_BOARD_SCALE)
}

/** Limita el pan a los bordes del grid; si cabe en el viewport lo centra. */
fun limitarPan(
    pan: Float,
    viewport: Float,
    content: Float,
    edgeMargin: Float = 0f,
): Float {
    if (viewport <= 0f || content <= 0f) return 0f
    return if (content <= viewport) {
        (viewport - content) / 2f
    } else {
        pan.coerceIn(viewport - content - edgeMargin, edgeMargin)
    }
}

/** Pan que conserva bajo los dedos el mismo punto del board al hacer zoom. */
fun panTrasZoom(pan: Float, focoAnterior: Float, focoActual: Float, ratio: Float): Float =
    focoActual - (focoAnterior - pan) * ratio

/** Detecta si dos rectángulos (x,y,w,h) se solapan (AABB collision). */
fun colisionan(
    x1: Float, y1: Float, w1: Float, h1: Float,
    x2: Float, y2: Float, w2: Float, h2: Float,
): Boolean = x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2

/**
 * Busca la posición libre más cercana con búsqueda en espiral. Comprueba colisión
 * de bounding boxes completos y respeta los límites del grid de la zona.
 */
fun findNearestFreeCell(
    targetX: Float,
    targetY: Float,
    draggedW: Float,
    draggedH: Float,
    occupied: List<List<Float>>,
    limiteX: Float = ZONA_ANCHO,
    limiteY: Float = ZONA_ALTO,
): Pair<Float, Float> {
    val maxX = maxOf(CELL_F, floor((limiteX - draggedW - CELL_F) / CELL_F) * CELL_F)
    val maxY = maxOf(CELL_F, floor((limiteY - draggedH - CELL_F) / CELL_F) * CELL_F)
    val safeX = ((targetX / CELL_F).roundToInt() * CELL_F).coerceIn(CELL_F, maxX)
    val safeY = ((targetY / CELL_F).roundToInt() * CELL_F).coerceIn(CELL_F, maxY)

    fun hayColision(x: Float, y: Float): Boolean = occupied.any { o ->
        colisionan(x, y, draggedW, draggedH, o[0], o[1], o[2], o[3])
    }

    if (!hayColision(safeX, safeY)) return safeX to safeY

    var ring = 1
    while (ring < 50) {
        for (dx in -ring..ring) {
            for (dy in -ring..ring) {
                if (maxOf(abs(dx), abs(dy)) != ring) continue
                val cx = safeX + dx * CELL_F
                val cy = safeY + dy * CELL_F
                if (cx in CELL_F..maxX && cy in CELL_F..maxY && !hayColision(cx, cy)) {
                    return cx to cy
                }
            }
        }
        ring++
    }
    return safeX to safeY
}

/** True si el rectángulo (x,y,w,h) se sale de los límites del grid de la zona. */
fun estaFueraDeLimites(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    limiteX: Float = ZONA_ANCHO,
    limiteY: Float = ZONA_ALTO,
): Boolean = x < CELL_F || y < CELL_F || x + w > limiteX - CELL_F || y + h > limiteY - CELL_F

/** Clamp duro: devuelve la posición alineada al grid más cercana DENTRO de los límites. */
fun clampAlBorde(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    limiteX: Float = ZONA_ANCHO,
    limiteY: Float = ZONA_ALTO,
): Pair<Float, Float> {
    val maxX = maxOf(CELL_F, floor((limiteX - w - CELL_F) / CELL_F) * CELL_F)
    val maxY = maxOf(CELL_F, floor((limiteY - h - CELL_F) / CELL_F) * CELL_F)
    val cX = ((x / CELL_F).roundToInt() * CELL_F).coerceIn(CELL_F, maxX)
    val cY = ((y / CELL_F).roundToInt() * CELL_F).coerceIn(CELL_F, maxY)
    return cX to cY
}

/** Repara posiciones legacy fuera del grid y solapes, conservando todas las mesas. */
fun normalizarMesasEnGrid(mesas: List<Mesa>): Map<String, Pair<Float, Float>> {
    val posiciones = linkedMapOf<String, Pair<Float, Float>>()
    val ocupadas = mutableListOf<List<Float>>()

    mesas.sortedWith(compareBy<Mesa> { it.indiceZona }.thenBy { it.id }).forEach { mesa ->
        val (w, h) = mesaDims(mesa.forma, mesa.girada)
        val (x, y) = findNearestFreeCell(mesa.posX, mesa.posY, w, h, ocupadas)
        posiciones[mesa.id] = x to y
        ocupadas += listOf(x, y, w, h)
    }
    return posiciones
}

/** Detecta si una mesa está demasiado lejos del cluster (Manhattan > 500dp). */
fun isIsolated(x: Float, y: Float, draggedId: String, allMesas: List<Mesa>): Boolean {
    val others = allMesas.filter { it.id != draggedId }
    if (others.isEmpty()) return false
    return others.minOf { abs(x - it.posX) + abs(y - it.posY) } > 500f
}

/** Posición segura cerca del cluster (borde inferior-derecho de las demás mesas). */
fun traerCerca(
    allMesas: List<Mesa>,
    draggedW: Float = CARD_W,
    draggedH: Float = CARD_W,
): Pair<Float, Float> {
    if (allMesas.isEmpty()) return CELL_F to CELL_F
    val maxX = allMesas.maxOf {
        val (w, _) = mesaDims(it.forma, it.girada)
        it.posX + w
    } + CELL_F
    val avgY = allMesas.map { it.posY }.average().toFloat()
    val targetX = (maxX / CELL_F).roundToInt() * CELL_F
    val targetY = (avgY / CELL_F).roundToInt() * CELL_F
    val ocupadas = allMesas.map {
        val (ow, oh) = mesaDims(it.forma, it.girada)
        listOf(it.posX, it.posY, ow, oh)
    }
    return findNearestFreeCell(targetX, targetY, draggedW, draggedH, ocupadas)
}
