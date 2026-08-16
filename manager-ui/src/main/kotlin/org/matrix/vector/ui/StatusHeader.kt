package org.matrix.vector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.matrix.vector.ui.ambience.AmbienceKind
import org.matrix.vector.ui.ambience.AmbienceSettings
import org.matrix.vector.ui.ambience.AmbientSurface

/**
 * The four looks the status header can wear, decoupled from any one app's domain.
 *
 * A host maps its own state onto these: Vector's framework Active/Degraded/Inactive/Checking,
 * LSPatch's Shizuku granted/ungranted. Each drives the container colour, the badge's shape and
 * motion, and its glyph — so state is carried by shape and motion as well as hue, since under
 * Material You the hue is the wallpaper's to choose and colour alone can never be the signal.
 */
enum class StatusTone {
    /** Everything is running. Primary container, a rounded square, a slow breath, a tick. */
    Active,

    /** Running with a caveat. Tertiary container, a softer form, a caution mark. */
    Warning,

    /** Not running. Error container, a circle, a cross. */
    Error,

    /** The moment before it is known. Surface container, a circle, no glyph. */
    Neutral,
}

/**
 * The top-of-app status pane, with a living [AmbientSurface] behind it — the shared hero both
 * managers open on.
 *
 * There is no app bar above it and no separate status row: a bar naming the app spends a whole row
 * on what the launcher, the task switcher and the system already say, and that row goes instead to
 * the one thing genuinely unknown on opening the app. The pane is full-bleed and runs under the
 * status bar, tinted by [tone], with only its bottom corners rounded so it reads as one pane hanging
 * from the top edge.
 *
 * Everything a host differs on is a parameter: the [brand] and [statusWord], the [detail] line
 * (rendered by the host in the colour handed to it), and whether the badge opens anything
 * ([onOpenStatus] — null makes it a non-navigating indicator, which is how LSPatch shows Shizuku
 * state with no status page behind it). The appearance and language buttons appear only when their
 * callbacks are supplied.
 */
