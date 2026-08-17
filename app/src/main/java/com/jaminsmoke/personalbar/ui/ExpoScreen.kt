package com.jaminsmoke.personalbar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.ui.components.PbColumnHeader
import com.jaminsmoke.personalbar.ui.components.PbEmptyQueue
import com.jaminsmoke.personalbar.ui.components.PbRoomStatus
import com.jaminsmoke.personalbar.ui.components.PbTicketCard
import com.jaminsmoke.personalbar.ui.gestion.GestionAcceso
import com.jaminsmoke.personalbar.ui.gestion.GestionScreen
import com.jaminsmoke.personalbar.ui.mapa.MapaScreen
import com.jaminsmoke.personalbar.ui.sesion.SesionHeader

/** Entradas del sidebar (navigation rail) del puesto de barra. */
enum class PbSection(val route: String, val labelRes: Int, val icon: ImageVector) {
    COLAS("colas", R.string.nav_colas, Icons.Outlined.Checklist),
    MAPA("mapa", R.string.nav_mapa, Icons.Default.Map),
    GESTION("gestion", R.string.nav_gestion, Icons.Default.Business),
    AJUSTES("ajustes", R.string.nav_ajustes, Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpoScreen(
    viewModel: ExpoViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var section by remember { mutableStateOf(PbSection.COLAS) }
    // Sub-pantalla de Gestión pedida externamente (p. ej. «Ir al perfil» desde la sesión).
    var gestionSolicitud by remember { mutableStateOf<GestionAcceso?>(null) }
    // Parada del nodo pendiente de confirmación (la sala se quedaría ciega).
    var paradaPendiente by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    SesionHeader(
                        onAbrirPerfil = {
                            gestionSolicitud = GestionAcceso.PERFIL
                            section = PbSection.GESTION
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    PbRoomStatus(
                        active = uiState.roomActive,
                        fgsOk = uiState.fgsOk,
                        conectados = uiState.conectados,
                        onToggle = {
                            if (uiState.roomActive) {
                                paradaPendiente = true
                            } else {
                                viewModel.toggleLocal()
                            }
                        },
                    )
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PbSidebar(
                current = section,
                onSelect = { section = it },
            )
            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            // Zona de trabajo acotada: margen uniforme respecto al rail y al borde,
            // en todas las pantallas del viewport (mapa incluido).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                when (section) {
                    PbSection.COLAS -> ExpoColas(
                        uiState = uiState,
                        escuchando = viewModel.escuchando.collectAsState().value,
                        parcial = viewModel.parcial.collectAsState().value,
                        mensajeVoz = viewModel.mensajeVoz.collectAsState().value,
                        onAlternarDeServicio = viewModel::alternarDeServicio,
                        onSeleccionarEnMano = viewModel::seleccionarEnMano,
                        onClearPreparador = viewModel::clearPreparador,
                        onPreparar = viewModel::marcarPreparado,
                        onRecoger = viewModel::marcarRecogido,
                        onEmpezarEscucha = viewModel::empezarEscucha,
                        onDetenerEscucha = viewModel::detenerEscucha,
                        onPermisoDenegado = viewModel::notificarPermisoDenegado,
                        modifier = Modifier.fillMaxSize(),
                    )
                    PbSection.MAPA -> MapaScreen()
                    PbSection.GESTION -> GestionScreen(
                        accesoSolicitado = gestionSolicitud,
                        onAccesoSolicitadoConsumido = { gestionSolicitud = null },
                    )
                    PbSection.AJUSTES -> AjustesScreen()
                }
            }
        }
    }

    if (paradaPendiente) {
        AlertDialog(
            onDismissRequest = { paradaPendiente = false },
            title = { Text(stringResource(R.string.local_parar_titulo)) },
            text = { Text(stringResource(R.string.local_parar_mensaje)) },
            confirmButton = {
                Button(
                    onClick = {
                        paradaPendiente = false
                        viewModel.toggleLocal()
                    },
                ) {
                    Text(stringResource(R.string.local_parar_confirmar))
                }
            },
            dismissButton = {
                TextButton(onClick = { paradaPendiente = false }) {
                    Text(stringResource(R.string.mapa_cancelar))
                }
            },
        )
    }
}

