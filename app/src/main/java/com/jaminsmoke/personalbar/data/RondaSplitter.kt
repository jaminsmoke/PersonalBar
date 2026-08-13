package com.jaminsmoke.personalbar.data

/**
 * Parte una ronda en tickets por destino (máx. 2: BARRA y COCINA).
 * Puro: no muta nada y no depende de Android.
 */
object RondaSplitter {

    /**
     * @param ronda ronda recibida
     * @param catalogo map productoId → Producto (para derivar categoría/destino)
     * @return tickets por destino presentes en la ronda, en orden BARRA, COCINA
     */
    fun split(ronda: Ronda, catalogo: Map<String, Producto>): List<Ticket> {
        val lineasPorDestino = ronda.lineas.groupBy { linea ->
            catalogo[linea.productoId]
                ?.let { destinoDesdeCategoria(it.categoria) }
                ?: Destino.BARRA
        }
        return Destino.entries.mapNotNull { destino ->
            val lineas = lineasPorDestino[destino].orEmpty()
            if (lineas.isEmpty()) {
                null
            } else {
                Ticket(
                    id = "${ronda.id}-${destino.name.lowercase()}",
                    rondaId = ronda.id,
                    destino = destino,
                    lineas = lineas,
                )
            }
        }
    }
}
