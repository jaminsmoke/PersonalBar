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
import com.jaminsmoke.personalbar.data.Invitacion
import com.jaminsmoke.personalbar.data.InvitacionEstado
import com.jaminsmoke.personalbar.data.RolCamarero

@Composable
fun CamarerosScreen(viewModel: CamarerosViewModel = viewModel()) {
    val camareros by viewModel.camareros.collectAsState()
    val identityConfig by viewModel.identityConfig.collectAsState()
    val invitaciones by viewModel.invitaciones.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var qrInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize(),
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
            },
            label = { Text(stringResource(R.string.camareros_pegar_qr)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (viewModel.altaPorQr(qrInput) == AltaResultado.OK) qrInput = ""
                },
                enabled = !trabajando,
            ) {
                Text(stringResource(R.string.camareros_alta))
            }
            mensaje?.let { res ->
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        InvitacionSection(
            conectado = identityConfig.conectado,
            isOnline = isOnline,
            invitaciones = invitaciones,
            trabajando = trabajando,
            email = emailInput,
            onEmail = { emailInput = it },
            onInvitar = {
                viewModel.invitarPorEmail(emailInput)
                emailInput = ""
            },
            onRevocar = viewModel::revocarInvitacion,
            onSincronizar = viewModel::sincronizar,
        )
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

/** Alta por email: buscar el camarero en Identity, crear la invitación y mostrar el estado. */
@Composable
private fun InvitacionSection(
    conectado: Boolean,
    isOnline: Boolean,
    invitaciones: List<Invitacion>,
    trabajando: Boolean,
    email: String,
    onEmail: (String) -> Unit,
    onInvitar: () -> Unit,
    onRevocar: (String) -> Unit,
    onSincronizar: () -> Unit,
) {
    Text(
        text = stringResource(R.string.camareros_invitar_titulo),
        style = MaterialTheme.typography.titleMedium,
    )
    if (!conectado) {
        Text(
            text = stringResource(R.string.camareros_sin_identity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    if (!isOnline) {
        Text(
            text = stringResource(R.string.sin_conexion_aviso),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmail,
            label = { Text(stringResource(R.string.camareros_invitar_email)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = onInvitar, enabled = !trabajando) {
            Text(stringResource(R.string.camareros_invitar))
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onSincronizar, enabled = !trabajando) {
            Text(stringResource(R.string.camareros_sincronizar))
        }
    }
    if (invitaciones.isEmpty()) {
        Text(
            text = stringResource(R.string.camareros_invitaciones_vacia),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        invitaciones.forEach { invitacion ->
            InvitacionRow(invitacion = invitacion, onRevocar = { onRevocar(invitacion.id) })
        }
    }
}

@Composable
private fun InvitacionRow(
    invitacion: Invitacion,
    onRevocar: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = invitacion.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = invitacionEstadoText(invitacion.estado),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (invitacion.estado == InvitacionEstado.PENDIENTE) {
                OutlinedButton(onClick = onRevocar) {
                    Text(stringResource(R.string.camareros_invitar_revocar))
                }
            }
        }
    }
}

@Composable
private fun invitacionEstadoText(estado: InvitacionEstado): String = when (estado) {
    InvitacionEstado.PENDIENTE -> stringResource(R.string.camareros_invitacion_estado_pendiente)
    InvitacionEstado.ACEPTADA -> stringResource(R.string.camareros_invitacion_estado_aceptada)
    InvitacionEstado.REVOCADA -> stringResource(R.string.camareros_invitacion_estado_revocada)
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
