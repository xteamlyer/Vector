package org.matrix.vector.manager.ui.components

import org.matrix.vector.manager.R
import org.matrix.vector.ui.StatusTone

/** The four states the framework can be in, plus the moment before we know. */
enum class FrameworkState {
    Checking,
    Active,
    Degraded,
    Inactive,

    /**
     * The framework is running, and this manager cannot talk to it.
     *
     * Distinct from [Inactive], which means there is no framework. Here there is one, it pushed us a
     * binder, and that binder speaks a different generation of `IManagerService` — so every
     * transaction would fail and the honest thing to say is that the two builds are out of step, not
     * that nothing is installed. Reached only through `ServiceLocator.peerDescriptor`.
     */
    Mismatched,
}

/** How each framework state paints the shared [org.matrix.vector.ui.StatusHeader]. */
fun FrameworkState.toTone(): StatusTone =
    when (this) {
        FrameworkState.Active -> StatusTone.Active
        FrameworkState.Degraded -> StatusTone.Warning
        FrameworkState.Inactive,
        FrameworkState.Mismatched -> StatusTone.Error
        FrameworkState.Checking -> StatusTone.Neutral
    }

/** The word the header shows beside the brand for each state. */
fun FrameworkState.statusWordRes(): Int =
    when (this) {
        FrameworkState.Active -> R.string.status_active
        FrameworkState.Degraded -> R.string.status_degraded
        FrameworkState.Inactive -> R.string.status_inactive
        FrameworkState.Mismatched -> R.string.status_mismatched
        FrameworkState.Checking -> R.string.status_checking
    }
