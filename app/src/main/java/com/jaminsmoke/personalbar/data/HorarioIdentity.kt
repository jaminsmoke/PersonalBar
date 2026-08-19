package com.jaminsmoke.personalbar.data

import com.jaminsmoke.personalbar.lan.IdentityHorarioDia
import com.jaminsmoke.personalbar.lan.IdentityTurnoHorario

/**
 * Mapeo del horario local (ISO 1 = lunes … 7 = domingo, un turno abre/cierra)
 * al contrato Identity (0 = lunes … 6 = domingo, N turnos).
 *
 * La UI de Bar edita el primer turno; los extras del remoto se conservan y se
 * reenvían en el PATCH para no pisarlos.
 */
fun isoADiaIdentity(diaIso: Int): Int = diaIso - 1

fun diaIdentityAIso(diaIdentity: Int): Int = diaIdentity + 1

data class HorarioConExtras(
    val locales: List<HorarioLocal>,
    val extras: Map<Int, List<IdentityTurnoHorario>>,
)

fun horarioRemotoALocal(dias: List<IdentityHorarioDia>): HorarioConExtras {
    val extras = mutableMapOf<Int, List<IdentityTurnoHorario>>()
    val locales = (1..7).map { iso ->
        val remoto = dias.find { it.diaSemana == isoADiaIdentity(iso) }
        if (remoto == null || remoto.cerrado || remoto.turnos.isEmpty()) {
            HorarioLocal(diaSemana = iso, abre = null, cierra = null)
        } else {
            val primero = remoto.turnos.first()
            val resto = remoto.turnos.drop(1)
            if (resto.isNotEmpty()) extras[iso] = resto
            HorarioLocal(diaSemana = iso, abre = primero.abre, cierra = primero.cierra)
        }
    }
    return HorarioConExtras(locales, extras)
}

fun horarioLocalARemoto(
    locales: List<HorarioLocal>,
    extras: Map<Int, List<IdentityTurnoHorario>>,
): List<IdentityHorarioDia> = (1..7).map { iso ->
    val local = locales.find { it.diaSemana == iso }
    if (local?.abierto == true) {
        val primero = IdentityTurnoHorario(abre = local.abre!!, cierra = local.cierra!!)
        IdentityHorarioDia(
            diaSemana = isoADiaIdentity(iso),
            cerrado = false,
            turnos = listOf(primero) + extras[iso].orEmpty(),
        )
    } else {
        IdentityHorarioDia(
            diaSemana = isoADiaIdentity(iso),
            cerrado = true,
            turnos = emptyList(),
        )
    }
}
