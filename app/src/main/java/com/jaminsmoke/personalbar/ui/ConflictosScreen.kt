package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.ConflictoRemoto
import com.jaminsmoke.personalbar.data.ProductoRemoto

/**
 * Pantalla «Conflictos»: lista los cambios de catálogo que no coinciden con
 * Identity y permite aceptar (aplicar la propuesta de Bar) o rechazar (mantener
 * el canónico). Es una sub-pantalla de Gestión; los conflictos son efímeros y se
 * recargan bajo demanda.
 */
@Composable
fun ConflictosScreen(viewModel: ConflictosViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.conflictos_titulo),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.conflictos_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refrescar) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.conflictos_refrescar),
                )
            }
        }

        state.aviso?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.cargando && state.conflictos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error && state.conflictos.isEmpty() -> ConflictosError(onReintentar = viewModel::refrescar)
            state.conflictos.isEmpty() -> ConflictosVacio()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.conflictos, key = { it.id }) { conflicto ->
                    ConflictoCard(
                        conflicto = conflicto,
                        resolviendo = conflicto.id in state.resolviendo,
                        onAceptar = { viewModel.aceptar(conflicto) },
                        onRechazar = { viewModel.rechazar(conflicto) },
                    )
                }
            }
        }
    }
}

/** Tarjeta de un conflicto: acción + diff canónico (Identity) → propuesto (Bar). */
@Composable
private fun ConflictoCard(
    conflicto: ConflictoRemoto,
    resolviendo: Boolean,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
) {
    val nombre = conflicto.proposed?.nombre
        ?: conflicto.canonical?.nombre
        ?: conflicto.aggregateId.take(8)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(accionLabelRes(conflicto.action)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConflictoLado(
                    etiqueta = stringResource(R.string.conflictos_canonico),
                    producto = conflicto.canonical,
                    sinProducto = "—",
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ConflictoLado(
                    etiqueta = stringResource(R.string.conflictos_propuesto),
                    producto = conflicto.proposed,
                    sinProducto = if (conflicto.action == "archivar") {
                        stringResource(R.string.conflictos_archivado)
                    } else {
                        "—"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRechazar, enabled = !resolviendo) {
                    Text(stringResource(R.string.conflictos_rechazar))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAceptar, enabled = !resolviendo) {
                    Text(stringResource(R.string.conflictos_aceptar))
                }
            }
        }
    }
}

/** Una cara del diff (Identity o Bar): nombre, precio y disponibilidad. */
@Composable
private fun ConflictoLado(
    etiqueta: String,
    producto: ProductoRemoto?,
    sinProducto: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(4.dp))
        if (producto == null) {
            Text(
                text = sinProducto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = producto.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = precioTexto(producto.precio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.conflictos_disponible,
                    stringResource(if (producto.disponible) R.string.conflictos_si else R.string.conflictos_no)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estado vacío: sin conflictos pendientes. */
@Composable
private fun ConflictosVacio() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.conflictos_vacio_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.conflictos_vacio_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Error de carga con reintento. */
@Composable
private fun ConflictosError(onReintentar: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.conflictos_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onReintentar) {
            Text(stringResource(R.string.conflictos_reintentar))
        }
    }
}

/** Recurso de texto de la acción de un conflicto. */
private fun accionLabelRes(action: String): Int = when (action) {
    "crear" -> R.string.conflictos_accion_crear
    "actualizar" -> R.string.conflictos_accion_actualizar
    "archivar" -> R.string.conflictos_accion_archivar
    else -> R.string.conflictos_accion_actualizar
}

/** Precio legible: «—» si es 0; si no, «2,50 €» con locale del dispositivo. */
private fun precioTexto(precio: Double): String {
    if (precio == 0.0) return "—"
    val importe = String.format(java.util.Locale.getDefault(), "%.2f", precio)
    return "$importe €"
}
