package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.SesionNegocio
import com.jaminsmoke.personalbar.ui.components.PbPestanasMenu
import com.jaminsmoke.personalbar.ui.components.PbSesionRequerida

/**
 * Perfil de la cuenta de negocio: email, UUID del establecimiento vinculado y
 * cambio de contraseña. El local (nombre, web, horario…) está en [LocalScreen].
 */
@Composable
fun PerfilCuentaScreen(viewModel: PerfilCuentaViewModel = viewModel()) {
    val sesion by viewModel.sesion.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val identityConfig by viewModel.identityConfig.collectAsState()

    var passwordActual by remember { mutableStateOf("") }
    var passwordNueva by remember { mutableStateOf("") }
    var passwordConfirmacion by remember { mutableStateOf("") }

    // Pestañas de sección: 0 = Cuenta, 1 = Contraseña.
    var seccion by remember { mutableStateOf(0) }

    if (!identityConfig.conectado) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.perfil_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PbSesionRequerida(
                titulo = stringResource(R.string.perfil_titulo),
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.perfil_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        PbPestanasMenu(
            titulos = listOf(
                stringResource(R.string.perfil_pestana_cuenta),
                stringResource(R.string.perfil_pestana_contrasena),
            ),
            indice = seccion,
            onSeleccionar = { seccion = it },
        )
        Spacer(Modifier.height(16.dp))
        when (seccion) {
            0 -> PerfilCuentaSeccion(sesion = sesion)
            else -> PerfilPasswordSeccion(
                passwordActual = passwordActual,
                onPasswordActual = { passwordActual = it },
                passwordNueva = passwordNueva,
                onPasswordNueva = { passwordNueva = it },
                passwordConfirmacion = passwordConfirmacion,
                onPasswordConfirmacion = { passwordConfirmacion = it },
                trabajando = trabajando,
                mensaje = mensaje,
                onCambiar = {
                    viewModel.cambiarPassword(passwordActual, passwordNueva, passwordConfirmacion)
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pestaña «Cuenta»: email y UUID del establecimiento vinculado. */
@Composable
private fun PerfilCuentaSeccion(sesion: SesionNegocio?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.perfil_email),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sesion?.email ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.perfil_uuid),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sesion?.establecimientoUuid ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Pestaña «Contraseña»: cambio de contraseña (3 campos + botón). */
@Composable
private fun PerfilPasswordSeccion(
    passwordActual: String,
    onPasswordActual: (String) -> Unit,
    passwordNueva: String,
    onPasswordNueva: (String) -> Unit,
    passwordConfirmacion: String,
    onPasswordConfirmacion: (String) -> Unit,
    trabajando: Boolean,
    mensaje: Int?,
    onCambiar: () -> Unit,
) {
    Column {
        mensaje?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = if (res == R.string.perfil_password_ok) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.perfil_password_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordActual,
                    onValueChange = onPasswordActual,
                    label = { Text(stringResource(R.string.perfil_password_actual)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordNueva,
                    onValueChange = onPasswordNueva,
                    label = { Text(stringResource(R.string.perfil_password_nueva)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordConfirmacion,
                    onValueChange = onPasswordConfirmacion,
                    label = { Text(stringResource(R.string.perfil_password_confirmacion)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCambiar,
                    enabled = !trabajando,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.perfil_password_cambiar))
                }
            }
        }
    }
}
