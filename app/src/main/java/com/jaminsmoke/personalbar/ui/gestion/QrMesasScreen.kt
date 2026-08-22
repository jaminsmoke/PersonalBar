package com.jaminsmoke.personalbar.ui.gestion

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
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.lan.MesaCfcResponse
import com.jaminsmoke.personalbar.ui.qrImageBitmap

/**
 * Pantalla de gestión de QRs por mesa (CFC).
 * Muestra cada mesa sincronizada con su QR, estado y opción de rotar.
 */
@Composable
fun QrMesasScreen(vm: QrMesasViewModel = viewModel()) {
    val mesas by vm.mesas.collectAsState()
    val trabajando by vm.trabajando.collectAsState()
    val conectado by vm.conectado.collectAsState()

    var mesaA_rotar by remember { mutableStateOf<MesaCfcResponse?>(null) }

    if (!conectado) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.qr_mesas_sin_identity),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (trabajando && mesas.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally),
            )
        } else if (mesas.isEmpty()) {
            Text(
                text = stringResource(R.string.qr_mesas_vacio),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(mesas, key = { it.mesaUuid }) { mesa ->
                    MesaCfcCard(
                        mesa = mesa,
                        trabajando = trabajando,
                        onRotar = { mesaA_rotar = it },
                    )
                }
            }
        }
    }

    // Diálogo de confirmación de rotación
    mesaA_rotar?.let { mesa ->
        val nuevaEtiqueta = remember(mesa) {
            mesa.etiqueta.ifBlank { mesa.mesaUuid.take(8) }
        }
        AlertDialog(
            onDismissRequest = { mesaA_rotar = null },
            title = { Text(stringResource(R.string.qr_mesas_rotar_titulo)) },
            text = {
                Text(
                    stringResource(R.string.qr_mesas_rotar_texto, nuevaEtiqueta),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rotar(mesa.mesaUuid)
                        mesaA_rotar = null
                    },
                ) {
                    Text(stringResource(R.string.qr_mesas_rotar_confirmar))
                }
            },
            dismissButton = {
                TextButton(onClick = { mesaA_rotar = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            },
        )
    }
}

@Composable
private fun MesaCfcCard(
    mesa: MesaCfcResponse,
    trabajando: Boolean,
    onRotar: (MesaCfcResponse) -> Unit,
) {
    val activo = mesa.estado.equals("activo", ignoreCase = true)
    val url = mesa.urlPublica
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (activo) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            },
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // QR
            if (url != null) {
                val qr = remember(url) { qrImageBitmap(url, 320) }
                Image(
                    bitmap = qr,
                    contentDescription = stringResource(R.string.qr_mesas_qr_desc, mesa.etiqueta),
                    modifier = Modifier.size(120.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 2.dp,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Etiqueta + badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mesa.etiqueta.ifBlank { mesa.mesaUuid.take(8) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource(
                                    if (activo) R.string.qr_mesas_estado_activo
                                    else R.string.qr_mesas_estado_revocado,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                imageVector = if (activo) {
                                    Icons.Outlined.QrCode2
                                } else {
                                    Icons.Outlined.LinkOff
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                // URL
                if (url != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Botón rotar
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRotar(mesa) },
                    enabled = !trabajando && activo,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.qr_mesas_rotar),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.qr_mesas_rotar))
                }
            }
        }
    }
}
