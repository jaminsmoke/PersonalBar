package com.jaminsmoke.personalbar.ui.theme

import androidx.compose.ui.graphics.Color
import com.jaminsmoke.personalbar.data.MesaVisualStatus
import com.jaminsmoke.personalbar.data.ZonaColor

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

// ─── Colores de zona (paleta fija, tokens de espacio físico fuera del theme) ───
// Estilo «territorio de banda» (GTA): fill semi-transparente + borde más opaco,
// con el nombre/camarero encima. Se mapean por nombre de `ZonaColor` (viaja por LAN).

val PbZonaAzul = Color(0xFF3B82F6)
val PbZonaVerde = Color(0xFF22C55E)
val PbZonaAmarillo = Color(0xFFEAB308)
val PbZonaNaranja = Color(0xFFF97316)
val PbZonaMorado = Color(0xFF8B5CF6)
val PbZonaRojo = Color(0xFFEF4444)

/** Relleno semi-transparente de la zona (se dibuja detrás de las mesas). */
fun zonaColorFill(color: ZonaColor): Color = when (color) {
    ZonaColor.AZUL -> PbZonaAzul.copy(alpha = 0.22f)
    ZonaColor.VERDE -> PbZonaVerde.copy(alpha = 0.20f)
    ZonaColor.AMARILLO -> PbZonaAmarillo.copy(alpha = 0.24f)
    ZonaColor.NARANJA -> PbZonaNaranja.copy(alpha = 0.22f)
    ZonaColor.MORADO -> PbZonaMorado.copy(alpha = 0.22f)
    ZonaColor.ROJO -> PbZonaRojo.copy(alpha = 0.20f)
}

/** Borde de la zona (más opaco que el fill, para delimitar el territorio). */
fun zonaColorAccent(color: ZonaColor): Color = when (color) {
    ZonaColor.AZUL -> PbZonaAzul.copy(alpha = 0.85f)
    ZonaColor.VERDE -> PbZonaVerde.copy(alpha = 0.85f)
    ZonaColor.AMARILLO -> PbZonaAmarillo.copy(alpha = 0.9f)
    ZonaColor.NARANJA -> PbZonaNaranja.copy(alpha = 0.85f)
    ZonaColor.MORADO -> PbZonaMorado.copy(alpha = 0.85f)
    ZonaColor.ROJO -> PbZonaRojo.copy(alpha = 0.85f)
}

/** Color sólido de la zona (para el chip del selector y el texto de contraste). */
fun zonaColorSolid(color: ZonaColor): Color = when (color) {
    ZonaColor.AZUL -> PbZonaAzul
    ZonaColor.VERDE -> PbZonaVerde
    ZonaColor.AMARILLO -> PbZonaAmarillo
    ZonaColor.NARANJA -> PbZonaNaranja
    ZonaColor.MORADO -> PbZonaMorado
    ZonaColor.ROJO -> PbZonaRojo
}

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
