package org.matrix.vector.ui.appearance

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.matrix.vector.ui.ChoiceRow
import org.matrix.vector.ui.ColorWheel
import org.matrix.vector.ui.LocalDialogLocalizer
import org.matrix.vector.ui.R
import org.matrix.vector.ui.SheetHeading
import org.matrix.vector.ui.ToggleRow
import org.matrix.vector.ui.ambience.AmbienceKind
import org.matrix.vector.ui.theme.SeedScheme
import org.matrix.vector.ui.theme.ThemeMode

/**
 * The look of the app, edited in a sheet: theme mode, accent colour (wallpaper / seed / wheel),
 * true-black for OLED, and the header ambience. Every choice takes effect immediately behind the
 * sheet, which is the point of a sheet rather than a screen — change the surface or the theme and
 * you watch it happen without losing your place.
 *
 * Shared: a host supplies its [AppearanceSettings] and this is the whole appearance surface it
 * needs. A host with more to offer wraps this and adds its own sections around it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    settings: AppearanceSettings,
    onDismiss: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val amoled by settings.amoledBlack.collectAsStateWithLifecycle()
    val seed by settings.seedColor.collectAsStateWithLifecycle()
    val ambience by settings.headerAmbience.collectAsStateWithLifecycle()
    val resolvedDark =
        when (ThemeMode.from(themeMode)) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LocalDialogLocalizer.current {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                SheetHeading(stringResource(R.string.appearance_theme), Icons.Rounded.Palette)
                BrightnessSelector(
                    selected = ThemeMode.from(themeMode),
                    onSelect = { settings.setThemeMode(it.key) },
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                SheetHeading(stringResource(R.string.appearance_color), Icons.Rounded.Colorize)
                ColorSection(
                    dynamicColor = dynamicColor,
                    seed = seed,
                    dark = resolvedDark,
                    onDynamic = settings::setDynamicColor,
                    onSeed = settings::setSeedColor,
                )
                ToggleRow(
                    title = stringResource(R.string.appearance_amoled),
                    icon = Icons.Rounded.DarkMode,
                    subtitle = stringResource(R.string.appearance_amoled_summary),
                    checked = amoled,
                    onCheckedChange = settings::setAmoledBlack,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                SheetHeading(stringResource(R.string.settings_ambience), Icons.Rounded.Waves)
                ChoiceRow {
                    AmbienceKind.entries.forEach { kind ->
                        FilterChip(
                            selected = AmbienceKind.from(ambience) == kind,
                            onClick = { settings.setHeaderAmbience(kind.key) },
                            label = { Text(stringResource(kind.labelRes())) },
                        )
                    }
                }

                // A host-supplied tail — LSPatch hangs its floating-navigation toggle here — so a
                // consumer can add its own settings without the sheet knowing what they are.
                extra?.let {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    it()
                }
            }
        }
    }
}

/** Light, dark, or whatever the system says — a segmented row because the three cover the choice. */
@Composable
private fun BrightnessSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape =
                    SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                icon = {},
                label = {
                    Icon(
                        mode.icon(),
                        contentDescription = stringResource(mode.labelRes()),
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

/** Where the accent comes from: wallpaper, a preset seed, or the wheel — alternatives to each other. */
@Composable
private fun ColorSection(
    dynamicColor: Boolean,
    seed: Int,
    dark: Boolean,
    onDynamic: (Boolean) -> Unit,
    onSeed: (Int) -> Unit,
) {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var wheelOpen by remember { mutableStateOf(false) }
    val custom = !dynamicColor && seed !in SeedScheme.PRESETS

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (supportsDynamic) {
            SourceSwatch(
                selected = dynamicColor,
                onClick = {
                    onDynamic(true)
                    wheelOpen = false
                },
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.appearance_dynamic_color),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SeedScheme.PRESETS.forEach { preset ->
            val presetScheme = remember(preset, dark) { SeedScheme.of(preset, dark) }
            SourceSwatch(
                selected = !dynamicColor && seed == preset,
                fill = presetScheme.primary,
                onClick = {
                    onDynamic(false)
                    onSeed(preset)
                    wheelOpen = false
                },
            ) {}
        }

        SourceSwatch(
            selected = custom,
            fill = if (custom) MaterialTheme.colorScheme.primary else null,
            onClick = {
                onDynamic(false)
                wheelOpen = !wheelOpen
            },
        ) {
            if (!custom) {
                Icon(
                    Icons.Rounded.Colorize,
                    contentDescription = stringResource(R.string.appearance_custom_color),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    AnimatedVisibility(visible = wheelOpen && !dynamicColor) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (chroma, hue) = remember(seed) { with(SeedScheme) { Color(seed).toWheel() } }
            ColorWheel(
                hue = hue,
                chroma = chroma,
                dark = dark,
                onChange = { h, c -> onSeed(SeedScheme.wheelColor(h, c).toArgb()) },
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(0.72f),
            )
            Text(
                text = with(SeedScheme) { Color(seed).toHex() },
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }

    TonalPreview(dynamicColor = dynamicColor, seed = seed, dark = dark)
}

/** One choosable colour source: a filled circle that gains a ring when it is the live one. */
@Composable
private fun SourceSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    fill: Color? = null,
    content: @Composable () -> Unit,
) {
    val ring by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "swatch ring",
        )
    Box(
        modifier =
            Modifier.size(44.dp)
                .border(width = 2.dp, color = ring, shape = CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(fill ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint =
                    if (fill != null) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The tones the current source produces, light to dark — the values the scheme actually hands out. */
@Composable
private fun TonalPreview(dynamicColor: Boolean, seed: Int, dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val swatches =
        if (dynamicColor) {
            listOf(
                scheme.primary,
                scheme.onPrimaryContainer,
                scheme.primaryContainer,
                scheme.secondary,
                scheme.secondaryContainer,
                scheme.tertiary,
                scheme.tertiaryContainer,
                scheme.surfaceContainerHighest,
                scheme.surfaceContainer,
                scheme.surface,
            )
        } else {
            remember(seed, dark) {
                val ramp = SeedScheme.Ramp(with(SeedScheme) { Color(seed).toWheel().second }, 48f)
                SeedScheme.PREVIEW_TONES.map { ramp[it] }
            }
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        swatches.forEachIndexed { index, colour ->
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(28.dp)
                        .clip(
                            when (index) {
                                0 -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                                swatches.lastIndex ->
                                    RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                                else -> RectangleShape
                            }
                        )
                        .background(colour)
            )
        }
    }
}

private fun ThemeMode.icon() =
    when (this) {
        ThemeMode.System -> Icons.Rounded.BrightnessAuto
        ThemeMode.Light -> Icons.Rounded.LightMode
        ThemeMode.Dark -> Icons.Rounded.DarkMode
    }

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.System -> R.string.appearance_theme_system
        ThemeMode.Light -> R.string.appearance_theme_light
        ThemeMode.Dark -> R.string.appearance_theme_dark
    }

private fun AmbienceKind.labelRes(): Int =
    when (this) {
        AmbienceKind.Snow -> R.string.ambience_snow
        AmbienceKind.Maze -> R.string.ambience_maze
        AmbienceKind.Circuit -> R.string.ambience_circuit
        AmbienceKind.Matrix -> R.string.ambience_matrix
        AmbienceKind.None -> R.string.ambience_none
    }
