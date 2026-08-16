package org.matrix.vector.ui.navigation

/**
 * Where the floating nav ball rests, injected so the shared [FloatingPanelNav] does not reach into
 * either app's settings store.
 *
 * The ball parks against one side of the window, a fraction of the way down, and that position is
 * persisted so a rotation or a fold puts it back where it was left. A host that persists these
 * (Vector, through its settings repository; LSPatch, through its preferences) provides an
 * implementation; one that does not lets the ephemeral default hold the position for the process.
 *
 * Deliberately plain getters and setters, not flows: the ball's position is read once when the arc
 * opens and written once on release, and observing it would recompose the ball every time it moved.
 */
interface FloatingNavSettings {

    /** Whether the ball parks against the end (right in LTR) rather than the start edge. */
    fun atEnd(): Boolean

    /** How far down the window the ball rests, 0 (top) to 1 (bottom). */
    fun y(): Float

    fun setAtEnd(atEnd: Boolean)

    fun setY(fraction: Float)

    /** Holds the position for the process only. */
    object Ephemeral : FloatingNavSettings {
        private var end = true
        private var fraction = 0.72f

        override fun atEnd(): Boolean = end

        override fun y(): Float = fraction

        override fun setAtEnd(atEnd: Boolean) {
            end = atEnd
        }

        override fun setY(fraction: Float) {
            this.fraction = fraction
        }
    }
}
