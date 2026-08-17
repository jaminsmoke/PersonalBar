package com.jaminsmoke.personalbar.ui.gestion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.ui.CamarerosScreen
import com.jaminsmoke.personalbar.ui.CartaScreen
import com.jaminsmoke.personalbar.ui.EnlacesNegocioScreen
import com.jaminsmoke.personalbar.ui.PerfilEstablecimientoScreen

/**
 * Acceso del hub de Gestión: icono + nombre de contexto debajo.
 * Extensible: la carta del bar y otras pantallas de gestión serán nuevas entradas.
 */
enum class GestionAcceso(
    val labelRes: Int,
    val icon: ImageVector,
) {
    CAMAREROS(R.string.gestion_camareros, Icons.Default.QrCode),
    CARTA(R.string.gestion_carta, Icons.Default.RestaurantMenu),
    ENLACES(R.string.gestion_enlaces, Icons.Default.Link),
    PERFIL(R.string.gestion_perfil, Icons.Default.Storefront),
}

/**
 * Pantalla «Gestión»: si no hay sub-pantalla seleccionada muestra el hub de iconos
 * (relleno por filas, hasta 8 columnas); si la hay, muestra esa sub-pantalla con
 * una barra superior de retorno al hub.
 */
@Composable
fun GestionScreen() {
    var seleccion by remember { mutableStateOf<GestionAcceso?>(null) }

    when (val actual = seleccion) {
        null -> PbGestionHub(
            accesos = GestionAcceso.entries,
            onSeleccionar = { seleccion = it },
        )
        GestionAcceso.CAMAREROS -> GestionSubPantalla(
            titulo = stringResource(R.string.gestion_camareros),
            onVolver = { seleccion = null },
        ) {
            CamarerosScreen()
        }
        GestionAcceso.CARTA -> GestionSubPantalla(
            titulo = stringResource(R.string.gestion_carta),
            onVolver = { seleccion = null },
        ) {
            CartaScreen()
        }
        GestionAcceso.ENLACES -> GestionSubPantalla(
            titulo = stringResource(R.string.gestion_enlaces),
            onVolver = { seleccion = null },
        ) {
            EnlacesNegocioScreen()
        }
        GestionAcceso.PERFIL -> GestionSubPantalla(
            titulo = stringResource(R.string.gestion_perfil),
            onVolver = { seleccion = null },
        ) {
            PerfilEstablecimientoScreen()
        }
    }
}

/** Barra de retorno + contenido de una sub-pantalla de gestión. */
@Composable
private fun GestionSubPantalla(
    titulo: String,
    onVolver: () -> Unit,
    contenido: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVolver) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.gestion_volver),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            contenido()
        }
    }
}

/**
 * Hub de iconos con el nombre debajo. Se rellena **por filas**: el primer acceso
 * ocupa col1/fila1, el segundo col2/fila1… hasta [columnas] (8), y salta a la fila siguiente.
 */
@Composable
private fun PbGestionHub(
    accesos: List<GestionAcceso>,
    onSeleccionar: (GestionAcceso) -> Unit,
    columnas: Int = 8,
) {
    val filas = accesos.chunked(columnas)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.gestion_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        filas.forEach { fila ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                fila.forEach { acceso ->
                    PbGestionAcceso(
                        acceso = acceso,
                        onClick = { onSeleccionar(acceso) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Rellenar la fila incompleta para que el siguiente elemento caiga en su columna.
                repeat(columnas - fila.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Celda del hub: icono grande centrado + nombre de contexto debajo. */
@Composable
private fun PbGestionAcceso(
    acceso: GestionAcceso,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = acceso.icon,
                contentDescription = stringResource(acceso.labelRes),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(acceso.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
