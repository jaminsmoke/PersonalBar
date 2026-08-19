package com.jaminsmoke.personalbar.data

import kotlinx.serialization.Serializable

/**
 * Destino de un ticket en la expo. Barra = bebida, Cocina = comida.
 * Cuando exista Kitchen, COCINA migra allí; Bar se queda BARRA.
 */
@Serializable
enum class Destino { BARRA, COCINA }

/**
 * Estado de un ticket en la cola: PENDIENTE → PREPARADO → RECOGIDO (sale de la cola).
 * «Preparado» registra quién lo preparó; «Recogido» lo saca de la cola (lo recogió el
 * camarero de barra). El cierre del ciclo (SERVIDO y ronda finalizada) vive en Commander.
 */
@Serializable
enum class TicketEstado { PENDIENTE, PREPARADO, RECOGIDO }

/** Estado de una línea dentro de un ticket. */
@Serializable
enum class LineaEstado { PENDIENTE, SERVIDA }

/**
 * Deriva el destino de una línea a partir de la categoría del producto.
 * Regla v0.1: categorías de bebida → BARRA; categorías de comida → COCINA;
 * categoría vacía/desconocida → BARRA (defecto seguro en barra).
 */
fun destinoDesdeCategoria(categoria: String): Destino {
    val c = categoria.trim().lowercase()
    return when {
        c.contains("bebida") || c.contains("barra") || c.contains("refresco") ||
            c.contains("cerveza") || c.contains("caña") || c.contains("vino") ||
            c.contains("cafe") || c.contains("café") || c.contains("copa") ||
            c.contains("zumo") || c.contains("agua") -> Destino.BARRA
        c.contains("comida") || c.contains("cocina") || c.contains("pizza") ||
            c.contains("entrante") || c.contains("principal") || c.contains("postre") ||
            c.contains("tapa") || c.contains("bocadillo") || c.contains("croqueta") ||
            c.contains("tostada") -> Destino.COCINA
        else -> Destino.BARRA
    }
}

/** Destino del sync (`barra`/`cocina`) derivado de la categoría, para el payload de `POST /sync/operaciones`. */
fun destinoSyncDesdeCategoria(categoria: String): String = when (destinoDesdeCategoria(categoria)) {
    Destino.BARRA -> "barra"
    Destino.COCINA -> "cocina"
}

/**
 * Producto canónico remoto (snapshot/delta de Identity) para aplicar al mirror
 * local. [precio] en euros (el server devuelve `precio_centimos`); [revision]
 * es la revisión canónica del producto (para `base_revision` y conflictos).
 */
data class ProductoRemoto(
    val id: String,
    val nombre: String,
    val categoria: String,
    val precio: Double,
    val disponible: Boolean,
    val revision: Int,
)

/** Cambio de catálogo (delta) del server. [producto] null = archivado (tombstone). */
data class CambioRemoto(
    val aggregateId: String,
    val action: String,          // crear|actualizar|archivar
    val producto: ProductoRemoto?,
)

/**
 * Conflicto de catálogo pendiente de decisión (`GET /sync/conflictos`).
 * [canonical] null = el producto aún no existe en el server; [proposed] null =
 * archivado (tombstone). Ambos son `ProductoRemoto` para pintar el diff en la UI.
 */
data class ConflictoRemoto(
    val id: String,
    val operationId: String,
    val aggregateId: String,
    val action: String,             // crear|actualizar|archivar
    val baseRevision: Int,
    val canonicalRevision: Int,
    val canonical: ProductoRemoto?,
    val proposed: ProductoRemoto?,
    val estado: String,
    val clientCreatedAt: String,
)

/**
 * Notificación de la bandeja durable del negocio (`GET /notificaciones`).
 * [conflictoId] y [deepLink] provienen del `payload` (solo `conflicto_sync` en v0.1).
 */
data class NotificacionRemoto(
    val id: String,
    val conflictoId: String?,
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val deepLink: String?,
    val leida: Boolean,
)

/**
 * Prefijo corto de zona para IDs tipo B1, T1, I1… Reutiliza la semántica de Commander
 * para que la misma mesa se reconozca igual en ambos nodos.
 */
fun zonaPrefijo(zona: String): String = when {
    zona.contains("Bar", ignoreCase = true) -> "B"
    zona.contains("Terraza", ignoreCase = true) -> "T"
    zona.contains("Interior", ignoreCase = true) || zona.contains("Salon", ignoreCase = true) ||
        zona.contains("Salón", ignoreCase = true) -> "I"
    zona.contains("VIP", ignoreCase = true) || zona.contains("Reservado", ignoreCase = true) -> "V"
    zona.isBlank() -> "M"
    else -> zona.trim().firstOrNull()?.uppercase() ?: "M"
}
