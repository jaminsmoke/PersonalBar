package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
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
import com.jaminsmoke.personalbar.data.Sala

/** Ajustes: cuenta del establecimiento (lectura) y CRUD de salas del mapa. */
@Composable
fun AjustesScreen(viewModel: AjustesViewModel = viewModel()) {
    val establecimiento by viewModel.establecimiento.collectAsState()
    val salas by viewModel.salas.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    var nuevaSala by remember { mutableStateOf("") }

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
