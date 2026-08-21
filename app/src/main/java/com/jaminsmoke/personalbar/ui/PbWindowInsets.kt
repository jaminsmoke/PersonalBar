package com.jaminsmoke.personalbar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * Insets del puesto de barra: no pintar UI táctil bajo status, recorte, IME,
 * barra de navegación (3 botones o gestos) ni taskbar táctil de tablet.
 *
 * Usar en el [androidx.compose.material3.Scaffold] raíz y en overlays a
 * pantalla completa. El rail no debe volver a consumir barras: el padre ya
 * las reservó vía `innerPadding`.
 */
@Composable
fun pbShellWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.union(WindowInsets.tappableElement)