@Composable
private fun PbSidebar(
    current: PbSection,
    onSelect: (PbSection) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PbSection.entries.forEach { item ->
                NavigationRailItem(
                    selected = item == current,
                    onClick = { onSelect(item) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                        )
                    },
                    label = { Text(stringResource(item.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun ExpoColas(
    uiState: ExpoUiState,
    escuchando: Boolean,
    parcial: String?,
    mensajeVoz: String?,
    onAlternarDeServicio: (String) -> Unit,
    onSeleccionarEnMano: (String) -> Unit,
    onClearPreparador: () -> Unit,
    onPreparar: (String) -> Unit,
    onRecoger: (String) -> Unit,
    onEmpezarEscucha: () -> Unit,
    onDetenerEscucha: () -> Unit,
    onPermisoDenegado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) onEmpezarEscucha()
        else onPermisoDenegado()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PbPreparadorSelector(
            camareros = uiState.camareros,
            deServicio = uiState.deServicio,
            enMano = uiState.enMano,
            onAlternar = onAlternarDeServicio,
            onSeleccionarEnMano = onSeleccionarEnMano,
            onClear = onClearPreparador,
        )
        Spacer(Modifier.height(8.dp))
        PbVozBar(
            escuchando = escuchando,
            parcial = parcial,
            mensaje = mensajeVoz,
            onTocar = {
                if (escuchando) {
                    onDetenerEscucha()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    onEmpezarEscucha()
                } else {
                    permisoLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PbQueueColumn(
                title = stringResource(R.string.cola_bebida),
                tickets = uiState.drinkQueue,
                puedePreparar = uiState.enMano != null,
                onPreparar = onPreparar,
                onRecoger = onRecoger,
                emptyIcon = Icons.Default.LocalBar,
                emptyTitle = stringResource(R.string.cola_bebida_vacia_titulo),
                emptySubtitle = stringResource(R.string.cola_bebida_vacia_subtitulo),
                modifier = Modifier.weight(1f),
            )
            PbQueueColumn(
                title = stringResource(R.string.cola_comida),
                tickets = uiState.foodQueue,
                puedePreparar = uiState.enMano != null,
                onPreparar = onPreparar,
                onRecoger = onRecoger,
                emptyIcon = Icons.Default.RestaurantMenu,
                emptyTitle = stringResource(R.string.cola_comida_vacia_titulo),
                emptySubtitle = stringResource(R.string.cola_comida_vacia_subtitulo),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Barra de escucha por voz: botón grande a todo lo ancho bajo «Quién soy».
 * Mientras escucha muestra spinner + texto parcial; después, el feedback (OK/error).
 */
@Composable
private fun PbVozBar(
    escuchando: Boolean,
    parcial: String?,
    mensaje: String?,
    onTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onTocar,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.voz_escuchar),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (escuchando) R.string.voz_escuchando else R.string.voz_escuchar),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (escuchando) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    escuchando -> parcial ?: stringResource(R.string.voz_pista)
                    mensaje != null -> mensaje
                    else -> stringResource(R.string.voz_pista)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mensaje != null && !escuchando) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Barra «Quién soy»: chips de los camareros ACTIVA. Tap = alterna «de servicio»;
 * el chip «en mano» (último de servicio pulsado) queda resaltado y es quien
 * prepara. Sin camareros de alta muestra aviso.
 */
@Composable
private fun PbPreparadorSelector(
    camareros: List<Camarero>,
    deServicio: List<Camarero>,
    enMano: Camarero?,
    onAlternar: (String) -> Unit,
    onSeleccionarEnMano: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.cola_quien_soy),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                if (camareros.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sin_camareros_aviso),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (deServicio.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.cola_en_mano, enMano?.let { it.nombre ?: it.id.take(8) }
                            ?: stringResource(R.string.cola_sin_sesion)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.cola_quitar_sesion))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                camareros.forEach { c ->
                    val nombre = c.nombre ?: c.id.take(8)
                    val deServ = c.deServicio
                    val esEnMano = enMano?.id == c.id
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = when {
                            esEnMano -> MaterialTheme.colorScheme.tertiary
                            deServ -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.clickable { onAlternar(c.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = nombre,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (deServ) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (deServ) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.cola_de_servicio_corto),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    // El texto «de servicio» fija este chip como «en mano» (quién prepara).
                                    modifier = Modifier.clickable { onSeleccionarEnMano(c.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PbQueueColumn(
    title: String,
    tickets: List<ExpoTicket>,
    puedePreparar: Boolean,
    onPreparar: (String) -> Unit,
    onRecoger: (String) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        PbColumnHeader(title = title, count = tickets.size)
        Spacer(Modifier.padding(top = 12.dp))
        if (tickets.isEmpty()) {
            PbEmptyQueue(
                icon = emptyIcon,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    PbTicketCard(
                        mesa = ticket.mesa,
                        ronda = ticket.ronda,
                        camarero = ticket.camarero,
                        lineas = ticket.lineas,
                        preparadoPor = ticket.preparadoPor,
                        estado = ticket.estado,
                        numeroCola = ticket.numeroCola,
                        destino = ticket.destino,
                        onPreparar = if (puedePreparar) ({ onPreparar(ticket.id) }) else null,
                        onRecoger = { onRecoger(ticket.id) },
                    )
                }
            }
        }
    }
}

