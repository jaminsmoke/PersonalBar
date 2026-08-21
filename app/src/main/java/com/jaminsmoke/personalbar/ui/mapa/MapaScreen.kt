package com.jaminsmoke.personalbar.ui.mapa

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.CELL_F
import com.jaminsmoke.personalbar.data.MAX_BOARD_SCALE
import com.jaminsmoke.personalbar.data.MIN_BOARD_SCALE
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.Mesa
import com.jaminsmoke.personalbar.data.MesaForma
import com.jaminsmoke.personalbar.data.MesaVisualStatus
import com.jaminsmoke.personalbar.data.Ronda
import com.jaminsmoke.personalbar.data.Sala
import com.jaminsmoke.personalbar.data.Ticket
import com.jaminsmoke.personalbar.data.ticketsAbiertosDeMesa
import com.jaminsmoke.personalbar.data.ZONA_ALTO
import com.jaminsmoke.personalbar.data.ZONA_ANCHO
import com.jaminsmoke.personalbar.data.calcularEscalaAjuste
import com.jaminsmoke.personalbar.data.clampAlBorde
import com.jaminsmoke.personalbar.data.findNearestFreeCell
import com.jaminsmoke.personalbar.data.limitarPan
import com.jaminsmoke.personalbar.data.mesaDims
import com.jaminsmoke.personalbar.data.panTrasZoom
import com.jaminsmoke.personalbar.data.zonaEmoji
import com.jaminsmoke.personalbar.ui.components.PbTicketCard
import com.jaminsmoke.personalbar.ui.theme.PbBoardCanvas
import com.jaminsmoke.personalbar.ui.theme.PbBoardGrid
import com.jaminsmoke.personalbar.ui.theme.PbBoardGridMajor
import com.jaminsmoke.personalbar.ui.theme.mesaStatusAccent
import com.jaminsmoke.personalbar.ui.theme.mesaStatusFill
import com.jaminsmoke.personalbar.ui.theme.mesaStatusOnFill
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaScreen(viewModel: MapaViewModel = viewModel()) {
    val mesas by viewModel.mesas.collectAsState()
    val salas by viewModel.salas.collectAsState()
    val estados by viewModel.estados.collectAsState()
    val salaSeleccionada by viewModel.salaSeleccionada.collectAsState()
    val rondas by viewModel.rondas.collectAsState()
    val bebida by viewModel.bebida.collectAsState()
    val comida by viewModel.comida.collectAsState()

    val salasById = remember(salas) { salas.associateBy { it.id } }
    val mesasFiltradas = remember(mesas, salaSeleccionada) {
        if (salaSeleccionada == null) mesas else mesas.filter { it.salaId == salaSeleccionada }
    }

    var mesaEditando by remember { mutableStateOf<Mesa?>(null) }
    var mesaBorrando by remember { mutableStateOf<Mesa?>(null) }
    var mesaReservando by remember { mutableStateOf<Mesa?>(null) }
    var mesaVista by remember { mutableStateOf<Mesa?>(null) }
    var crearVisible by remember { mutableStateOf(false) }
    var crearSalaVisible by remember { mutableStateOf(false) }
    var salaEditando by remember { mutableStateOf<Sala?>(null) }
    var salaBorrando by remember { mutableStateOf<Sala?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Selector de salas + acciones contextuales (siempre visibles, fuera del canvas)
        val salaActiva = salaSeleccionada?.let { salasById[it] }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (salas.isNotEmpty()) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = if (salaSeleccionada != null) {
                        salas.indexOfFirst { it.id == salaSeleccionada }.let { if (it >= 0) it + 1 else 0 }
                    } else 0,
                    modifier = Modifier.weight(1f),
                    edgePadding = 12.dp,
                ) {
                    Tab(
                        selected = salaSeleccionada == null,
                        onClick = { viewModel.setSala(null) },
                    ) {
                        Text(
                            stringResource(R.string.mapa_todas_salas),
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontWeight = if (salaSeleccionada == null) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    salas.forEach { sala ->
                        Tab(
                            selected = salaSeleccionada == sala.id,
                            onClick = { viewModel.setSala(if (salaSeleccionada == sala.id) null else sala.id) },
                        ) {
                            Text(
                                "${zonaEmoji(sala.nombre)} ${sala.nombre}",
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontWeight = if (salaSeleccionada == sala.id) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.mapa_sin_salas_titulo),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
            }

            // Botones contextuales de sala: «+» nueva sala (siempre), ✏️ renombrar y 🗑 eliminar (con sala seleccionada)
            IconButton(onClick = { crearSalaVisible = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.mapa_nueva_sala))
            }
            if (salaActiva != null) {
                IconButton(onClick = { salaEditando = salaActiva }) {
                    Icon(Icons.Default.Edit, stringResource(R.string.mapa_renombrar_sala))
                }
                IconButton(onClick = { salaBorrando = salaActiva }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.mapa_eliminar_sala), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // «Nueva mesa» cuando hay una sala seleccionada (gestión de mesas, se mantiene)
        if (salaActiva != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { crearVisible = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.mapa_nueva_mesa))
                    Text(stringResource(R.string.mapa_nueva_mesa))
                }
            }
        }

        when {
            salas.isEmpty() -> SinSalasEstado(onCrearPrimera = { crearSalaVisible = true })
            salaSeleccionada == null -> ListaMesas(
                mesas = mesasFiltradas,
                salasById = salasById,
                estados = estados,
                onClick = { mesaVista = it },
            )
            else -> BoardView(
                mesas = mesasFiltradas,
                salasById = salasById,
                estados = estados,
                onClick = { mesaVista = it },
                onEdit = { mesaEditando = it },
                onDelete = { mesaBorrando = it },
                onReservar = { mesaReservando = it },
                onRotate = { viewModel.girarMesa(it.id) },
                onCancelarReserva = { viewModel.cancelarReserva(it.id) },
                onBloquear = { viewModel.bloquearMesa(it.id) },
                onDesbloquear = { viewModel.desbloquearMesa(it.id) },
                onMove = { mesa, x, y -> viewModel.moverMesa(mesa.id, x, y) },
            )
        }
    }

    // Diálogos
    val salaParaCrear = salaSeleccionada
    if (crearVisible && salaParaCrear != null) {
        CrearMesaDialog(
            onDismiss = { crearVisible = false },
            onCreate = { forma, cap, alias -> viewModel.crearMesa(salaParaCrear, forma, cap, alias); crearVisible = false },
        )
    }

    mesaEditando?.let { mesa ->
        EditarMesaDialog(
            mesa = mesa,
            nombreSala = salasById[mesa.salaId]?.nombre.orEmpty(),
            onDismiss = { mesaEditando = null },
            onSave = { alias, cap, forma -> viewModel.editarMesa(mesa.id, alias, cap, forma); mesaEditando = null },
        )
    }

    mesaBorrando?.let { mesa ->
        AlertDialog(
            onDismissRequest = { mesaBorrando = null },
            title = { Text(stringResource(R.string.mapa_borrar_mesa_titulo)) },
            text = { Text(mesa.nombreVisible(salasById[mesa.salaId]?.nombre.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { viewModel.borrarMesa(mesa.id); mesaBorrando = null }) { Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { mesaBorrando = null }) { Text(stringResource(R.string.mapa_cancelar)) } },
        )
    }

    mesaReservando?.let { mesa ->
        ReservarDialog(
            onDismiss = { mesaReservando = null },
            onReservar = { nombre -> viewModel.reservar(mesa.id, nombre, null); mesaReservando = null },
        )
    }

    if (crearSalaVisible) {
        NombreSalaDialog(
            titulo = stringResource(R.string.mapa_nueva_sala),
            onDismiss = { crearSalaVisible = false },
            onConfirm = { nombre -> viewModel.crearSala(nombre); crearSalaVisible = false },
        )
    }

    salaEditando?.let { sala ->
        NombreSalaDialog(
            titulo = stringResource(R.string.mapa_renombrar_sala),
            inicial = sala.nombre,
            onDismiss = { salaEditando = null },
            onConfirm = { nombre -> viewModel.renombrarSala(sala.id, nombre); salaEditando = null },
        )
    }

    salaBorrando?.let { sala ->
        AlertDialog(
            onDismissRequest = { salaBorrando = null },
            title = { Text(stringResource(R.string.mapa_eliminar_sala)) },
            text = { Text(sala.nombre) },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.eliminarSala(sala.id)) viewModel.setSala(null)
                    salaBorrando = null
                }) {
                    Text(stringResource(R.string.mapa_menu_borrar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { salaBorrando = null }) { Text(stringResource(R.string.mapa_cancelar)) } },
        )
    }

    mesaVista?.let { mesa ->
        val nombreSala = salasById[mesa.salaId]?.nombre.orEmpty()
        val tickets = ticketsAbiertosDeMesa(mesa, nombreSala, rondas, bebida, comida)
        ComandaVistaSheet(
            mesa = mesa,
            nombreSala = nombreSala,
            tickets = tickets,
            rondas = rondas,
            onDismiss = { mesaVista = null },
        )
    }
}

