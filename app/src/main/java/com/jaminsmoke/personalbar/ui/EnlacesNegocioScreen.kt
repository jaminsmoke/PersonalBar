package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.lan.IdentityEnlacePublico
import com.jaminsmoke.personalbar.ui.components.PbPestanasMenu
import com.jaminsmoke.personalbar.ui.components.PbSesionRequerida

/**
 * Panel «Enlaces del negocio»: QR públicos de la web y de la carta, oficios
 * distintos (promocional vs mesa). Muestra una tarjeta por tipo (`web` | `carta`):
 * si hay enlace activo, su QR y URL clicable (abre `url_publica` en el navegador)
 * si no, botón para crearlo. La tarjeta web también reconoce el alias legado
 * `ficha_negocio`.
 */
@Composable
fun EnlacesNegocioScreen(viewModel: EnlacesNegocioViewModel = viewModel()) {
    val enlaces by viewModel.enlaces.collectAsState()
    val identityConfig by viewModel.identityConfig.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    var revocando by remember { mutableStateOf<IdentityEnlacePublico?>(null) }

    // Pestañas de sección: 0 = Web del negocio, 1 = Carta (una tarjeta por pestaña).
    var seccion by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.enlaces_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // Sin cuenta del establecimiento conectada (header): los enlaces viven en Identity.
        if (!identityConfig.conectado) {
            PbSesionRequerida(
                titulo = stringResource(R.string.enlaces_titulo),
                modifier = Modifier.padding(top = 24.dp),
            )
            return
        }
        PbPestanasMenu(
            titulos = TipoEnlacePublico.entries.map { stringResource(it.labelRes) },
            indice = seccion,
            onSeleccionar = { seccion = it },
        )
        Spacer(Modifier.height(16.dp))
        mensaje?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (trabajando && enlaces.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val tipo = TipoEnlacePublico.entries[seccion]
            EnlaceTarjeta(
                titulo = stringResource(tipo.labelRes),
                ayuda = stringResource(tipo.ayudaRes),
                enlace = enlaces.firstOrNull { it.cubreTipo(tipo) },
                trabajando = trabajando,
                onCrear = { viewModel.crear(tipo) },
                onRotar = { viewModel.rotar(it) },
                onRevocar = { revocando = it },
            )
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

/** Tarjeta de un tipo de enlace: QR + URL con acciones, o botón de creación si falta. */
@Composable
private fun EnlaceTarjeta(
    titulo: String,
    ayuda: String,
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
            Spacer(Modifier.height(4.dp))
            Text(
                text = ayuda,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                val uriHandler = LocalUriHandler.current
                val abrirEnlace = stringResource(R.string.enlaces_abrir)
                val colorEnlace = MaterialTheme.colorScheme.primary
                val urlAnotada = remember(url, colorEnlace) {
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(
                                        color = colorEnlace,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append(url)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val qr = remember(url) { qrImageBitmap(url, 320) }
                    Image(
                        bitmap = qr,
                        contentDescription = stringResource(R.string.enlaces_qr_desc, titulo),
                        modifier = Modifier
                            .size(220.dp)
                            .clickable(
                                onClickLabel = abrirEnlace,
                                onClick = { uriHandler.openUri(url) },
                            ),
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enlaces_estado_activo),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = urlAnotada,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = abrirEnlace,
                                tint = colorEnlace,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(
                                        onClickLabel = abrirEnlace,
                                        onClick = { uriHandler.openUri(url) },
                                    ),
                            )
                        }
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
