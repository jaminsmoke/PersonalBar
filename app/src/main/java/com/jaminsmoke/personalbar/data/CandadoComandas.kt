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
 * Candado de `POST /v1/rondas`.
 *
 * La ronda v1 lleva el camarero como nombre (`Ronda.camarero`), no como id, así
 * que el candado empareja por nombre normalizado contra la lista blanca:
 * - Si el camarero **está** en la lista blanca (contratado), exige sesión activa:
 *   un `ACTIVA` sin jornada, o un `REVOCADA`, no puede comandar → false.
 * - Si **no** está en la lista blanca, no bloquea aquí: la admisión de membresía
 *   la gestiona `POST /v1/sesion` (QR + firma); Commander ya cierra la jornada
 *   localmente antes de mandar y ante el 403.
 *
 * Es un backstop server-side: la defensa primaria es el gate local de Commander
 * (`sesionTrabajo`) y la auto-inactivación por heartbeat/SSE.
 */
object CandadoComandas {

    fun admitida(ronda: Ronda, camareros: List<Camarero>): Boolean {
        val nombre = normalizarNombreCamarero(ronda.camarero)
        if (nombre.isEmpty()) return true // ronda sin camarero: nada que comprobar

        val coinciden = camareros.filter { normalizarNombreCamarero(it.nombre) == nombre }
        if (coinciden.isEmpty()) return true // fuera de la lista blanca: no bloquea aquí

        return coinciden.any { it.estado == CamareroEstado.ACTIVA && it.sesionActiva }
    }
}
