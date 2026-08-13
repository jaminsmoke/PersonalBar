package com.jaminsmoke.personalbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Identidad dark premium de Personal Bar. `dynamicColor` desactivado. */
private val PbDarkColorScheme = darkColorScheme(
    primary = PbPrimary,
    onPrimary = PbOnPrimary,
    primaryContainer = PbPrimaryContainer,
    onPrimaryContainer = PbOnPrimaryContainer,
    secondary = PbSecondary,
    onSecondary = PbOnSecondary,
    secondaryContainer = PbSecondaryContainer,
    onSecondaryContainer = PbOnSecondaryContainer,
    tertiary = PbTertiary,
    onTertiary = PbOnTertiary,
    tertiaryContainer = PbTertiaryContainer,
    onTertiaryContainer = PbOnTertiaryContainer,
    error = PbError,
    onError = PbOnError,
    errorContainer = PbErrorContainer,
    onErrorContainer = PbOnErrorContainer,
    background = PbBackground,
    onBackground = PbOnBackground,
    surface = PbSurface,
    onSurface = PbOnSurface,
    surfaceVariant = PbSurfaceVariant,
    onSurfaceVariant = PbOnSurfaceVariant,
    surfaceDim = PbSurfaceDim,
    surfaceBright = PbSurfaceBright,
    surfaceContainerLowest = PbSurfaceContainerLowest,
    surfaceContainerLow = PbSurfaceContainerLow,
    surfaceContainer = PbSurfaceContainer,
    surfaceContainerHigh = PbSurfaceContainerHigh,
    surfaceContainerHighest = PbSurfaceContainerHighest,
    outline = PbOutline,
    outlineVariant = PbOutlineVariant,
    inverseSurface = PbInverseSurface,
    inverseOnSurface = PbInverseOnSurface,
    inversePrimary = PbInversePrimary,
)

/** Light mínimo espejo (no es el look de producto). */
private val PbLightColorScheme = lightColorScheme(
    primary = PbInversePrimary,
    onPrimary = PbPrimary,
    primaryContainer = PbPrimary,
    onPrimaryContainer = PbOnPrimary,
    secondary = PbSecondaryContainer,
    onSecondary = PbSecondary,
    secondaryContainer = PbSecondary,
    onSecondaryContainer = PbOnSecondary,
    tertiary = PbOnTertiaryContainer,
    onTertiary = PbTertiary,
    tertiaryContainer = PbTertiary,
    onTertiaryContainer = PbOnTertiary,
    error = PbErrorContainer,
    onError = PbError,
    errorContainer = PbError,
    onErrorContainer = PbOnError,
    background = PbInverseSurface,
    onBackground = PbInverseOnSurface,
    surface = PbInverseSurface,
    onSurface = PbInverseOnSurface,
    surfaceVariant = PbSurfaceVariant,
    onSurfaceVariant = PbOnSurfaceVariant,
    outline = PbOutline,
    outlineVariant = PbOutlineVariant,
    inverseSurface = PbSurface,
    inverseOnSurface = PbOnSurface,
    inversePrimary = PbPrimary,
)

@Composable
fun PersonalBarTheme(
    /** Forzar scheme de marca dark (recomendado para producto). */
    forceBrandDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (forceBrandDark) PbDarkColorScheme else PbLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PbTypography,
        content = content,
    )
}
