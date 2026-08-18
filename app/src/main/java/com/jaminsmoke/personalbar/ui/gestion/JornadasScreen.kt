package com.jaminsmoke.personalbar.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.ResumenCamarero
import com.jaminsmoke.personalbar.data.JornadasResumen
import com.jaminsmoke.personalbar.ui.ExpoViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Vista de jornadas de los camareros de servicio (solo lectura): horas trabajadas
 * y mesas distintas servidas hoy, más los intervalos del día. Lee del repositorio
 * del nodo (Room v9 `jornadas` + resumen del puesto); la fuente canónica del
 * historial vive en Identity vía el productor de oficio.
 */
@Composable
fun JornadasScreen(
    viewModel: ExpoViewModel = viewModel(),
) {
    val resumen = remember(viewModel) { viewModel.resumenJornadasHoy() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.jornadas_titulo),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        if (resumen.porCamarero.isEmpty()) {
            Text(
                text = stringResource(R.string.jornadas_vacio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(resumen.porCamarero, key = { it.camareroId }) { r ->
                    PbResumenCamarero(r)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }
                items(resumen.intervalos, key = { "${it.camareroId}:${it.inicio}" }) { intervalo ->
                    Text(
                        text = intervaloTexto(intervalo.camareroId, intervalo.inicio, intervalo.fin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PbResumenCamarero(resumen: ResumenCamarero) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = resumen.camareroId,
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
private fun intervaloTexto(camareroId: String, inicio: Long, fin: Long?): String {
    val config = LocalConfiguration.current
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT, config.locales[0])
    val inicioTxt = fmt.format(Date(inicio))
    val finTxt = fin?.let { fmt.format(Date(it)) } ?: stringResource(R.string.jornadas_abierta)
    return "$camareroId · $inicioTxt – $finTxt"
}

/** Horas en formato `Hh Mm` (p. ej. «2h 30m»); menos de un minuto → «0h 0m». */
private fun horasLegibles(horasMs: Long): String {
    val totalMin = horasMs / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return "${h}h ${m}m"
}
