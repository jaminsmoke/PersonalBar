package com.jaminsmoke.personalbar.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Brand / surfaces (dark premium alineado a Commander, no copiado) ───────

val PbBackground = Color(0xFF101416)
val PbSurface = Color(0xFF101416)
val PbSurfaceDim = Color(0xFF101416)
val PbSurfaceBright = Color(0xFF363A3C)
val PbSurfaceContainerLowest = Color(0xFF0B0F10)
val PbSurfaceContainerLow = Color(0xFF181C1E)
val PbSurfaceContainer = Color(0xFF1C2022)
val PbSurfaceContainerHigh = Color(0xFF262B2C)
val PbSurfaceContainerHighest = Color(0xFF313537)
val PbSurfaceVariant = Color(0xFF313537)

val PbOnBackground = Color(0xFFE0E3E5)
val PbOnSurface = Color(0xFFE0E3E5)
val PbOnSurfaceVariant = Color(0xFFC4C6CC)

val PbOutline = Color(0xFF8E9196)
val PbOutlineVariant = Color(0xFF44474B)

// Primary — cool slate
val PbPrimary = Color(0xFFBAC8D8)
val PbOnPrimary = Color(0xFF25323E)
val PbPrimaryContainer = Color(0xFF121F2B)
val PbOnPrimaryContainer = Color(0xFFD6E4F5)
val PbInversePrimary = Color(0xFF53606E)

// Secondary — gold (marca / CTA)
val PbSecondary = Color(0xFFE9C349)
val PbOnSecondary = Color(0xFF3C2F00)
val PbSecondaryContainer = Color(0xFFAF8D11)
val PbOnSecondaryContainer = Color(0xFF342800)

// Tertiary — mint (libre / OK)
val PbTertiary = Color(0xFF80D6C3)
val PbOnTertiary = Color(0xFF00382F)
val PbTertiaryContainer = Color(0xFF00231D)
val PbOnTertiaryContainer = Color(0xFF9CF3DE)

// Error
val PbError = Color(0xFFFFB4AB)
val PbOnError = Color(0xFF690005)
val PbErrorContainer = Color(0xFF93000A)
val PbOnErrorContainer = Color(0xFFFFDAD6)

val PbInverseSurface = Color(0xFFE0E3E5)
val PbInverseOnSurface = Color(0xFF2D3133)

/**
 * Plano del board: sepia/crema, fuera del ColorScheme.
 * El viewport alrededor sigue en surface oscura. Las mesas pastel necesitan este suelo.
 */
val PbBoardCanvas = Color(0xFFF2E8D5)
val PbBoardGrid = Color(0xFF3D3428).copy(alpha = 0.14f)
val PbBoardGridMajor = Color(0xFF3D3428).copy(alpha = 0.22f)

/** Gradiente FAB / botón primario (gold). */
val PbGoldGradientTop = Color(0xFFE9C349)
val PbGoldGradientBottom = Color(0xFFAF8D11)

// ─── Estado de ticket en la expo (fuera del scheme, como MesaColors) ─────────
// Lectura a distancia: PENDIENTE = post-it amarillo, PREPARADO = listo verde.

/** Fondo de tarjeta PENDIENTE (post-it). */
val PbTicketPendiente = Color(0xFFFFE082)
val PbOnTicketPendiente = Color(0xFF3C2F00)

/** Fondo de tarjeta PREPARADO (listo). */
val PbTicketPreparado = Color(0xFFA5D6A7)
val PbOnTicketPreparado = Color(0xFF1B5E20)
