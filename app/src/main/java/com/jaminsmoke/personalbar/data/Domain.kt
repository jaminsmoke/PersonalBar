package com.jaminsmoke.personalbar.data

import java.text.Normalizer
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

/**
 * Slug estable para ids de producto: minúsculas, acentos fuera, no-alfanuméricos
 * a guion. «Caña» → `cana`; «Tostada con tomate» → `tostada-con-tomate`.
 * Devuelve cadena vacía si no queda nada (p. ej. solo símbolos).
 */
fun slugProducto(nombre: String): String {
    val nfd = Normalizer.normalize(nombre.trim().lowercase(), Normalizer.Form.NFD)
    val ascii = nfd.replace(Regex("\\p{M}+"), "")
    return ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
