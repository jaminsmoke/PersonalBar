package com.jaminsmoke.personalbar.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.TipoEstablecimiento
import com.jaminsmoke.personalbar.ui.components.PbSesionRequerida
import com.jaminsmoke.personalbar.ui.gestion.HorarioScreen

/**
 * Local: identidad, web pública, fotos, horario y apariencia del establecimiento.
 */
@Composable
fun LocalScreen(viewModel: LocalViewModel = viewModel()) {
    val identityConfig by viewModel.identityConfig.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    var seccion by remember { mutableStateOf(LocalSeccion.IDENTIDAD) }

    if (!identityConfig.conectado) {
        Column(modifier = Modifier.fillMaxSize()) {
            PbSesionRequerida(
                titulo = stringResource(R.string.gestion_local),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        mensaje?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = if (res == R.string.local_guardado) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LocalSeccion.entries.forEach { dest ->
                    FilterChip(
                        selected = seccion == dest,
                        onClick = { seccion = dest },
                        label = { Text(stringResource(dest.labelRes)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (seccion) {
                    LocalSeccion.IDENTIDAD -> LocalIdentidad(viewModel)
                    LocalSeccion.WEB -> LocalWeb(viewModel)
                    LocalSeccion.FOTOS -> LocalFotos(viewModel)
                    LocalSeccion.HORARIO -> HorarioScreen()
                    LocalSeccion.APARIENCIA -> LocalApariencia(viewModel)
                }
            }
        }
    }
}

private val LocalSeccion.labelRes: Int
    get() = when (this) {
        LocalSeccion.IDENTIDAD -> R.string.local_seccion_identidad
        LocalSeccion.WEB -> R.string.local_seccion_web
        LocalSeccion.FOTOS -> R.string.local_seccion_fotos
        LocalSeccion.HORARIO -> R.string.local_seccion_horario
        LocalSeccion.APARIENCIA -> R.string.local_seccion_apariencia
    }

@Composable
private fun LocalIdentidad(viewModel: LocalViewModel) {
    val establecimiento by viewModel.establecimiento.collectAsState()
    val sesion by viewModel.sesion.collectAsState()
    val logoBytes by viewModel.logoBytes.collectAsState()
    val visibleDirectorio by viewModel.visibleDirectorio.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    var editandoNombre by remember { mutableStateOf(false) }
    var nombreDraft by remember { mutableStateOf("") }
    val logoBitmap = remember(logoBytes) {
        logoBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.subirLogo(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = stringResource(R.string.perfil_logo),
                        modifier = Modifier.size(72.dp),
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = stringResource(R.string.perfil_logo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.perfil_logo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickLogo.launch("image/*") }, enabled = !trabajando) {
                            Text(stringResource(R.string.perfil_cambiar_logo))
                        }
                        if (logoBitmap != null) {
                            OutlinedButton(onClick = { viewModel.borrarLogo() }, enabled = !trabajando) {
                                Text(stringResource(R.string.perfil_borrar_logo))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.perfil_nombre),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editandoNombre) {
                    OutlinedTextField(
                        value = nombreDraft,
                        onValueChange = { nombreDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.editarNombre(nombreDraft)
                                editandoNombre = false
                            },
                            enabled = !trabajando,
                        ) { Text(stringResource(R.string.perfil_guardar)) }
                        OutlinedButton(onClick = { editandoNombre = false }) {
                            Text(stringResource(R.string.perfil_cancelar))
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = establecimiento.nombre,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = {
                            nombreDraft = establecimiento.nombre
                            editandoNombre = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.perfil_editar),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.perfil_tipo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoEstablecimiento.entries.forEach { tipo ->
                        FilterChip(
                            selected = sesion?.tipo == tipo,
                            onClick = { viewModel.editarTipo(tipo) },
                            label = { Text(stringResource(tipoLabel(tipo))) },
                            enabled = !trabajando,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
                    Text(stringResource(R.string.perfil_visible_directorio), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.perfil_visible_directorio_aviso),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = visibleDirectorio,
                    onCheckedChange = { viewModel.editarVisibilidad(it) },
                    enabled = !trabajando,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LocalWeb(viewModel: LocalViewModel) {
    val eslogan by viewModel.eslogan.collectAsState()
    val descripcion by viewModel.descripcion.collectAsState()
    val direccion by viewModel.direccion.collectAsState()
    val ciudad by viewModel.ciudad.collectAsState()
    val telefono by viewModel.telefono.collectAsState()
    val emailContacto by viewModel.emailContacto.collectAsState()
    val web by viewModel.web.collectAsState()
    val instagram by viewModel.instagram.collectAsState()
    val facebook by viewModel.facebook.collectAsState()
    val tiktok by viewModel.tiktok.collectAsState()
    val webUrl by viewModel.webUrl.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { webUrl?.let { uriHandler.openUri(it) } },
            enabled = webUrl != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (webUrl != null) R.string.perfil_abrir_web else R.string.perfil_sin_web),
            )
        }
        OutlinedTextField(
            value = eslogan,
            onValueChange = { viewModel.setEslogan(it.take(140)) },
            label = { Text(stringResource(R.string.local_eslogan)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = descripcion,
            onValueChange = { viewModel.setDescripcion(it) },
            label = { Text(stringResource(R.string.local_descripcion)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        OutlinedTextField(
            value = direccion,
            onValueChange = { viewModel.setDireccion(it) },
            label = { Text(stringResource(R.string.local_direccion)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = ciudad,
            onValueChange = { viewModel.setCiudad(it) },
            label = { Text(stringResource(R.string.local_ciudad)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = telefono,
            onValueChange = { viewModel.setTelefono(it) },
            label = { Text(stringResource(R.string.local_telefono)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = emailContacto,
            onValueChange = { viewModel.setEmailContacto(it) },
            label = { Text(stringResource(R.string.local_email_contacto)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = web,
            onValueChange = { viewModel.setWeb(it) },
            label = { Text(stringResource(R.string.local_web)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = instagram,
            onValueChange = { viewModel.setInstagram(it) },
            label = { Text(stringResource(R.string.local_instagram)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = facebook,
            onValueChange = { viewModel.setFacebook(it) },
            label = { Text(stringResource(R.string.local_facebook)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = tiktok,
            onValueChange = { viewModel.setTiktok(it) },
            label = { Text(stringResource(R.string.local_tiktok)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { viewModel.guardarWeb() },
            enabled = !trabajando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.local_guardar_web))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LocalFotos(viewModel: LocalViewModel) {
    val heroBytes by viewModel.heroBytes.collectAsState()
    val galeria by viewModel.galeria.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()
    val heroBitmap = remember(heroBytes) {
        heroBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val pickHero = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.subirHero(uri)
    }
    val pickGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.subirGaleria(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.local_hero), style = MaterialTheme.typography.titleMedium)
        if (heroBitmap != null) {
            Image(
                bitmap = heroBitmap,
                contentDescription = stringResource(R.string.local_hero),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickHero.launch("image/*") }, enabled = !trabajando) {
                Text(stringResource(R.string.local_cambiar_hero))
            }
            if (heroBitmap != null) {
                OutlinedButton(onClick = { viewModel.borrarHero() }, enabled = !trabajando) {
                    Text(stringResource(R.string.local_quitar_hero))
                }
            }
        }
        Text(stringResource(R.string.local_galeria), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            galeria.forEach { item ->
                val bmp = remember(item.bytes) {
                    item.bytes?.let { bytes ->
                        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = stringResource(R.string.local_galeria),
                            modifier = Modifier.size(96.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(modifier = Modifier.size(96.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    }
                    OutlinedButton(
                        onClick = { viewModel.borrarGaleria(item.id) },
                        enabled = !trabajando,
                    ) {
                        Text(stringResource(R.string.local_quitar_foto))
                    }
                }
            }
        }
        OutlinedButton(onClick = { pickGaleria.launch("image/*") }, enabled = !trabajando) {
            Text(stringResource(R.string.local_anadir_foto))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LocalApariencia(viewModel: LocalViewModel) {
    val colorPrimario by viewModel.colorPrimario.collectAsState()
    val tz by viewModel.tz.collectAsState()
    val plantilla by viewModel.plantilla.collectAsState()
    val webPublica by viewModel.webPublica.collectAsState()
    val mostrarEquipo by viewModel.mostrarEquipo.collectAsState()
    val trabajando by viewModel.trabajando.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = colorPrimario,
            onValueChange = { viewModel.setColorPrimario(it.take(20)) },
            label = { Text(stringResource(R.string.local_color)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = tz,
            onValueChange = { viewModel.setTz(it) },
            label = { Text(stringResource(R.string.local_tz)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            text = stringResource(R.string.local_plantilla, plantilla),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.local_web_publica), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.local_web_publica_aviso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = webPublica, onCheckedChange = { viewModel.setWebPublica(it) }, enabled = !trabajando)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.local_mostrar_equipo), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.local_mostrar_equipo_aviso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = mostrarEquipo, onCheckedChange = { viewModel.setMostrarEquipo(it) }, enabled = !trabajando)
        }
        Button(
            onClick = { viewModel.guardarApariencia() },
            enabled = !trabajando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.local_guardar_apariencia))
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun tipoLabel(tipo: TipoEstablecimiento): Int = when (tipo) {
    TipoEstablecimiento.BAR -> R.string.sesion_tipo_bar
    TipoEstablecimiento.RESTAURANTE -> R.string.sesion_tipo_restaurante
    TipoEstablecimiento.CAFETERIA -> R.string.sesion_tipo_cafeteria
    TipoEstablecimiento.PUB -> R.string.sesion_tipo_pub
    TipoEstablecimiento.COPAS -> R.string.sesion_tipo_copas
}