@Composable
private fun SinSalasEstado(onCrearPrimera: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.mapa_sin_salas_titulo), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.mapa_sin_salas_subtitulo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Button(onClick = onCrearPrimera) {
                Icon(Icons.Default.Add, stringResource(R.string.mapa_crear_primera_sala))
                Text(stringResource(R.string.mapa_crear_primera_sala))
            }
        }
    }
}

@Composable
private fun ListaMesas(
    mesas: List<Mesa>,
    salasById: Map<String, Sala>,
    estados: Map<String, MesaVisualStatus>,
    onClick: (Mesa) -> Unit,
) {
    if (mesas.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.mapa_sin_mesas_titulo), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.mapa_sin_mesas_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(mesas, key = { it.id }) { mesa ->
                val estado = estados[mesa.id] ?: MesaVisualStatus.LIBRE
                val fill = mesaStatusFill(estado)
                val accent = mesaStatusAccent(estado)
                val onFill = mesaStatusOnFill()
                Card(
                    Modifier.fillMaxWidth().clickable { onClick(mesa) },
                    colors = CardDefaults.cardColors(containerColor = fill, contentColor = onFill),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${mesa.nombreVisible(salasById[mesa.salaId]?.nombre.orEmpty())} · ${salasById[mesa.salaId]?.nombre.orEmpty()}",
                                fontWeight = FontWeight.Bold,
                                color = onFill,
                            )
                            Text(
                                "${mesa.capacidad}p · ${estadoLabel(estado)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardView(
    mesas: List<Mesa>,
    salasById: Map<String, Sala>,
    estados: Map<String, MesaVisualStatus>,
    onClick: (Mesa) -> Unit,
    onEdit: (Mesa) -> Unit,
    onDelete: (Mesa) -> Unit,
    onReservar: (Mesa) -> Unit,
    onRotate: (Mesa) -> Unit,
    onCancelarReserva: (Mesa) -> Unit,
    onBloquear: (Mesa) -> Unit,
    onDesbloquear: (Mesa) -> Unit,
    onMove: (Mesa, Float, Float) -> Unit,
) {
    val density = LocalDensity.current

    var scale by remember { mutableStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var boardAutoFitado by remember { mutableStateOf(false) }

    var draggedMesa by remember { mutableStateOf<Mesa?>(null) }
    var dragBaseX by remember { mutableStateOf(0f) }
    var dragBaseY by remember { mutableStateOf(0f) }
    var dragPxX by remember { mutableStateOf(0f) }
    var dragPxY by remember { mutableStateOf(0f) }

    val scheme = MaterialTheme.colorScheme

    fun clampPan() {
        val contentW = with(density) { ZONA_ANCHO.dp.toPx() } * scale
        val contentH = with(density) { ZONA_ALTO.dp.toPx() } * scale
        val edge = with(density) { 36.dp.toPx() }
        panX = limitarPan(panX, viewportSize.width.toFloat(), contentW, edge)
        panY = limitarPan(panY, viewportSize.height.toFloat(), contentH, edge)
    }

    fun autoFit() {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val fitPadding = with(density) { 12.dp.toPx() }
            val marginDp = 2f * CELL_F
            // Encaja el CONTENIDO (bounding box de las mesas + margen), no el grid vacío.
            var leftDp = 0f
            var topDp = 0f
            var contentWDp = ZONA_ANCHO
            var contentHDp = ZONA_ALTO
            if (mesas.isNotEmpty()) {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                mesas.forEach { m ->
                    val (w, h) = mesaDims(m.forma, m.girada)
                    minX = minOf(minX, m.posX)
                    minY = minOf(minY, m.posY)
                    maxX = maxOf(maxX, m.posX + w)
                    maxY = maxOf(maxY, m.posY + h)
                }
                leftDp = minX - marginDp
                topDp = minY - marginDp
                contentWDp = maxX - minX + 2 * marginDp
                contentHDp = maxY - minY + 2 * marginDp
            }
            val newScale = calcularEscalaAjuste(
                viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                with(density) { contentWDp.dp.toPx() }, with(density) { contentHDp.dp.toPx() }, fitPadding,
            )
            scale = newScale
            // Centra el contenido en el viewport. Si el contenido es más ancho/alto que
            // el viewport, sobresale simétricamente (el usuario puede hacer pan).
            val leftPx = with(density) { leftDp.dp.toPx() } * newScale
            val topPx = with(density) { topDp.dp.toPx() } * newScale
            val contentWPx = with(density) { contentWDp.dp.toPx() } * newScale
            val contentHPx = with(density) { contentHDp.dp.toPx() } * newScale
            panX = (viewportSize.width - contentWPx) / 2f - leftPx
            panY = (viewportSize.height - contentHPx) / 2f - topPx
        }
    }

    fun zoomBy(factor: Float) {
        val newScale = (scale * factor).coerceIn(MIN_BOARD_SCALE, MAX_BOARD_SCALE)
        val ratio = newScale / scale
        val focusX = viewportSize.width / 2f
        val focusY = viewportSize.height / 2f
        panX = panTrasZoom(panX, focusX, focusX, ratio)
        panY = panTrasZoom(panY, focusY, focusY, ratio)
        scale = newScale
        clampPan()
    }

    // Cada sala tiene su propio contenido (banda del canvas): al cambiar de sala se
    // re-encaja a su bounding box. Dentro de la misma sala no se re-encaja (el usuario
    // mantiene su pan/zoom al mover mesas).
    val salaActual = mesas.firstOrNull()?.salaId
    LaunchedEffect(salaActual) { boardAutoFitado = false }
    LaunchedEffect(viewportSize, mesas) {
        if (viewportSize.width > 0 && mesas.isNotEmpty() && !boardAutoFitado) {
            autoFit()
            boardAutoFitado = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(scheme.surfaceContainerLowest)
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_BOARD_SCALE, MAX_BOARD_SCALE)
                    val ratio = newScale / scale
                    panX = (panX - centroid.x) * ratio + centroid.x + pan.x
                    panY = (panY - centroid.y) * ratio + centroid.y + pan.y
                    scale = newScale
                    clampPan()
                }
            },
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(width = ZONA_ANCHO.dp, height = ZONA_ALTO.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = panX
                    translationY = panY
                    scaleX = scale
                    scaleY = scale
                }
                .drawWithCache {
                    val spacing = CELL_F.dp.toPx()
                    val majorSpacing = spacing * 5f
                    val canvasColor = PbBoardCanvas
                    val gridColor = PbBoardGrid
                    val majorColor = PbBoardGridMajor
                    val glowColor = scheme.secondary.copy(alpha = 0.18f)
                    val accentColor = scheme.secondary.copy(alpha = 0.55f)
                    val coreColor = scheme.secondary.copy(alpha = 0.85f)
                    val dotRadius = (1.15.dp.toPx() / scale).coerceIn(0.7f, 7f)
                    val majorStroke = (0.65.dp.toPx() / scale).coerceIn(0.5f, 5f)
                    val glowW = (12.dp.toPx() / scale).coerceIn(4f, 90f)
                    val accentW = (2.dp.toPx() / scale).coerceIn(1f, 24f)
                    val coreW = (1.dp.toPx() / scale).coerceIn(0.8f, 10f)

                    onDrawBehind {
                        drawRect(canvasColor)
                        var majorX = majorSpacing
                        while (majorX < size.width) {
                            drawLine(majorColor, Offset(majorX, 0f), Offset(majorX, size.height), majorStroke)
                            majorX += majorSpacing
                        }
                        var majorY = majorSpacing
                        while (majorY < size.height) {
                            drawLine(majorColor, Offset(0f, majorY), Offset(size.width, majorY), majorStroke)
                            majorY += majorSpacing
                        }
                        var x = spacing
                        while (x < size.width) {
                            var y = spacing
                            while (y < size.height) {
                                drawCircle(gridColor, dotRadius, Offset(x, y))
                                y += spacing
                            }
                            x += spacing
                        }
                        drawRect(
                            color = glowColor,
                            topLeft = Offset(glowW / 2f, glowW / 2f),
                            size = Size(size.width - glowW, size.height - glowW),
                            style = Stroke(width = glowW),
                        )
                        drawRect(
                            color = accentColor,
                            topLeft = Offset(accentW / 2f, accentW / 2f),
                            size = Size(size.width - accentW, size.height - accentW),
                            style = Stroke(width = accentW),
                        )
                        drawRect(
                            color = coreColor,
                            topLeft = Offset(coreW / 2f, coreW / 2f),
                            size = Size(size.width - coreW, size.height - coreW),
                            style = Stroke(width = coreW),
                        )
                    }
                },
        ) {
            mesas.forEach { mesa ->
                key(mesa.id) {
                    val estado = estados[mesa.id] ?: MesaVisualStatus.LIBRE
                    val isDragging = draggedMesa?.id == mesa.id
                    val (mw, _) = mesaDims(mesa.forma, mesa.girada)
                    val animX by animateFloatAsState(mesa.posX, tween(250, easing = FastOutSlowInEasing), label = "posX")
                    val animY by animateFloatAsState(mesa.posY, tween(250, easing = FastOutSlowInEasing), label = "posY")

                    MesaCard(
                        mesa = mesa,
                        estado = estado,
                        nombreSala = salasById[mesa.salaId]?.nombre.orEmpty(),
                        isDragging = isDragging,
                        modifier = Modifier
                            .offset { IntOffset(animX.dp.roundToPx(), animY.dp.roundToPx()) }
                            .width(mw.dp),
                        onClick = { onClick(mesa) },
                        onEdit = { onEdit(mesa) },
                        onDelete = { onDelete(mesa) },
                        onRotate = { onRotate(mesa) },
                        onReservar = { onReservar(mesa) },
                        onCancelarReserva = { onCancelarReserva(mesa) },
                        onBloquear = { onBloquear(mesa) },
                        onDesbloquear = { onDesbloquear(mesa) },
                        onDragStart = {
                            draggedMesa = mesa
                            dragBaseX = mesa.posX
                            dragBaseY = mesa.posY
                            dragPxX = 0f
                            dragPxY = 0f
                        },
                        onDrag = { delta ->
                            dragPxX += delta.x
                            dragPxY += delta.y
                        },
                        onDragEnd = {
                            draggedMesa?.let { dragged ->
                                val deltaDpX = with(density) { dragPxX.toDp().value }
                                val deltaDpY = with(density) { dragPxY.toDp().value }
                                val rawX = dragBaseX + deltaDpX
                                val rawY = dragBaseY + deltaDpY
                                val snappedX = (rawX / CELL_F).roundToInt() * CELL_F
                                val snappedY = (rawY / CELL_F).roundToInt() * CELL_F
                                val (w, h) = mesaDims(dragged.forma, dragged.girada)
                                val occupied = mesas.filter { it.id != dragged.id }.map {
                                    val (ow, oh) = mesaDims(it.forma, it.girada)
                                    listOf(it.posX, it.posY, ow, oh)
                                }
                                val (bx, by) = clampAlBorde(snappedX, snappedY, w, h)
                                val (fx, fy) = findNearestFreeCell(bx, by, w, h, occupied)
                                onMove(dragged, fx, fy)
                            }
                            draggedMesa = null
                            dragPxX = 0f
                            dragPxY = 0f
                        },
                    )
                }
            }

            draggedMesa?.let { mesa ->
                val overlayX = dragBaseX + with(density) { dragPxX.toDp().value }
                val overlayY = dragBaseY + with(density) { dragPxY.toDp().value }
                val (ow, _) = mesaDims(mesa.forma, mesa.girada)
                Box(
                    Modifier
                        .offset { IntOffset(overlayX.dp.roundToPx(), overlayY.dp.roundToPx()) }
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; shadowElevation = 16f; alpha = 0.92f }
                        .width(ow.dp)
                        .zIndex(10f),
                ) {
                    DragOverlayCard(mesa, salasById[mesa.salaId]?.nombre.orEmpty(), estados[mesa.id] ?: MesaVisualStatus.LIBRE)
                }
            }
        }

        // Controles de cámara
        Card(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).zIndex(5f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoomBy(1f / 1.25f) }, enabled = scale > MIN_BOARD_SCALE) {
                    Icon(Icons.Default.ZoomOut, stringResource(R.string.mapa_zoom_out))
                }
                Text(
                    "${(scale * 100).roundToInt()}%",
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { zoomBy(1.25f) }, enabled = scale < MAX_BOARD_SCALE) {
                    Icon(Icons.Default.ZoomIn, stringResource(R.string.mapa_zoom_in))
                }
                IconButton(onClick = { autoFit() }) {
                    Icon(Icons.Default.FitScreen, stringResource(R.string.mapa_encajar))
                }
            }
        }
    }
}