@Composable
fun StatusHeader(
    brand: String,
    statusWord: String,
    tone: StatusTone,
    ambience: AmbienceKind,
    modifier: Modifier = Modifier,
    ambienceSettings: AmbienceSettings = AmbienceSettings.Ephemeral,
    statusContentDescription: String = statusWord,
    hintStatus: Boolean = false,
    onOpenStatus: (() -> Unit)? = null,
    appearanceLabel: String? = null,
    onOpenAppearance: (() -> Unit)? = null,
    languageLabel: String? = null,
    onOpenLanguage: (() -> Unit)? = null,
    onBrandTap: (() -> Unit)? = null,
    detail: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    val container by
        animateColorAsState(
            when (tone) {
                StatusTone.Active -> colors.primaryContainer
                StatusTone.Warning -> colors.tertiaryContainer
                StatusTone.Error -> colors.errorContainer
                StatusTone.Neutral -> colors.surfaceContainer
            },
            animationSpec = tween(420),
            label = "headerContainer",
        )
    val onContainer by
        animateColorAsState(
            when (tone) {
                StatusTone.Active -> colors.onPrimaryContainer
                StatusTone.Warning -> colors.onTertiaryContainer
                StatusTone.Error -> colors.onErrorContainer
                StatusTone.Neutral -> colors.onSurfaceVariant
            },
            animationSpec = tween(420),
            label = "headerOnContainer",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // Square at the top so it meets the screen edge, rounded at the bottom so it reads
                // as one pane hanging from it.
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(container, container.copy(alpha = 0.82f).compositeOverSurface())
                    )
                )
    ) {
        // matchParentSize, NOT fillMaxSize: a Box child that fills its maximum constraint drags the
        // Box to full height with it; matchParentSize sizes to whatever the content settled on.
        AmbientSurface(
            kind = ambience,
            tint = onContainer,
            modifier = Modifier.matchParentSize(),
            settings = ambienceSettings,
        )

        Column(
            modifier =
                Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 20.dp)
        ) {
            // The ambient surface gets the upper part of the pane to itself; the status settles at
            // the bottom, where it sits on the surface rather than floating above a gap.
            Spacer(Modifier.height(66.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.height(HEADLINE_ROW), contentAlignment = Alignment.Center) {
                    StatusIndicator(
                        tone = tone,
                        tint = onContainer,
                        hint = hintStatus,
                        onClick = onOpenStatus,
                        contentDescription = statusContentDescription,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                            val brandModifier =
                                if (onBrandTap == null) Modifier
                                else
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onBrandTap,
                                    )
                            Text(
                                text = brand,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Normal,
                                color = onContainer.copy(alpha = 0.62f),
                                modifier = brandModifier,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = statusWord,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onContainer,
                            )
                        }
                        // Neither is a gear. What they open governs how the app presents itself —
                        // its colours and its language — rather than what it does. Shown only when
                        // the host wires them.
                        if (onOpenAppearance != null || onOpenLanguage != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (onOpenAppearance != null) {
                                    IconButton(
                                        onClick = onOpenAppearance,
                                        modifier = Modifier.size(ICON_BUTTON),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Palette,
                                            contentDescription = appearanceLabel,
                                            tint = onContainer,
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                }
                                if (onOpenLanguage != null) {
                                    IconButton(
                                        onClick = onOpenLanguage,
                                        modifier = Modifier.size(ICON_BUTTON),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Language,
                                            contentDescription = languageLabel,
                                            tint = onContainer,
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (detail != null) {
                        Spacer(Modifier.height(2.dp))
                        // The full content colour, so a host that marks part of the line (an update
                        // dot) can use it and dim the rest itself.
                        detail(onContainer)
                    }
                }
            }
        }
    }
}

/**
 * The status badge: a shape that changes with [tone], a breath while Active, and — only when it is a
 * door to somewhere ([onClick] non-null) and [hint] is set — a periodic turn into a gear, the one
 * symbol everyone reads as "there are settings here". When [onClick] is null it is a pure indicator:
 * no ripple, no gear, nothing to press.
 */
@Composable
private fun StatusIndicator(
    tone: StatusTone,
    tint: Color,
    hint: Boolean,
    onClick: (() -> Unit)?,
    contentDescription: String,
) {
    val corner by
        animateFloatAsState(
            when (tone) {
                StatusTone.Active -> 34f
                StatusTone.Warning -> 42f
                else -> 50f
            },
            animationSpec = tween(420),
            label = "indicatorCorner",
        )

    val breathing = rememberInfiniteTransition(label = "indicatorBreath")
    val pulse by
        breathing.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
            label = "indicatorPulse",
        )

    val icon =
        when (tone) {
            StatusTone.Active -> Icons.Rounded.Check
            StatusTone.Warning -> Icons.Rounded.PriorityHigh
            StatusTone.Error -> Icons.Rounded.Close
            StatusTone.Neutral -> null
        }

    val hinting = hint && onClick != null && tone == StatusTone.Active
    var asGear by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }

    LaunchedEffect(hinting) {
        if (!hinting) {
            asGear = false
            return@LaunchedEffect
        }
        while (true) {
            delay(HINT_PERIOD_MS)
            asGear = true
            repeat(HINT_TURNS) {
                if (Random.nextBoolean()) {
                    spin.animateTo(
                        spin.value + FULL_TURN,
                        animationSpec = tween(HINT_TURN_MS, easing = LinearEasing),
                    )
                } else {
                    delay(HINT_TURN_MS.toLong())
                }
            }
            asGear = false
            spin.snapTo(spin.value.mod(FULL_TURN))
        }
    }

    val morph by
        animateFloatAsState(if (asGear) 1f else 0f, tween(MORPH_MS), label = "indicatorMorph")

    val base =
        Modifier.size(52.dp)
            .scale(if (tone == StatusTone.Active) pulse else 1f)
            .clip(RoundedCornerShape(percent = corner.toInt()))
            .background(tint.copy(alpha = lerp(RESTING_FILL, HINTING_FILL, morph)))
    Box(
        modifier =
            (if (onClick == null) base else base.clickable(onClick = onClick)).semantics {
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier =
                    Modifier.size(26.dp).graphicsLayer {
                        alpha = 1f - morph
                        val leaving = lerp(1f, 0.6f, morph)
                        scaleX = leaving
                        scaleY = leaving
                        rotationZ = -MORPH_TURN * morph
                    },
            )
        }
        if (tone == StatusTone.Active && onClick != null) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = null,
                tint = tint,
                modifier =
                    Modifier.size(26.dp).graphicsLayer {
                        alpha = morph
                        val arriving = lerp(0.6f, 1f, morph)
                        scaleX = arriving
                        scaleY = arriving
                        rotationZ = spin.value
                    },
            )
        }
    }
}

/** Keeps the gradient's lower stop opaque; a translucent stop would show the list scrolling under. */
@Composable
private fun Color.compositeOverSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    return Color(
        red = red * alpha + surface.red * (1 - alpha),
        green = green * alpha + surface.green * (1 - alpha),
        blue = blue * alpha + surface.blue * (1 - alpha),
        alpha = 1f,
    )
}

/** One of the two stacked buttons beside the wordmark. */
private val ICON_BUTTON = 38.dp

private const val HINT_PERIOD_MS = 30_000L
private const val HINT_TURN_MS = 2_000
private const val HINT_TURNS = 5
private const val FULL_TURN = 360f
private const val MORPH_MS = 420
private const val MORPH_TURN = 60f
private const val RESTING_FILL = 0.15f
private const val HINTING_FILL = 0.24f

/** The height of the row the wordmark shares with those buttons; the badge is centred against it. */
private val HEADLINE_ROW = ICON_BUTTON * 2
