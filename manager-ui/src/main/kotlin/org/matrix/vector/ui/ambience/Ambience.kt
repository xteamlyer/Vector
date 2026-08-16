package org.matrix.vector.ui.ambience

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * What a header's open space is doing. The kind carries only a stable [key]; the display label is a
 * per-app concern (each app maps the key to its own localized string), keeping this library free of
 * any app's resources.
 */
enum class AmbienceKind(val key: String) {
    /** Snowfall. Tap a flake to burst it; tap empty space and one grows there. */
    Snow("snow"),
    /** A carved maze with one wanderer in it. Tap to move it, swipe for a new maze. */
    Maze("maze"),
    /** Signal traces carrying several pulses. Tap to fire one, swipe to re-route, up/down for speed. */
    Circuit("circuit"),
    /** Falling code. Hold to stop the rain and pick a glyph out of it; pinch to go deeper. */
    Matrix("matrix"),
    None("none");

    companion object {
        fun from(key: String?): AmbienceKind = entries.firstOrNull { it.key == key } ?: Maze
    }
}

/**
 * A self-contained little simulation. Deliberately mutable and frame-driven rather than built from
 * Compose animations: these have dozens of independent particles with their own lifetimes, which
 * `animate*AsState` models badly. The renderer owns its state, the surface owns the clock.
 */
interface AmbienceRenderer {
    /** [dt] is milliseconds since the previous frame. */
    fun update(dt: Float, size: Size)

    fun DrawScope.render(tint: Color)

    fun onTap(position: Offset, size: Size)

    /** A press held down; the surface may freeze, grab something, or both. */
    fun onLongPress(position: Offset, size: Size) {}

    /** The held press ended. */
    fun onRelease() {}

    /** A drag, reported as the movement since the previous event. */
    fun onDrag(pan: Offset, at: Offset, size: Size) {}

    /** How large this render draws itself, as a multiple of its resting size. */
    var scale: Float

    /** How fast it moves, as a multiple of its resting speed. */
    var speed: Float
        get() = 1f
        set(_) {}

    /** Which of the render's own variations it is drawing, cycled by a double tap. */
    var variant: Int
        get() = 0
        set(_) {}

    /** A double tap. Distinct from [onTap], which seeds rather than switches. */
    fun onDoubleTap() {}

    /** Whether a double tap means anything here (listening for one delays every single tap). */
    val hasVariants: Boolean
        get() = false

    /** False when nothing is moving, letting the surface park the frame loop. */
    val isAnimating: Boolean
}

fun rendererFor(kind: AmbienceKind): AmbienceRenderer? =
    when (kind) {
        AmbienceKind.Snow -> SnowRenderer()
        AmbienceKind.Maze -> MazeRenderer()
        AmbienceKind.Circuit -> CircuitRenderer()
        AmbienceKind.Matrix -> MatrixRenderer()
        AmbienceKind.None -> null
    }