// ── Diálogos ─────────────────────────────────────────────────────────────────

@Composable
private fun CrearMesaDialog(onDismiss: () -> Unit, onCreate: (MesaForma, Int, String?) -> Unit) {
    var forma by remember { mutableStateOf(MesaForma.CUADRADA) }
    var cap by remember { mutableStateOf("4") }
    var alias by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mapa_nueva_mesa)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mapa_forma), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MesaForma.entries.forEach { f ->
                        FilterChip(
                            selected = forma == f,
                            onClick = { forma = f; cap = f.capacidadDefecto.toString() },
                            label = { Text(formaLabel(f)) },
                        )
                    }
                }
                OutlinedTextField(
                    cap,
                    { cap = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.mapa_capacidad)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    alias,
                    { alias = it },
                    label = { Text(stringResource(R.string.mapa_alias)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(forma, cap.toIntOrNull() ?: 4, alias.ifBlank { null }) }) {
                Text(stringResource(R.string.mapa_crear_mesa))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) } },
    )
}

@Composable
private fun EditarMesaDialog(
    mesa: Mesa,
    nombreSala: String,
    onDismiss: () -> Unit,
    onSave: (String?, Int, MesaForma) -> Unit,
) {
    var alias by remember(mesa) { mutableStateOf(mesa.alias ?: "") }
    var cap by remember(mesa) { mutableStateOf(mesa.capacidad.toString()) }
    var forma by remember(mesa) { mutableStateOf(mesa.forma) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mapa_editar_mesa)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mesa.nombreVisible(nombreSala), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    alias,
                    { alias = it },
                    label = { Text(stringResource(R.string.mapa_alias)) },
                    placeholder = { Text(mesa.idZona(nombreSala)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    cap,
                    { cap = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.mapa_capacidad)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.mapa_forma), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MesaForma.entries.forEach { f ->
                        FilterChip(selected = forma == f, onClick = { forma = f }, label = { Text(formaLabel(f)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(alias.ifBlank { null }, cap.toIntOrNull() ?: mesa.capacidad, forma) }) {
                Text(stringResource(R.string.mapa_editar_mesa))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) } },
    )
}

