package nl.ramon96.medicijntracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import nl.ramon96.medicijntracker.data.prefs.AppFont
import nl.ramon96.medicijntracker.data.prefs.ThemeMode
import nl.ramon96.medicijntracker.data.prefs.ThemeSettings

@Composable
fun MedicijnTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        // Material You wins when the user asked for it, but not in high contrast: wallpaper
        // colours are chosen for looks, not for legibility.
        settings.dynamicColor && !settings.highContrast &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> Palettes.schemeFor(
            palette = Palettes.byId(settings.paletteId),
            dark = dark,
            highContrast = settings.highContrast,
        )
    }

    val typography = remember(settings.font) { typographyFor(settings.font.toFontFamily()) }

    // Scaling the density's fontScale is what makes the text-size setting reach every sp in the
    // app, including the text inside Material components we never touch.
    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.textScale) {
        Density(density = density.density, fontScale = density.fontScale * settings.textScale)
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

private fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SANS -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONO -> FontFamily.Monospace
}

/** Applies one family across every Material text style. */
private fun typographyFor(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
