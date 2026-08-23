package com.jaminsmoke.personalbar.data

import java.text.Normalizer

/**
 * Normaliza un nombre para comparación de candado: minúsculas, acentos fuera
 * y espacios colapsados. «Lucía García» → «lucia garcia».
 */
fun normalizarNombreCamarero(nombre: String?): String =
    Normalizer.normalize(nombre?.trim().orEmpty(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Candado de `POST /v1/rondas` (auth v0.2).
 *
 * La ronda v1 lleva el camarero como nombre (`Ronda.camarero`), no como id, así
 * que el candado empareja por nombre normalizado contra la lista blanca, y exige
 * que coincida con el camarero autenticado por el token de sesión:
 * - `camareroIdAutenticado` es obligatorio (la ruta ya es privada, pero el
 *   candado nunca debe poder ser true sin él).
 * - La ronda debe llevar un camarero cuyo nombre normalizado coincida con el
 *   del camarero autenticado (suplantación bloqueada: nadie comanda por otro).
 * - El camarero debe estar en la lista blanca, `ACTIVA` y con sesión activa.
 *
 * Es un backstop server-side: la defensa primaria es el gate local de Commander
 * (`sesionTrabajo`) y la auto-inactivación por heartbeat/SSE.
 */
object CandadoComandas {

    fun admitida(ronda: Ronda, camareros: List<Camarero>, camareroIdAutenticado: String): Boolean {
        val autenticado = camareros.firstOrNull { it.id == camareroIdAutenticado }
            ?: return false
        // El camarero de la ronda debe ser el mismo que el autenticado.
        val nombreRonda = normalizarNombreCamarero(ronda.camarero)
        val nombreAutenticado = normalizarNombreCamarero(autenticado.nombre)
        if (nombreRonda.isEmpty() || nombreRonda != nombreAutenticado) return false
        return autenticado.estado == CamareroEstado.ACTIVA && autenticado.sesionActiva
    }
}