@Composable
private fun ReservarDialog(onDismiss: () -> Unit, onReservar: (String) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mapa_reservar_titulo)) },
        text = {
            OutlinedTextField(
                nombre,
                { nombre = it },
                label = { Text(stringResource(R.string.mapa_nombre_reserva)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onReservar(nombre) }, enabled = nombre.isNotBlank()) {
                Text(stringResource(R.string.mapa_reservar))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) } },
    )
}

@Composable
private fun NombreSalaDialog(titulo: String, inicial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var nombre by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                nombre,
                { nombre = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nombre) }, enabled = nombre.isNotBlank()) { Text(titulo) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mapa_cancelar)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComandaVistaSheet(
    mesa: Mesa,
    nombreSala: String,
    tickets: List<Ticket>,
    rondas: List<Ronda>,
    onDismiss: () -> Unit,
) {
    val rondasPorId = remember(rondas) { rondas.associateBy { it.id } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.mapa_comanda_titulo, mesa.nombreVisible(nombreSala)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.mapa_comanda_solo_vista),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (tickets.isEmpty()) {
                Text(
                    stringResource(R.string.mapa_comanda_vacia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tickets.forEach { ticket ->
                    val ronda = rondasPorId[ticket.rondaId]
                    val destinoRes = if (ticket.destino == Destino.BARRA) {
                        R.string.mapa_ticket_destino_barra
                    } else {
                        R.string.mapa_ticket_destino_cocina
                    }
                    Text(
                        stringResource(destinoRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    PbTicketCard(
                        mesa = mesa.nombreVisible(nombreSala),
                        ronda = ronda?.numero ?: 0,
                        camarero = ronda?.camarero,
                        lineas = ticket.lineas.map { "${it.cantidad}× ${it.nombreProducto}" },
                        numeroCola = ticket.numeroCola,
                        destino = ticket.destino,
                    )
                    Spacer(Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }
}

internal fun formaLabel(forma: MesaForma): String = when (forma) {
    MesaForma.REDONDA -> "⭕"
    MesaForma.CUADRADA -> "🟩"
    MesaForma.RECTANGULAR -> "🟦"
    MesaForma.RECTANGULAR_XL -> "🟫"
}
