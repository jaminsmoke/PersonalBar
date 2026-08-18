package com.jaminsmoke.personalbar.data

/**
 * Estado de la sesión de negocio del puesto, derivado de [SesionNegocio.validaHasta].
 * Es la fuente que consume el gate de la raíz (UI) y cualquier cliente que necesite
 * saber si la cuenta está operativa:
 *
 * - [SIN_SESION]: no hay cuenta de negocio (ni en memoria ni persistida).
 * - [VALIDA]: hay sesión y `ahora <= validaHasta` → se puede operar (online y offline).
 * - [CADUCADA]: hay sesión pero `ahora > validaHasta` → exige login online para renovar.
 * - [INVALIDA]: sesión sin validez conocida (`validaHasta == null`, sesiones previas a
 *   v10) o revocada por el server (`validaHasta == 0`, 401 en la revalidación) → sin
 *   acceso al puesto hasta que un login online la renueve.
 */
enum class SesionEstado { SIN_SESION, VALIDA, CADUCADA, INVALIDA }

/**
 * Derivación pura del estado de sesión a partir de la sesión guardada y el instante
 * actual. Testeable sin Room ni red.
 *
 * - token nulo → [SesionEstado.SIN_SESION]
 * - `validaHasta == null` → [SesionEstado.INVALIDA] (sin validez offline conocida)
 * - `validaHasta == 0` → [SesionEstado.INVALIDA] (401 del server: revocada/borrada)
 * - `ahora <= validaHasta` → [SesionEstado.VALIDA]
 * - `ahora > validaHasta` → [SesionEstado.CADUCADA]
 */
fun sesionEstadoDe(sesion: SesionNegocio?, ahora: Long): SesionEstado {
    if (sesion?.token == null) return SesionEstado.SIN_SESION
    val validaHasta = sesion.validaHasta ?: return SesionEstado.INVALIDA
    if (validaHasta <= 0L) return SesionEstado.INVALIDA
    return if (ahora <= validaHasta) SesionEstado.VALIDA else SesionEstado.CADUCADA
}
