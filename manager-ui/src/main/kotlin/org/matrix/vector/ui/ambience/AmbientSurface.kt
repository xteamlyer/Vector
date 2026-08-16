package org.matrix.vector.ui.ambience

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.android.awaitFrame

/**
 * The header's living background.
 *
 * Draws whichever [AmbienceRenderer] is selected and hands it taps. It sits behind the header's
 * content, so the controls above keep working normally — only the open space responds. The frame
 * loop parks itself whenever the renderer reports nothing moving, and [AmbienceKind.None] skips the
 * loop entirely.
 *
 * Persistence of scale/speed/variant is injected via [settings]; the default keeps no state.
 */
@Composable
fun AmbientSurface(
    kind: AmbienceKind,
    tint: Color,
    modifier: Modifier = Modifier,
    settings: AmbienceSettings = AmbienceSettings.Ephemeral,
) {
    val renderer =
        remember(kind) {
            rendererFor(kind)?.apply {
                scale = settings.scale(kind.key)
                speed = settings.speed(kind.key)
                variant = settings.variant(kind.key)
            }
        } ?: return
    val haptics = LocalHapticFeedback.current

    var frame by remember(kind) { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(kind) {
        var last = 0L
        while (true) {
            if (!renderer.isAnimating) {
                awaitFrame()
                last = 0L
                continue
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
                last = now
                renderer.update(dt.coerceAtMost(64f), canvasSize)
                frame += 1f
            }
        }
    }

    val measurer = rememberTextMeasurer()
    (renderer as? MatrixRenderer)?.textMeasurer = measurer

    Canvas(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .pointerInput(kind) {
                    detectTapGestures(
                        onDoubleTap =
                            if (!renderer.hasVariants) null
                            else {
                                {
                                    renderer.onDoubleTap()
                                    settings.setVariant(kind.key, renderer.variant)
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                            },
                        onTap = { offset ->
                            renderer.onTap(offset, size.toSize())
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        },
                        onLongPress = { offset ->
                            renderer.onLongPress(offset, size.toSize())
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onPress = {
                            tryAwaitRelease()
                            renderer.onRelease()
                        },
                    )
                }
                .pointerInput(kind) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, gestureZoom, _ ->
                        if (gestureZoom != 1f) {
                            renderer.scale *= gestureZoom
                            settings.setScale(kind.key, renderer.scale)
                        }
                        if (pan != Offset.Zero) {
                            renderer.onDrag(pan, centroid, size.toSize())
                            settings.setSpeed(kind.key, renderer.speed)
                        }
                    }
                }
    ) {
        canvasSize = size
        @Suppress("UNUSED_EXPRESSION") frame
        with(renderer) { render(tint) }
    }
}
