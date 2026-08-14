package com.jaminsmoke.personalbar.ui

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
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Camarero
import com.jaminsmoke.personalbar.ui.components.PbColumnHeader
import com.jaminsmoke.personalbar.ui.components.PbEmptyQueue
import com.jaminsmoke.personalbar.ui.components.PbRoomStatus
import com.jaminsmoke.personalbar.ui.components.PbTicketCard
import com.jaminsmoke.personalbar.ui.mapa.MapaScreen

/** Entradas del sidebar (navigation rail) del puesto de barra. */
enum class PbSection(val route: String, val labelRes: Int, val icon: ImageVector) {
    COLAS("colas", R.string.nav_colas, Icons.Outlined.Checklist),
    MAPA("mapa", R.string.nav_mapa, Icons.Default.Map),
    CAMAREROS("camareros", R.string.nav_camareros, Icons.Default.QrCode),
    AJUSTES("ajustes", R.string.nav_ajustes, Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpoScreen(
    viewModel: ExpoViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var section by remember { mutableStateOf(PbSection.COLAS) }

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
                    PbRoomStatus(
                        active = uiState.roomActive,
                        onToggle = viewModel::toggleLocal,
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
            when (section) {
                PbSection.COLAS -> ExpoColas(
                    uiState = uiState,
                    onSetPreparador = viewModel::setPreparador,
                    onClearPreparador = viewModel::clearPreparador,
                    onPreparar = viewModel::marcarPreparado,
                    onRecoger = viewModel::marcarRecogido,
                    modifier = Modifier.weight(1f),
                )
                PbSection.MAPA -> MapaScreen()
                PbSection.CAMAREROS -> CamarerosScreen()
                PbSection.AJUSTES -> AjustesScreen()
            }
        }
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
    onSetPreparador: (String) -> Unit,
    onClearPreparador: () -> Unit,
    onPreparar: (String) -> Unit,
    onRecoger: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PbPreparadorSelector(
            active = uiState.activeCamarero,
            camareros = uiState.camareros,
            onSet = onSetPreparador,
            onClear = onClearPreparador,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PbQueueColumn(
                title = stringResource(R.string.cola_bebida),
                tickets = uiState.drinkQueue,
                puedePreparar = uiState.activeCamarero != null,
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
                puedePreparar = uiState.activeCamarero != null,
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

/** Barra «Quién soy»: sesión activa del preparador (lista blanca ACTIVA) o aviso. */
@Composable
private fun PbPreparadorSelector(
    active: Camarero?,
    camareros: List<Camarero>,
    onSet: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cola_quien_soy),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Box {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { menuOpen = true },
                ) {
                    Text(
                        text = active?.let { it.nombre ?: it.id.take(8) }
                            ?: stringResource(R.string.cola_sin_sesion),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    camareros.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.nombre ?: c.id.take(8)) },
                            onClick = {
                                onSet(c.id)
                                menuOpen = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cola_quitar_sesion)) },
                        onClick = {
                            onClear()
                            menuOpen = false
                        },
                    )
                }
            }
            if (camareros.isEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sin_camareros_aviso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                        onPreparar = if (puedePreparar) ({ onPreparar(ticket.id) }) else null,
                        onRecoger = { onRecoger(ticket.id) },
                    )
                }
            }
        }
    }
}

