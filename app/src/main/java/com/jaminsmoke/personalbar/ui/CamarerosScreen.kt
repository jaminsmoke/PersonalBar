package com.jaminsmoke.personalbar.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.data.CamareroEstado
import com.jaminsmoke.personalbar.data.RolCamarero

@Composable
fun CamarerosScreen(viewModel: CamarerosViewModel = viewModel()) {
    val camareros by viewModel.camareros.collectAsState()
    var qrInput by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.camareros_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.camareros_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = qrInput,
            onValueChange = {
                qrInput = it
                errorRes = null
            },
            label = { Text(stringResource(R.string.camareros_pegar_qr)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                errorRes = when (viewModel.altaPorQr(qrInput)) {
                    AltaResultado.OK -> {
                        qrInput = ""
                        null
                    }
                    AltaResultado.QR_INVALIDO -> R.string.camareros_qr_invalido
                    AltaResultado.YA_EXISTE -> R.string.camareros_ya_existe
                }
            }) {
                Text(stringResource(R.string.camareros_alta))
            }
            errorRes?.let { res ->
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        if (camareros.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.camareros_vacia),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.camareros_vacia_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(camareros, key = { it.id }) { camarero ->
                    CamareroRow(camarero = camarero, onRevocar = { viewModel.revocar(camarero.id) })
                }
            }
        }
    }
}

@Composable
private fun CamareroRow(
    camarero: Camarero,
    onRevocar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camarero.nombre ?: camarero.id.take(8),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.camareros_identidad, camarero.id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${rolText(camarero.rol)} · ${estadoText(camarero.estado)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (camarero.estado == CamareroEstado.ACTIVA) {
                OutlinedButton(onClick = onRevocar) {
                    Text(stringResource(R.string.camareros_revocar))
                }
            }
        }
    }
}

@Composable
private fun rolText(rol: RolCamarero): String =
    stringResource(if (rol == RolCamarero.DUENO) R.string.camareros_rol_dueno else R.string.camareros_rol_staff)

@Composable
private fun estadoText(estado: CamareroEstado): String =
    stringResource(
        if (estado == CamareroEstado.ACTIVA) R.string.camareros_estado_activa else R.string.camareros_estado_revocada
    )
