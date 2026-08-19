package com.jaminsmoke.personalbar.ui.sesion

import android.graphics.BitmapFactory
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.ui.SesionViewModel

/**
 * Indicador de sesión del header: icono de cuenta. Sin sesión abre el modal de
 * login/registro; con sesión muestra el nombre del establecimiento (y su logo, si
 * tiene) y permite cerrar.
 */
@Composable
fun SesionHeader(
    viewModel: SesionViewModel = viewModel(),
    onAbrirPerfil: () -> Unit = {},
) {
    val sesion by viewModel.sesion.collectAsState()
    val logoBytes by viewModel.logoBytes.collectAsState()
    var modalVisible by remember { mutableStateOf(false) }

    val logoBitmap = remember(logoBytes) {
        logoBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                .getOrNull()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sesion != null) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap,
                    contentDescription = stringResource(R.string.sesion_logo),
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = sesion?.nombreMostrar ?: stringResource(R.string.sesion_cuenta_negocio),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            sesion?.let { PbSesionValidez(it) }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { modalVisible = true }) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = stringResource(R.string.sesion_cuenta_negocio),
            )
        }
    }

    if (modalVisible) {
        SesionDialog(
            sesion = sesion,
            viewModel = viewModel,
            onDismiss = { modalVisible = false },
            onAbrirPerfil = onAbrirPerfil,
        )
    }
}

/**
 * Badge de validez de la sesión local: «Sesión válida hasta el <fecha>» (formato
 * del dispositivo). Si quedan menos de 24 h, muestra el aviso en color de warning.
 * Sin `validaHasta` (o 0 = inválida) no se muestra nada.
 */
@Composable
private fun PbSesionValidez(sesion: SesionNegocio) {
    val validaHasta = sesion.validaHasta ?: return
    if (validaHasta <= 0L) return
    val ahora = System.currentTimeMillis()
    val caducaPronto = validaHasta - ahora <= 24 * 60 * 60 * 1000L
    val config = LocalConfiguration.current
    val fecha = DateFormat.getDateInstance(DateFormat.MEDIUM, config.locales[0])
        .format(Date(validaHasta))
    Text(
        text = if (caducaPronto) {
            stringResource(R.string.sesion_caduca_pronto)
        } else {
            stringResource(R.string.sesion_valida_hasta, fecha)
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (caducaPronto) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** Modal de sesión: registro y login como flujos separados, o sesión activa con logout. */
@Composable
private fun SesionDialog(
    sesion: SesionNegocio?,
    viewModel: SesionViewModel,
    onDismiss: () -> Unit,
    onAbrirPerfil: () -> Unit,
) {
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()

    if (sesion != null) {
        val desvinculado = sesion.establecimientoUuid == null
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.sesion_cuenta_negocio)) },
            text = {
                if (desvinculado) {
                    Column {
                        Text(stringResource(R.string.sesion_establecimiento_desvinculado))
                        mensaje?.let { id ->
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(id), color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Text(sesion.nombreMostrar ?: sesion.email ?: "")
                }
            },
            confirmButton = {
                if (desvinculado) {
                    Button(
                        onClick = { viewModel.revincular() },
                        enabled = !trabajando,
                    ) {
                        Text(stringResource(R.string.sesion_revincular))
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            onAbrirPerfil()
                        },
                    ) {
                        Text(stringResource(R.string.sesion_ir_al_perfil))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.logout()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.sesion_cerrar))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.sesion_cuenta_negocio))
        },
        text = {
            SesionForm(viewModel = viewModel)
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) }
        },
    )
}
