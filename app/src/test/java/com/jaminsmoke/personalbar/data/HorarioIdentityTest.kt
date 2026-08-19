package com.jaminsmoke.personalbar.data

import com.jaminsmoke.personalbar.lan.IdentityHorarioDia
import com.jaminsmoke.personalbar.lan.IdentityPerfilWeb
import com.jaminsmoke.personalbar.lan.IdentityPerfilWebUpdate
import com.jaminsmoke.personalbar.lan.IdentityTurnoHorario
import com.jaminsmoke.personalbar.lan.LanJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorarioIdentityTest {

    @Test
    fun isoYIdentitySonInversos() {
        for (iso in 1..7) {
            assertEquals(iso, diaIdentityAIso(isoADiaIdentity(iso)))
        }
        assertEquals(0, isoADiaIdentity(1))
        assertEquals(6, isoADiaIdentity(7))
    }

    @Test
    fun remotoConTurnosExtraSeConservaAlRoundTrip() {
        val remoto = listOf(
            IdentityHorarioDia(
                diaSemana = 0,
                cerrado = false,
                turnos = listOf(
                    IdentityTurnoHorario("10:00", "14:00"),
                    IdentityTurnoHorario("18:00", "23:00"),
                ),
            ),
            IdentityHorarioDia(diaSemana = 1, cerrado = true, turnos = emptyList()),
        )
        val mapeado = horarioRemotoALocal(remoto)
        val lunes = mapeado.locales.find { it.diaSemana == 1 }!!
        assertEquals("10:00", lunes.abre)
        assertEquals("14:00", lunes.cierra)
        assertEquals(listOf(IdentityTurnoHorario("18:00", "23:00")), mapeado.extras[1])

        val otraVez = horarioLocalARemoto(mapeado.locales, mapeado.extras)
        val lunRemoto = otraVez.find { it.diaSemana == 0 }!!
        assertFalse(lunRemoto.cerrado)
        assertEquals(2, lunRemoto.turnos.size)
        assertEquals("18:00", lunRemoto.turnos[1].abre)
        val mar = otraVez.find { it.diaSemana == 1 }!!
        assertTrue(mar.cerrado)
        assertTrue(mar.turnos.isEmpty())
    }

    @Test
    fun perfilWebUpdateOmiteNullsYSerializaVacios() {
        val soloColor = LanJson.encodeToString(IdentityPerfilWebUpdate(colorPrimario = "#fbbc00"))
        assertTrue(soloColor.contains("color_primario"))
        assertFalse(soloColor.contains("eslogan"))
        val vacio = LanJson.encodeToString(IdentityPerfilWebUpdate(eslogan = ""))
        assertTrue(vacio.contains("\"eslogan\":\"\""))
    }

    @Test
    fun perfilWebDecodificaContratoIdentity() {
        val json = """
            {"establecimiento_id":"e-1","eslogan":"Cañas Test","descripcion":"Bar de barrio",
             "direccion":"Calle Test 1","ciudad":"Madrid","telefono":"600000000",
             "email_contacto":"barTest@x.es","web":null,"redes":{"instagram":"@barTest"},
             "tz":"Europe/Madrid","plantilla":"estate_hospitality","color_primario":"#fbbc00",
             "web_publica":true,"mostrar_equipo":false,"hero_url":"/v1/establecimientos/e-1/hero"}
        """.trimIndent()
        val perfil = LanJson.decodeFromString<IdentityPerfilWeb>(json)
        assertEquals("Cañas Test", perfil.eslogan)
        assertEquals("@barTest", perfil.redes["instagram"])
        assertEquals("#fbbc00", perfil.colorPrimario)
        assertTrue(perfil.webPublica)
        assertFalse(perfil.mostrarEquipo)
    }
}
