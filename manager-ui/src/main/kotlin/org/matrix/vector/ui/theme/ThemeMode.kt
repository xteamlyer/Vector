package org.matrix.vector.ui.theme

/** How an app resolves light vs dark. Persisted under the string keys used here. */
enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}
