package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R

/** Ajustes: configuración del establecimiento (la gestión de salas vive en el Mapa). */
@Composable
fun AjustesScreen(viewModel: AjustesViewModel = viewModel()) {
    val establecimiento by viewModel.establecimiento.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize(),
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
    }
}
