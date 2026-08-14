package com.jaminsmoke.personalbar.ui.sesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.ui.SesionViewModel

/**
 * Indicador de sesión del header: icono de cuenta. Sin sesión abre el modal de
 * login/registro; con sesión muestra el nombre del establecimiento y permite cerrar.
 */
@Composable
fun SesionHeader(
    viewModel: SesionViewModel = viewModel(),
) {
    val sesion by viewModel.sesion.collectAsState()
    var modalVisible by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sesion != null) {
            Text(
                text = sesion?.nombreMostrar ?: stringResource(R.string.sesion_cuenta_negocio),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { modalVisible = true }) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = stringResource(R.string.sesion_cuenta_negocio),
            )
        }
    }

    if (modalVisible) {
        SesionDialog(
            sesion = sesion,
            viewModel = viewModel,
            onDismiss = { modalVisible = false },
        )
    }
}

/** Modal de sesión: registro y login como flujos separados, o sesión activa con logout. */
@Composable
private fun SesionDialog(
    sesion: SesionNegocio?,
    viewModel: SesionViewModel,
    onDismiss: () -> Unit,
) {
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    if (sesion != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.sesion_cuenta_negocio)) },
            text = { Text(sesion.nombreMostrar ?: sesion.email ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.sesion_cerrar))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
        return
    }

    var modoRegistro by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TipoEstablecimiento.BAR) }
    var logoClave by remember { mutableStateOf("") }
    var recordar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (modoRegistro) R.string.sesion_crear_cuenta else R.string.sesion_iniciar_sesion))
        },
        text = {
            Column {
                if (modoRegistro) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text(stringResource(R.string.sesion_nombre)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.sesion_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.sesion_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (modoRegistro) {
                    Spacer(Modifier.height(8.dp))
                    TipoSelector(
                        seleccionado = tipo,
                        onSeleccionar = { tipo = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = logoClave,
                        onValueChange = { logoClave = it },
                        label = { Text(stringResource(R.string.sesion_logo)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recordar, onCheckedChange = { recordar = it })
                    Text(stringResource(R.string.sesion_recordar))
                }
                mensaje?.let { res ->
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !trabajando,
                onClick = {
                    if (modoRegistro) {
                        viewModel.registro(nombre, email, password, tipo, logoClave.takeIf { it.isNotBlank() }, recordar)
                    } else {
                        viewModel.login(email, password, recordar)
                    }
                },
            ) {
                Text(stringResource(if (modoRegistro) R.string.sesion_registrarse else R.string.sesion_entrar))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { modoRegistro = !modoRegistro }) {
                    Text(stringResource(if (modoRegistro) R.string.sesion_iniciar_sesion else R.string.sesion_crear_cuenta))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
            }
        },
    )
}

/** Selector del tipo de establecimiento (menú desplegable). */
@Composable
private fun TipoSelector(
    seleccionado: TipoEstablecimiento,
    onSeleccionar: (TipoEstablecimiento) -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expandido = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(tipoLabel(seleccionado)))
        }
        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            TipoEstablecimiento.entries.forEach { tipo ->
                DropdownMenuItem(
                    text = { Text(stringResource(tipoLabel(tipo))) },
                    onClick = {
                        onSeleccionar(tipo)
                        expandido = false
                    },
                )
            }
        }
    }
}

private fun tipoLabel(tipo: TipoEstablecimiento): Int = when (tipo) {
    TipoEstablecimiento.BAR -> R.string.sesion_tipo_bar
    TipoEstablecimiento.RESTAURANTE -> R.string.sesion_tipo_restaurante
    TipoEstablecimiento.CAFETERIA -> R.string.sesion_tipo_cafeteria
    TipoEstablecimiento.PUB -> R.string.sesion_tipo_pub
}
