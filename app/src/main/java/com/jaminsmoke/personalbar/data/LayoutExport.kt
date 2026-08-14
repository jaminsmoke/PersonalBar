package com.jaminsmoke.personalbar.data

/**
 * Conversión de exportación del layout de Bar al canvas de Commander.
 *
 * Bar es la fuente de verdad del layout y usa su canvas horizontal
 * (`ZONA_ANCHO×ZONA_ALTO` = 2600×2000). Commander renderiza las `posX/posY`
 * tal cual en su propio canvas vertical (2000×2600). Al exportar (`/v1/estado`)
 * se mapea el canvas completo de Bar sobre el de Commander con **escala
 * uniforme + centrado**, de forma que el bloque de mesas sale igual de ordenado
 * y con las mismas proporciones, adaptado a las medidas del board de Commander.
 * Commander no cambia.
 *
 * Solo se convierten posiciones: el tamaño físico de las mesas (CARD_W) es el
 * mismo en ambas apps. El espaciado mínimo del grid (CELL_F) garantiza que la
 * escala uniforme (≤ 1 aquí) no produce solapes.
 */
fun convertirLayout(
    mesas: List<Mesa>,
    canvasAncho: Float = ZONA_ANCHO_COMANDER,
    canvasAlto: Float = ZONA_ALTO_COMANDER,
): Map<String, Pair<Float, Float>> {
    if (mesas.isEmpty()) return emptyMap()
    val escala = minOf(canvasAncho / ZONA_ANCHO, canvasAlto / ZONA_ALTO)
    val offsetX = (canvasAncho - ZONA_ANCHO * escala) / 2f
    val offsetY = (canvasAlto - ZONA_ALTO * escala) / 2f
    return mesas.associate { mesa ->
        mesa.id to ((mesa.posX * escala + offsetX) to (mesa.posY * escala + offsetY))
    }
}
