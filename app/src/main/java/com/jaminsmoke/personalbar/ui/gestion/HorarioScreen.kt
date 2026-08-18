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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.PersonalBarApp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.HorarioLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado editable del horario: por día (1 = lun … 7 = dom), abierto y horas HH:mm. */
data class DiaHorario(
    val dia: Int,
    var abierto: Boolean,
    var abre: String,
    var cierra: String,
)

/** Edición del horario del establecimiento: carga de Room, valida y persiste local. */
class HorarioViewModel : ViewModel() {
    private val repository = PersonalBarApp.get().repository

    private val _dias = MutableStateFlow<List<DiaHorario>>(emptyList())
    val dias: StateFlow<List<DiaHorario>> = _dias.asStateFlow()

    private val _guardado = MutableStateFlow(false)
    val guardado: StateFlow<Boolean> = _guardado.asStateFlow()

    /** Mensaje de validación (null = sin error). */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    init {
        val actual = repository.horario.value.associateBy { it.diaSemana }
        _dias.value = (1..7).map { dia ->
            val diaBd = actual[dia]
            DiaHorario(
                dia = dia,
                abierto = diaBd?.abierto == true,
                abre = diaBd?.abre ?: "10:00",
                cierra = diaBd?.cierra ?: "22:00",
            )
        }
    }

    fun setAbierto(dia: Int, abierto: Boolean) {
        _guardado.value = false
        _error.value = null
        _dias.value = _dias.value.map { if (it.dia == dia) it.copy(abierto = abierto) else it }
    }

    fun setHora(dia: Int, esAbre: Boolean, hora: String) {
        _guardado.value = false
        _error.value = null
        _dias.value = _dias.value.map {
            if (it.dia != dia) it
            else if (esAbre) it.copy(abre = hora) else it.copy(cierra = hora)
        }
    }

    /** Valida (`abre < cierra` en los días abiertos) y persiste en Room. */
    fun guardar() {
        val diasAbiertos = _dias.value.filter { it.abierto }
        val invalido = diasAbiertos.any { dia ->
            val a = dia.abre.toMinuto()
            val c = dia.cierra.toMinuto()
            a == null || c == null || a >= c
        }
        if (invalido) {
            _error.value = R.string.horario_invalido
            return
        }
        val horario = _dias.value.mapNotNull { dia ->
            if (dia.abierto) {
                val abre = dia.abre.padStart(5, '0')
                val cierra = dia.cierra.padStart(5, '0')
                HorarioLocal(diaSemana = dia.dia, abre = abre, cierra = cierra)
            } else {
                HorarioLocal(diaSemana = dia.dia, abre = null, cierra = null)
            }
        }
        repository.guardarHorario(horario)
        _guardado.value = true
    }
}

/** `HH:mm` → minutos desde medianoche; null si el formato no es válido. */
private fun String.toMinuto(): Int? {
    val partes = split(":")
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Editor de horario del establecimiento (local, Room v11; sin sync a Identity aún). */
@Composable
fun HorarioScreen(
    viewModel: HorarioViewModel = viewModel(),
) {
    val dias by viewModel.dias.collectAsState()
    val guardado by viewModel.guardado.collectAsState()
    val error by viewModel.error.collectAsState()
    val nombresDia = stringArrayResource(R.array.dias_semana_corto)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.horario_titulo),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dias, key = { it.dia }) { dia ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = nombresDia.getOrElse(dia.dia - 1) { "D${dia.dia}" },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.width(56.dp),
                        )
                        Switch(
                            checked = dia.abierto,
                            onCheckedChange = { viewModel.setAbierto(dia.dia, it) },
                        )
                        if (dia.abierto) {
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = dia.abre,
                                onValueChange = { viewModel.setHora(dia.dia, true, it) },
                                label = { Text(stringResource(R.string.horario_abre)) },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = dia.cierra,
                                onValueChange = { viewModel.setHora(dia.dia, false, it) },
                                label = { Text(stringResource(R.string.horario_cierra)) },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                            )
                        } else {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.horario_cerrado),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        error?.let { err ->
            Text(
                text = stringResource(err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (guardado) {
            Text(
                text = stringResource(R.string.horario_guardado),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = viewModel::guardar) {
            Text(stringResource(R.string.horario_guardar))
        }
    }
}
