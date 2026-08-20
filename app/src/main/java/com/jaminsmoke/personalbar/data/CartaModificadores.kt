package com.jaminsmoke.personalbar.data

import java.text.NumberFormat
import java.util.Locale

/**
 * Lógica canónica de modificadores de carta (espejo de `CartaModificadores` de
 * Commander, adaptado a ids `String` UUID de Bar y a [ModificadorLinea] en memoria).
 * Funciones puras, sin Android: testeables en JVM.
 */

/** Grupo con sus opciones (proyección para hoja/editor y validación). */
data class GrupoConOpciones(
    val grupo: GrupoModificador,
    val opciones: List<OpcionModificador>,
)

/** Borrador de una opción para el alta/edición inline de un grupo (id vacío = nueva). */
data class OpcionModificadorBorrador(
    val id: String = "",
    val nombre: String,
    val deltaPrecio: Double = 0.0,
    val alias: String = "",
)

object CartaModificadores {

    /** Precio unitario de una línea: base + suma de deltas de los modificadores elegidos. */
    fun precioUnitario(base: Double, elegidos: List<ModificadorLinea>): Double =
        base + elegidos.sumOf { it.delta }

    /** Texto de una línea: opciones elegidas + nota, unidas por « · ». */
    fun textoLinea(elegidos: List<ModificadorLinea>, nota: String?): String {
        val partes = elegidos.map { it.opcion.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val n = nota?.trim().orEmpty()
        if (n.isNotEmpty()) partes.add(n)
        return partes.joinToString(" · ")
    }

    /** Grupos (con opciones) asignados a un producto. */
    fun gruposDeProducto(
        productoId: String,
        grupos: List<GrupoModificador>,
        opciones: List<OpcionModificador>,
        asignaciones: List<ProductoGrupo>,
    ): List<GrupoConOpciones> {
        val ids = asignaciones.filter { it.productoId == productoId }.map { it.grupoId }.toSet()
        return grupos.filter { it.id in ids }.map { g ->
            GrupoConOpciones(g, opciones.filter { it.grupoId == g.id })
        }
    }

    /** true si algún grupo obligatorio no tiene ninguna opción elegida. */
    fun faltanObligatorios(
        grupos: List<GrupoConOpciones>,
        elegidos: List<ModificadorLinea>,
    ): Boolean {
        val porGrupo = elegidos.groupBy { it.grupoId.ifBlank { it.grupo } }
        return grupos.any { gc ->
            gc.grupo.obligatorio && porGrupo[gc.grupo.id].isNullOrEmpty()
        }
    }

    /** Tokens de voz de una opción: nombre + alias separados por `|` (normalizados). */
    fun tokensOpcion(opcion: OpcionModificador): List<List<String>> {
        val nombres = buildList {
            add(opcion.nombre)
            opcion.alias.split('|').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        }
        return nombres
            .map { normalizarNombreCamarero(it).split(" ").filter { t -> t.isNotEmpty() } }
            .filter { it.isNotEmpty() }
    }

    /** Snapshot canónico: ordenado por grupo/opción (estable para historial). */
    fun snapshotOrdenado(elegidos: List<ModificadorLinea>): List<ModificadorLinea> =
        elegidos.sortedWith(
            compareBy({ it.grupoId.ifBlank { it.grupo } }, { it.opcionId.ifBlank { it.opcion } }),
        )

    /** Agrupa productos consecutivos por subfamilia (el orden de carta ya viene agrupado). */
    fun agruparPorSubfamilia(productos: List<Producto>): List<Pair<String?, List<Producto>>> {
        if (productos.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<String?, MutableList<Producto>>>()
        for (p in productos) {
            val clave = p.subfamilia?.trim()?.takeIf { it.isNotEmpty() }
            val ultimo = out.lastOrNull()
            if (ultimo != null && ultimo.first == clave) ultimo.second.add(p)
            else out.add(clave to mutableListOf(p))
        }
        return out.map { it.first to it.second.toList() }
    }

    /** Delta formateado como moneda (p. ej. «0,50 €»); el signo `+` lo añade el caller. */
    fun formatoDelta(delta: Double): String =
        NumberFormat.getCurrencyInstance(Locale.getDefault()).format(delta)
}
