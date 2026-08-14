package com.jaminsmoke.personalbar.ui.mapa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.MesaVisualStatus
import com.jaminsmoke.personalbar.data.esRectangular
import com.jaminsmoke.personalbar.data.mesaDims
import com.jaminsmoke.personalbar.data.mesaShapeRadius
import com.jaminsmoke.personalbar.ui.theme.mesaStatusAccent
import com.jaminsmoke.personalbar.ui.theme.mesaStatusFill
import com.jaminsmoke.personalbar.ui.theme.mesaStatusOnFill

@Composable
internal fun estadoLabel(estado: MesaVisualStatus): String = stringResource(
    when (estado) {
        MesaVisualStatus.LIBRE -> R.string.mapa_estado_libre
        MesaVisualStatus.OCUPADA -> R.string.mapa_estado_ocupada
        MesaVisualStatus.EN_COCINA -> R.string.mapa_estado_en_cocina
        MesaVisualStatus.RESERVADA -> R.string.mapa_estado_reservada
        MesaVisualStatus.BLOQUEADA -> R.string.mapa_estado_bloqueada
    }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MesaCard(
    mesa: Mesa,
    estado: MesaVisualStatus,
    nombreSala: String,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRotate: () -> Unit,
    onReservar: () -> Unit,
    onCancelarReserva: () -> Unit,
    onBloquear: () -> Unit,
    onDesbloquear: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val fill = mesaStatusFill(estado)
    val accent = mesaStatusAccent(estado)
    val onFill = mesaStatusOnFill()
    val shapeRadius = mesaShapeRadius(mesa.forma).dp
    val (_, cardHf) = mesaDims(mesa.forma, mesa.girada)
    val cardHeight = cardHf.dp
    val esRedonda = mesa.forma == MesaForma.REDONDA

    var menuExpanded by remember { mutableStateOf(false) }
    var dragArrancado by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .height(cardHeight)
            .graphicsLayer { if (isDragging) alpha = 0.4f }
            .pointerInput(mesa.id, "tap") {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(mesa.id, "drag") {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        menuExpanded = true
                        dragArrancado = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (!dragArrancado) {
                            dragArrancado = true
                            menuExpanded = false
                            onDragStart()
                        }
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        if (dragArrancado) onDragEnd()
                        dragArrancado = false
                    },
                    onDragCancel = {
                        if (dragArrancado) onDragEnd()
                        dragArrancado = false
                    },
                )
            },
        shape = RoundedCornerShape(shapeRadius),
        colors = CardDefaults.cardColors(containerColor = fill, contentColor = onFill),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (esRedonda) 16.dp else 8.dp),
            contentAlignment = if (esRedonda) Alignment.Center else Alignment.TopStart,
        ) {
            Column {
                Text(
                    text = mesa.nombreVisible(nombreSala),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onFill,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mesa.alias != null) {
                    Text(
                        text = mesa.idZona(nombreSala),
                        style = MaterialTheme.typography.labelSmall,
                        color = onFill.copy(alpha = 0.7f),
                    )
                }
                Text(
                    text = "${mesa.capacidad}p · ${estadoLabel(estado)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
            }

            Box(Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mapa_menu_editar)) },
                        onClick = { menuExpanded = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    )
                    if (esRectangular(mesa.forma)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mapa_menu_girar)) },
                            onClick = { menuExpanded = false; onRotate() },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        )
                    }
                    when (estado) {
                        MesaVisualStatus.LIBRE -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mapa_menu_reservar)) },
                                onClick = { menuExpanded = false; onReservar() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mapa_menu_bloquear)) },
                                onClick = { menuExpanded = false; onBloquear() },
                            )
                        }
                        MesaVisualStatus.RESERVADA -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mapa_menu_cancelar_reserva)) },
                                onClick = { menuExpanded = false; onCancelarReserva() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mapa_menu_bloquear)) },
                                onClick = { menuExpanded = false; onBloquear() },
                            )
                        }
                        MesaVisualStatus.BLOQUEADA -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mapa_menu_desbloquear)) },
                                onClick = { menuExpanded = false; onDesbloquear() },
                            )
                        }
                        MesaVisualStatus.OCUPADA, MesaVisualStatus.EN_COCINA -> Unit
                    }
                }
            }
        }
    }
}

@Composable
internal fun DragOverlayCard(mesa: Mesa, nombreSala: String, estado: MesaVisualStatus) {
    val (cardWf, cardHf) = mesaDims(mesa.forma, mesa.girada)
    val fill = mesaStatusFill(estado)
    val onFill = mesaStatusOnFill()
    Card(
        modifier = Modifier.width(cardWf.dp).height(cardHf.dp),
        shape = RoundedCornerShape(mesaShapeRadius(mesa.forma).dp),
        colors = CardDefaults.cardColors(containerColor = fill, contentColor = onFill),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                text = mesa.nombreVisible(nombreSala),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onFill,
            )
        }
    }
}
