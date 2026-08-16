package org.matrix.vector.ui.appearance

import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared [AppearanceSheet] reads and writes, so it can edit either app's look without
 * reaching into either app's preference store.
 *
 * Each choice is a [StateFlow] the sheet observes plus a setter it calls; every host already keeps
 * these as flows (Vector in its settings repository, LSPatch in its preferences), so binding one is
 * only a matter of implementing this.
 */
interface AppearanceSettings {
    val themeMode: StateFlow<String>
    val dynamicColor: StateFlow<Boolean>
    val seedColor: StateFlow<Int>
    val amoledBlack: StateFlow<Boolean>
    val headerAmbience: StateFlow<String>

    fun setThemeMode(value: String)

    fun setDynamicColor(value: Boolean)

    fun setSeedColor(value: Int)

    fun setAmoledBlack(value: Boolean)

    fun setHeaderAmbience(key: String)
}
