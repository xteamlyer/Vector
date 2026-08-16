package org.matrix.vector.ui.store

/**
 * Where a store-driven module install has got to. A plain state type with no Android dependencies, so
 * the shared Details screen can render an install bar without knowing which app's installer produced
 * it. An app with no install capability never emits anything but [Idle].
 */
sealed interface InstallStep {
    data object Idle : InstallStep

    data class Downloading(val packageName: String, val bytes: Long, val total: Long) : InstallStep

    data class Installing(val packageName: String) : InstallStep

    data class Confirming(val packageName: String) : InstallStep

    data class Done(val packageName: String) : InstallStep

    data class Failed(val packageName: String, val reason: String?) : InstallStep
}
