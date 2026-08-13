package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import com.jaminsmoke.personalbar.ui.components.PbColumnHeader
import com.jaminsmoke.personalbar.ui.components.PbEmptyQueue
import com.jaminsmoke.personalbar.ui.components.PbRoomStatus
import com.jaminsmoke.personalbar.ui.components.PbTicketCard

/** Entradas del sidebar (navigation rail) del puesto de barra. */
enum class PbSection(val route: String, val labelRes: Int, val icon: ImageVector) {
    COLAS("colas", R.string.nav_colas, Icons.Outlined.Checklist),
    MAPA("mapa", R.string.nav_mapa, Icons.Default.Map),
    ALTA_QR("alta_qr", R.string.nav_alta_qr, Icons.Default.QrCode),
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
                    PbRoomStatus(active = uiState.roomActive)
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
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            when (section) {
                PbSection.COLAS -> ExpoColas(
                    drinkQueue = uiState.drinkQueue,
                    foodQueue = uiState.foodQueue,
                    modifier = Modifier.weight(1f),
                )
                PbSection.MAPA -> PbSectionPlaceholder(stringResource(R.string.nav_mapa))
                PbSection.ALTA_QR -> PbSectionPlaceholder(stringResource(R.string.nav_alta_qr))
                PbSection.AJUSTES -> PbSectionPlaceholder(stringResource(R.string.nav_ajustes))
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
    drinkQueue: List<TicketStub>,
    foodQueue: List<TicketStub>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PbQueueColumn(
            title = stringResource(R.string.cola_bebida),
            tickets = drinkQueue,
            emptyIcon = Icons.Default.LocalBar,
            emptyTitle = stringResource(R.string.cola_bebida_vacia_titulo),
            emptySubtitle = stringResource(R.string.cola_bebida_vacia_subtitulo),
            modifier = Modifier.weight(1f),
        )
        PbQueueColumn(
            title = stringResource(R.string.cola_comida),
            tickets = foodQueue,
            emptyIcon = Icons.Default.RestaurantMenu,
            emptyTitle = stringResource(R.string.cola_comida_vacia_titulo),
            emptySubtitle = stringResource(R.string.cola_comida_vacia_subtitulo),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PbQueueColumn(
    title: String,
    tickets: List<TicketStub>,
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
                items(tickets, key = { it.mesa + it.ronda + it.destino }) { ticket ->
                    PbTicketCard(
                        mesa = ticket.mesa,
                        ronda = ticket.ronda,
                        camarero = ticket.camarero,
                        lineas = ticket.lineas,
                    )
                }
            }
        }
    }
}

@Composable
private fun PbSectionPlaceholder(name: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.section_placeholder, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
