package com.jaminsmoke.personalbar.ui.theme

import androidx.compose.ui.graphics.Color
import com.jaminsmoke.personalbar.data.MesaVisualStatus

// ─── Colores de estado visual de mesa (idénticos a Commander, independientes del theme) ───

val PbMesaLibreFill = Color(0xFFA5D6A7)
val PbMesaLibreAccent = Color(0xFF1B5E20)

val PbMesaOcupadaFill = Color(0xFFFFE082)
val PbMesaOcupadaAccent = Color(0xFFF57F17)

val PbMesaEnCocinaFill = Color(0xFFFFB74D)
val PbMesaEnCocinaAccent = Color(0xFFE65100)

val PbMesaReservadaFill = Color(0xFFCE93D8)
val PbMesaReservadaAccent = Color(0xFF6A1B9A)

val PbMesaBloqueadaFill = Color(0xFFEF9A9A)
val PbMesaBloqueadaAccent = Color(0xFFB71C1C)

val PbMesaOnFill = Color(0xFF1A1A1A)

fun mesaStatusFill(status: MesaVisualStatus): Color = when (status) {
    MesaVisualStatus.LIBRE -> PbMesaLibreFill
    MesaVisualStatus.OCUPADA -> PbMesaOcupadaFill
    MesaVisualStatus.EN_COCINA -> PbMesaEnCocinaFill
    MesaVisualStatus.RESERVADA -> PbMesaReservadaFill
    MesaVisualStatus.BLOQUEADA -> PbMesaBloqueadaFill
}

fun mesaStatusAccent(status: MesaVisualStatus): Color = when (status) {
    MesaVisualStatus.LIBRE -> PbMesaLibreAccent
    MesaVisualStatus.OCUPADA -> PbMesaOcupadaAccent
    MesaVisualStatus.EN_COCINA -> PbMesaEnCocinaAccent
    MesaVisualStatus.RESERVADA -> PbMesaReservadaAccent
    MesaVisualStatus.BLOQUEADA -> PbMesaBloqueadaAccent
}

fun mesaStatusOnFill(): Color = PbMesaOnFill
