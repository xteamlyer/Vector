package org.matrix.vector.ui.ambience

/**
 * How an [AmbientSurface] persists per-kind scale / speed / variant. Injected so the library carries
 * no dependency on any app's settings store: an app that persists wires its own implementation; one
 * that does not uses [Ephemeral].
 */
interface AmbienceSettings {
    fun scale(key: String): Float

    fun speed(key: String): Float

    fun variant(key: String): Int

    fun setScale(key: String, value: Float)

    fun setSpeed(key: String, value: Float)

    fun setVariant(key: String, value: Int)

    /** No persistence: resting defaults, and gesture changes last only the surface's lifetime. */
    object Ephemeral : AmbienceSettings {
        override fun scale(key: String) = 1f

        override fun speed(key: String) = 1f

        override fun variant(key: String) = 0

        override fun setScale(key: String, value: Float) {}

        override fun setSpeed(key: String, value: Float) {}

        override fun setVariant(key: String, value: Int) {}
    }
}
