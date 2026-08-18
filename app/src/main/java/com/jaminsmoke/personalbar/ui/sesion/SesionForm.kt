package com.jaminsmoke.personalbar.ui.sesion

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.ui.SesionViewModel

/**
 * Formulario de sesión del puesto (login/registro de la cuenta del establecimiento).
 * Reutilizable: el modal del header ([SesionDialog]) y el workspace bloqueado sin
 * sesión lo comparten. Al éxito, [SesionViewModel.sesion] cambia y el gate de la
 * raíz monta el puesto al instante (sin navegación manual).
 */
@Composable
fun SesionForm(
    viewModel: SesionViewModel,
    modifier: Modifier = Modifier,
) {
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    var modoRegistro by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TipoEstablecimiento.BAR) }
    var logoUri by remember { mutableStateOf<Uri?>(null) }
    var recordar by remember { mutableStateOf(false) }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) logoUri = uri
    }

    Column(modifier = modifier) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickLogo.launch("image/*") }) {
                    Text(stringResource(R.string.sesion_elegir_logo))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (logoUri == null) R.string.sesion_logo_opcional else R.string.sesion_logo_seleccionado
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !trabajando,
            onClick = {
                if (modoRegistro) {
                    viewModel.registro(nombre, email, password, tipo, logoUri, recordar)
                } else {
                    viewModel.login(email, password, recordar)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (modoRegistro) R.string.sesion_registrarse else R.string.sesion_entrar))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { modoRegistro = !modoRegistro },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (modoRegistro) R.string.sesion_iniciar_sesion else R.string.sesion_crear_cuenta))
        }
    }
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
    TipoEstablecimiento.COPAS -> R.string.sesion_tipo_copas
}
