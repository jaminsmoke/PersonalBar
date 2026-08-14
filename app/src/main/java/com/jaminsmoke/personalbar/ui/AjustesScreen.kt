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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.jaminsmoke.personalbar.data.Sala

/** Ajustes: establecimiento, conexión con Identity y CRUD de salas del mapa. */
@Composable
fun AjustesScreen(viewModel: AjustesViewModel = viewModel()) {
    val establecimiento by viewModel.establecimiento.collectAsState()
    val salas by viewModel.salas.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val identityConfig by viewModel.identityConfig.collectAsState()
    val identityTrabajando by viewModel.trabajando.collectAsState()
    val identityError by viewModel.identityError.collectAsState()

    var nuevaSala by remember { mutableStateOf("") }
    var identityUrl by remember { mutableStateOf("") }
    var identityEmail by remember { mutableStateOf("") }
    var identityPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.ajustes_titulo_establecimiento),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = establecimiento.nombre,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        IdentitySection(
            conectado = identityConfig.conectado,
            establecimientoUuid = identityConfig.establecimientoUuid,
            trabajando = identityTrabajando,
            errorRes = identityError,
            url = identityUrl,
            email = identityEmail,
            password = identityPassword,
            onUrl = { identityUrl = it },
            onEmail = { identityEmail = it },
            onPassword = { identityPassword = it },
            onConectar = {
                viewModel.conectarIdentity(identityUrl, identityEmail, identityPassword)
            },
            onDesconectar = viewModel::desconectarIdentity,
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.ajustes_titulo_salas),
            style = MaterialTheme.typography.titleLarge,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(salas, key = { it.id }) { sala ->
                SalaRow(
                    sala = sala,
                    onRenombrar = { nombre -> viewModel.renombrarSala(sala.id, nombre) },
                    onEliminar = { viewModel.eliminarSala(sala.id) },
                )
            }
        }

        mensaje?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = nuevaSala,
                onValueChange = { nuevaSala = it },
                label = { Text(stringResource(R.string.ajustes_nueva_sala)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    viewModel.crearSala(nuevaSala)
                    nuevaSala = ""
                },
                enabled = nuevaSala.isNotBlank(),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ajustes_anadir))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ajustes_anadir))
            }
        }
    }
}

/** Sección Identity de Ajustes: conexión con PersonalHostel-Identity (cuenta de negocio). */
@Composable
private fun IdentitySection(
    conectado: Boolean,
    establecimientoUuid: String?,
    trabajando: Boolean,
    errorRes: Int?,
    url: String,
    email: String,
    password: String,
    onUrl: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConectar: () -> Unit,
    onDesconectar: () -> Unit,
) {
    Text(
        text = stringResource(R.string.identity_titulo),
        style = MaterialTheme.typography.titleLarge,
    )
    if (conectado) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.identity_conectado,
                    establecimientoUuid?.take(8) ?: "—",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDesconectar) {
                Text(stringResource(R.string.identity_desconectar))
            }
        }
    } else {
        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            label = { Text(stringResource(R.string.identity_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmail,
            label = { Text(stringResource(R.string.identity_email)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text(stringResource(R.string.identity_password)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onConectar, enabled = !trabajando) {
                Text(if (trabajando) "…" else stringResource(R.string.identity_conectar))
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
    }
}

@Composable
private fun SalaRow(
    sala: Sala,
    onRenombrar: (String) -> Unit,
    onEliminar: () -> Unit,
) {
    var nombre by remember(sala.id) { mutableStateOf(sala.nombre) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        IconButton(onClick = { onRenombrar(nombre) }) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.ajustes_renombrar))
        }
        IconButton(onClick = onEliminar) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ajustes_eliminar))
        }
    }
}
