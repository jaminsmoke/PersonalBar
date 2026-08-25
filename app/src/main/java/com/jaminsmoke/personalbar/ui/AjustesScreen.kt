package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jaminsmoke.personalbar.lan.SesionItem

/** Ajustes: configuración del establecimiento (las salas se gestionan en el Mapa)
 *  y, desde v0.4, las sesiones de dispositivo de la cuenta de negocio (revocar). */
@Composable
fun AjustesScreen(viewModel: AjustesViewModel = viewModel()) {
    val establecimiento by viewModel.establecimiento.collectAsState()
    val sesiones by viewModel.sesiones.collectAsState()
    val cargando by viewModel.cargandoSesiones.collectAsState()
    val error by viewModel.errorSesiones.collectAsState()

    // Cargar las sesiones al entrar en la pantalla.
    LaunchedEffect(Unit) { viewModel.listarSesiones() }

    var sesionARevocar by remember { mutableStateOf<SesionItem?>(null) }
    var confirmarOtras by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
            text = stringResource(R.string.ajustes_sesiones_titulo),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))

        when {
            cargando -> {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
            sesiones.isEmpty() -> {
                Text(
                    text = stringResource(R.string.ajustes_sesiones_vacia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sesiones, key = { it.id }) { sesion ->
                        SesionFila(
                            sesion = sesion,
                            onRevocar = { sesionARevocar = sesion },
                        )
                    }
                }
                if (sesiones.any { !it.actual }) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { confirmarOtras = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ajustes_sesiones_revocar_otras))
                    }
                }
            }
        }

        if (error) {
            Text(
                text = stringResource(R.string.ajustes_sesiones_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    sesionARevocar?.let { sesion ->
        AlertDialog(
            onDismissRequest = { sesionARevocar = null },
            title = { Text(stringResource(R.string.ajustes_sesiones_confirmar_una)) },
            text = { Text(sesion.etiqueta ?: sesion.id) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revocarSesion(sesion.id)
                        sesionARevocar = null
                    },
                ) {
                    Text(stringResource(R.string.ajustes_sesiones_revocar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sesionARevocar = null }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }

    if (confirmarOtras) {
        AlertDialog(
            onDismissRequest = { confirmarOtras = false },
            title = { Text(stringResource(R.string.ajustes_sesiones_confirmar_otras)) },
            text = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revocarOtrasSesiones()
                        confirmarOtras = false
                    },
                ) {
                    Text(stringResource(R.string.ajustes_sesiones_revocar_otras), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarOtras = false }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }
}

/** Fila de una sesión de dispositivo: etiqueta/id, fechas, badge «actual» y revocar. */
@Composable
private fun SesionFila(
    sesion: SesionItem,
    onRevocar: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sesion.etiqueta ?: sesion.id,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (sesion.actual) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ajustes_sesiones_actual),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ajustes_sesiones_creada, sesion.creadaEn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sesion.ultimoUsoEn.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ajustes_sesiones_ultimo_uso, sesion.ultimoUsoEn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!sesion.actual) {
                TextButton(onClick = onRevocar) {
                    Text(stringResource(R.string.ajustes_sesiones_revocar), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
