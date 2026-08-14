package com.jaminsmoke.personalbar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaminsmoke.personalbar.R
import com.jaminsmoke.personalbar.data.Destino
import com.jaminsmoke.personalbar.data.TicketEstado
import com.jaminsmoke.personalbar.ui.theme.PbGoldGradientBottom
import com.jaminsmoke.personalbar.ui.theme.PbGoldGradientTop
import com.jaminsmoke.personalbar.ui.theme.PbOnSecondary
import com.jaminsmoke.personalbar.ui.theme.PbOnTicketPendiente
import com.jaminsmoke.personalbar.ui.theme.PbOnTicketPreparado
import com.jaminsmoke.personalbar.ui.theme.PbTicketPendiente
import com.jaminsmoke.personalbar.ui.theme.PbTicketPreparado

/** Estado del local, clickeable: activo (mint) o inactivo (gris). Arranca/para el nodo. */
@Composable
fun PbRoomStatus(
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (active) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = modifier
            .clickable(onClick = onToggle)
            .background(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (active) R.string.local_activo else R.string.local_inactivo
            ),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
    }
}

/** Cabecera de columna de la expo: título + contador de tickets. */
@Composable
fun PbColumnHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Tarjeta de ticket del puesto: mesa, ronda, pedido por, preparado por, estado y
 * acciones («sello» Preparar/Recoger). En la expo, con `onRecoger` presente pero sin
 * preparador (`onPreparar` nulo), reserva el slot y muestra la pista «sin preparador».
 * En solo-lectura (ambos nulos, p. ej. el mapa) no se pinta acción. Los defaults
 * mantienen el uso en MapaScreen.
 */
@Composable
fun PbTicketCard(
    mesa: String,
    ronda: Int,
    camarero: String?,
    lineas: List<String>,
    modifier: Modifier = Modifier,
    preparadoPor: String? = null,
    estado: TicketEstado = TicketEstado.PENDIENTE,
    /** Id de cola visible/hablable («Cola 1 Bebida»); 0 = no mostrarlo. */
    numeroCola: Int = 0,
    destino: Destino? = null,
    onPreparar: (() -> Unit)? = null,
    onRecoger: (() -> Unit)? = null,
) {
    // Fondo por estado: PENDIENTE post-it / PREPARADO listo (recogidas no se pintan en expo).
    val fill = when (estado) {
        TicketEstado.PENDIENTE -> PbTicketPendiente
        else -> PbTicketPreparado
    }
    val onFill = when (estado) {
        TicketEstado.PENDIENTE -> PbOnTicketPendiente
        else -> PbOnTicketPreparado
    }
    val idCola = when (destino) {
        Destino.BARRA -> stringResource(R.string.cola_id_bebida, numeroCola)
        Destino.COCINA -> stringResource(R.string.cola_id_comida, numeroCola)
        null -> null
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = fill,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (idCola != null) {
                        Text(
                            text = idCola,
                            style = MaterialTheme.typography.titleLarge,
                            color = onFill,
                        )
                    }
                    Text(
                        text = mesa,
                        style = MaterialTheme.typography.titleMedium,
                        color = onFill,
                    )
                }
                Text(
                    text = stringResource(R.string.ticket_ronda, ronda),
                    style = MaterialTheme.typography.labelMedium,
                    color = onFill.copy(alpha = 0.7f),
                )
            }
            if (camarero != null) {
                Text(
                    text = stringResource(R.string.ticket_camarero, camarero),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onFill.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (preparadoPor != null) {
                Text(
                    text = stringResource(R.string.ticket_preparado_por, preparadoPor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PbOnTicketPreparado,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            lineas.forEach { linea ->
                Text(
                    text = linea,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onFill,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (estado == TicketEstado.PREPARADO) {
                Text(
                    text = stringResource(R.string.ticket_estado_preparado),
                    style = MaterialTheme.typography.labelLarge,
                    color = PbOnTicketPreparado,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            PbTicketAction(
                estado = estado,
                onPreparar = onPreparar,
                onRecoger = onRecoger,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Acción «sello» de la tarjeta de cola: botón ancho con icono + verbo y relleno
 * por estado. PENDIENTE → «Preparar» (ink); PREPARADO → «Recoger» (gold). En la
 * expo, si está accionable (`onRecoger` presente) pero no hay preparador en mano
 * (`onPreparar` nulo), reserva el slot con la pista «sin preparador». En
 * solo-lectura (ambos nulos, p. ej. el mapa) no pinta nada.
 */
@Composable
fun PbTicketAction(
    estado: TicketEstado,
    onPreparar: (() -> Unit)?,
    onRecoger: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        estado == TicketEstado.PENDIENTE && onPreparar != null -> {
            PbTicketSello(
                text = stringResource(R.string.accion_preparado),
                icon = Icons.Outlined.CheckCircle,
                fill = SolidColor(PbOnTicketPendiente),
                contentColor = PbTicketPendiente,
                onClick = onPreparar,
                modifier = modifier,
            )
        }
        estado == TicketEstado.PREPARADO && onRecoger != null -> {
            PbTicketSello(
                text = stringResource(R.string.accion_recogido),
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                fill = Brush.verticalGradient(listOf(PbGoldGradientTop, PbGoldGradientBottom)),
                contentColor = PbOnSecondary,
                onClick = onRecoger,
                modifier = modifier,
            )
        }
        estado == TicketEstado.PENDIENTE && onRecoger != null -> {
            Text(
                text = stringResource(R.string.accion_sin_preparador),
                style = MaterialTheme.typography.bodySmall,
                color = PbOnTicketPendiente.copy(alpha = 0.7f),
                modifier = modifier,
            )
        }
        // else: solo-lectura (p. ej. mapa) → sin acción
    }
}

/** Botón «sello» a todo lo ancho: relleno (sólido o degradado), icono y verbo. */
@Composable
private fun PbTicketSello(
    text: String,
    icon: ImageVector,
    fill: Brush,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
        }
    }
}
