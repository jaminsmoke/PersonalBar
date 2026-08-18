package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.lan.IdentityEnlacePublico
import com.jaminsmoke.personalbar.ui.components.PbSesionRequerida

/**
 * Panel «Enlaces del negocio»: QR públicos de la ficha y la carta. Muestra una
 * tarjeta por tipo (ficha_negocio | carta): si hay enlace activo, su QR y URL
 * (fuente de Identity) con acciones rotar/revocar; si no, botón para crearlo.
 */
@Composable
fun EnlacesNegocioScreen(viewModel: EnlacesNegocioViewModel = viewModel()) {
    val enlaces by viewModel.enlaces.collectAsState()
    val identityConfig by viewModel.identityConfig.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    var revocando by remember { mutableStateOf<IdentityEnlacePublico?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.enlaces_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.enlaces_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Sin cuenta del establecimiento conectada (header): los enlaces viven en Identity.
        if (!identityConfig.conectado) {
            PbSesionRequerida(
                titulo = stringResource(R.string.enlaces_titulo),
                modifier = Modifier.padding(top = 24.dp),
            )
            return
        }
        mensaje?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        if (trabajando && enlaces.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(TipoEnlacePublico.entries, key = { it.apiValor }) { tipo ->
                    EnlaceTarjeta(
                        titulo = stringResource(tipo.labelRes),
                        enlace = enlaces.firstOrNull { e -> e.tipo == tipo.apiValor && esActivo(e) },
                        trabajando = trabajando,
                        onCrear = { viewModel.crear(tipo) },
                        onRotar = { viewModel.rotar(it) },
                        onRevocar = { revocando = it },
                    )
                }
            }
        }
    }

    revocando?.let { enlace ->
        AlertDialog(
            onDismissRequest = { revocando = null },
            title = { Text(stringResource(R.string.enlaces_revocar_titulo)) },
            text = { Text(stringResource(R.string.enlaces_revocar_mensaje)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revocar(enlace.id)
                        revocando = null
                    },
                ) {
                    Text(stringResource(R.string.enlaces_revocar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revocando = null }) { Text(stringResource(R.string.mapa_cancelar)) }
            },
        )
    }
}

/** Un enlace es «activo» si Identity lo marca así (los revocados/rotados se descartan). */
private fun esActivo(enlace: IdentityEnlacePublico): Boolean =
    enlace.estado.equals("activo", ignoreCase = true)

/** Tarjeta de un tipo de enlace: QR + URL con acciones, o botón de creación si falta. */
@Composable
private fun EnlaceTarjeta(
    titulo: String,
    enlace: IdentityEnlacePublico?,
    trabajando: Boolean,
    onCrear: () -> Unit,
    onRotar: (String) -> Unit,
    onRevocar: (IdentityEnlacePublico) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            val url = enlace?.urlPublica
            if (enlace == null || url.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.enlaces_sin_enlace),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCrear, enabled = !trabajando) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.enlaces_crear),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.enlaces_crear))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val qr = remember(url) { qrImageBitmap(url, 320) }
                    Image(
                        bitmap = qr,
                        contentDescription = stringResource(R.string.enlaces_qr_desc, titulo),
                        modifier = Modifier.size(220.dp),
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enlaces_estado_activo),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRotar(enlace.id) }, enabled = !trabajando) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.enlaces_rotar),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.enlaces_rotar))
                            }
                            OutlinedButton(onClick = { onRevocar(enlace) }, enabled = !trabajando) {
                                Icon(
                                    imageVector = Icons.Outlined.LinkOff,
                                    contentDescription = stringResource(R.string.enlaces_revocar),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.enlaces_revocar))
                            }
                        }
                    }
                }
            }
        }
    }
}
