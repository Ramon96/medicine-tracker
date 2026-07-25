package nl.ramon96.medicijntracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A named accent colour, with its light and dark tones picked so text on top always has enough
 * contrast. Hand-tuned rather than generated from a seed: a free colour picker happily produces
 * combinations that are unreadable, which defeats the point of the accessibility settings.
 */
data class Palette(
    val id: String,
    /** Dutch label shown in settings. */
    val label: String,
    /** Colour of the settings swatch. */
    val swatch: Color,

    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,

    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

object Palettes {

    val all: List<Palette> = listOf(
        Palette(
            id = "roze", label = "Roze", swatch = Color(0xFF984061),
            lightPrimary = Color(0xFF984061), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFFFD9E2), lightOnContainer = Color(0xFF3E001D),
            darkPrimary = Color(0xFFFFB1C8), darkOnPrimary = Color(0xFF5E1133),
            darkContainer = Color(0xFF7B2949), darkOnContainer = Color(0xFFFFD9E2),
        ),
        Palette(
            id = "paars", label = "Paars", swatch = Color(0xFF6750A4),
            lightPrimary = Color(0xFF6750A4), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFEADDFF), lightOnContainer = Color(0xFF21005D),
            darkPrimary = Color(0xFFD0BCFF), darkOnPrimary = Color(0xFF381E72),
            darkContainer = Color(0xFF4F378B), darkOnContainer = Color(0xFFEADDFF),
        ),
        Palette(
            id = "blauw", label = "Blauw", swatch = Color(0xFF0B57D0),
            lightPrimary = Color(0xFF0B57D0), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFD3E3FD), lightOnContainer = Color(0xFF041E49),
            darkPrimary = Color(0xFFA8C7FA), darkOnPrimary = Color(0xFF062E6F),
            darkContainer = Color(0xFF0842A0), darkOnContainer = Color(0xFFD3E3FD),
        ),
        Palette(
            id = "turquoise", label = "Turquoise", swatch = Color(0xFF006A6A),
            lightPrimary = Color(0xFF006A6A), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFF9CF1F0), lightOnContainer = Color(0xFF002020),
            darkPrimary = Color(0xFF4DDADA), darkOnPrimary = Color(0xFF003737),
            darkContainer = Color(0xFF004F4F), darkOnContainer = Color(0xFF9CF1F0),
        ),
        Palette(
            id = "groen", label = "Groen", swatch = Color(0xFF146C2E),
            lightPrimary = Color(0xFF146C2E), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFC4EED0), lightOnContainer = Color(0xFF072711),
            darkPrimary = Color(0xFF6DD58C), darkOnPrimary = Color(0xFF0A3818),
            darkContainer = Color(0xFF0F5223), darkOnContainer = Color(0xFFC4EED0),
        ),
        Palette(
            id = "amber", label = "Amber", swatch = Color(0xFF7D5700),
            lightPrimary = Color(0xFF7D5700), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFFFDEA0), lightOnContainer = Color(0xFF271900),
            darkPrimary = Color(0xFFFFB951), darkOnPrimary = Color(0xFF422C00),
            darkContainer = Color(0xFF5E4100), darkOnContainer = Color(0xFFFFDEA0),
        ),
        Palette(
            id = "rood", label = "Rood", swatch = Color(0xFFB3261E),
            lightPrimary = Color(0xFFB3261E), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFF9DEDC), lightOnContainer = Color(0xFF410E0B),
            darkPrimary = Color(0xFFF2B8B5), darkOnPrimary = Color(0xFF601410),
            darkContainer = Color(0xFF8C1D18), darkOnContainer = Color(0xFFF9DEDC),
        ),
        Palette(
            id = "neutraal", label = "Neutraal", swatch = Color(0xFF4A5C92),
            lightPrimary = Color(0xFF4A5C92), lightOnPrimary = Color(0xFFFFFFFF),
            lightContainer = Color(0xFFDBE1FF), lightOnContainer = Color(0xFF001944),
            darkPrimary = Color(0xFFB4C5FF), darkOnPrimary = Color(0xFF1B2E60),
            darkContainer = Color(0xFF32447A), darkOnContainer = Color(0xFFDBE1FF),
        ),
    )

    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * Secondary and tertiary follow the primary hue instead of Material's defaults, so the whole
     * app stays in the chosen colour rather than mixing in a stray purple.
     */
    fun schemeFor(palette: Palette, dark: Boolean, highContrast: Boolean): ColorScheme {
        val scheme = if (dark) {
            darkColorScheme(
                primary = palette.darkPrimary,
                onPrimary = palette.darkOnPrimary,
                primaryContainer = palette.darkContainer,
                onPrimaryContainer = palette.darkOnContainer,
                secondary = palette.darkPrimary,
                onSecondary = palette.darkOnPrimary,
                secondaryContainer = palette.darkContainer,
                onSecondaryContainer = palette.darkOnContainer,
                tertiary = palette.darkPrimary,
                onTertiary = palette.darkOnPrimary,
                tertiaryContainer = palette.darkContainer,
                onTertiaryContainer = palette.darkOnContainer,
            )
        } else {
            lightColorScheme(
                primary = palette.lightPrimary,
                onPrimary = palette.lightOnPrimary,
                primaryContainer = palette.lightContainer,
                onPrimaryContainer = palette.lightOnContainer,
                secondary = palette.lightPrimary,
                onSecondary = palette.lightOnPrimary,
                secondaryContainer = palette.lightContainer,
                onSecondaryContainer = palette.lightOnContainer,
                tertiary = palette.lightPrimary,
                onTertiary = palette.lightOnPrimary,
                tertiaryContainer = palette.lightContainer,
                onTertiaryContainer = palette.lightOnContainer,
            )
        }
        return if (highContrast) scheme.toHighContrast(dark) else scheme
    }

    /**
     * Pushes backgrounds to pure black/white and text to the opposite extreme, and darkens the
     * outlines. Aimed at low vision rather than at looking pretty.
     */
    private fun ColorScheme.toHighContrast(dark: Boolean): ColorScheme {
        val background = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)
        val onBackground = if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)
        val surfaceVariant = if (dark) Color(0xFF1A1A1A) else Color(0xFFEDEDED)
        return copy(
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onBackground,
            surfaceContainer = surfaceVariant,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceVariant,
            surfaceContainerLow = background,
            surfaceContainerLowest = background,
            outline = onBackground,
            outlineVariant = onBackground.copy(alpha = 0.6f),
        )
    }
}
