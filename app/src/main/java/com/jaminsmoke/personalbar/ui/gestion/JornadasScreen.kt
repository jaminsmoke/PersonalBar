package com.jaminsmoke.personalbar.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.ResumenCamarero
import com.jaminsmoke.personalbar.data.JornadasResumen
import java.text.DateFormat
import java.util.Date

/**
 * Sección «Jornadas de hoy» (solo lectura): horas trabajadas y mesas distintas
 * servidas hoy por cada camarero de servicio, más los intervalos del día.
 *
 * Vive dentro de CamarerosScreen (absorbida desde el hub de Gestión). [camareros]
 * se usa para resolver los nombres: [ResumenCamarero] y los intervalos solo llevan
 * `camareroId`; sin este cruce la vista pintaría IDs crudos. La fuente canónica del
 * historial vive en Identity vía el productor de oficio.
 */
@Composable
fun PbJornadasSeccion(
    resumen: JornadasResumen,
    camareros: List<Camarero>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (resumen.porCamarero.isEmpty()) {
            Text(
                text = stringResource(R.string.jornadas_vacio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                resumen.porCamarero.forEach { r ->
                    PbResumenCamarero(r, nombreCamarero(camareros, r.camareroId))
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                resumen.intervalos.forEach { intervalo ->
                    Text(
                        text = intervaloTexto(
                            nombreCamarero(camareros, intervalo.camareroId),
                            intervalo.inicio,
                            intervalo.fin,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Nombre visible de un camarero: `Camarero.nombre` si existe; si no, el prefijo
 * corto del id (`id.take(8)`, mismo criterio que CamarerosScreen). Si el id no
 * está en la lista, se cae al prefijo del id recibido.
 */
internal fun nombreCamarero(camareros: List<Camarero>, camareroId: String): String =
    camareros.firstOrNull { it.id == camareroId }?.nombre ?: camareroId.take(8)

@Composable
private fun PbResumenCamarero(resumen: ResumenCamarero, nombre: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.jornadas_mesas)}: ${resumen.mesasDistintas}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${stringResource(R.string.jornadas_horas)}: ${horasLegibles(resumen.horasMs)}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** `HH:mm` del instante o etiqueta «en curso» si `fin` es null. */
@Composable
private fun intervaloTexto(nombre: String, inicio: Long, fin: Long?): String {
    val config = LocalConfiguration.current
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT, config.locales[0])
    val inicioTxt = fmt.format(Date(inicio))
    val finTxt = fin?.let { fmt.format(Date(it)) } ?: stringResource(R.string.jornadas_abierta)
    return "$nombre · $inicioTxt – $finTxt"
}

/** Horas en formato `Hh Mm` (p. ej. «2h 30m»); menos de un minuto → «0h 0m». */
private fun horasLegibles(horasMs: Long): String {
    val totalMin = horasMs / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return "${h}h ${m}m"
}
