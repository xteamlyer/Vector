package org.matrix.vector.manager.ui.navigation

import kotlinx.coroutines.flow.StateFlow
import org.matrix.vector.manager.di.ServiceLocator
import org.matrix.vector.ui.navigation.FloatingNavSettings
import org.matrix.vector.ui.navigation.NavPanelStore

/**
 * Vector's side of the shared navigation container's two ports.
 *
 * The container is drawn by the shared library, which knows nothing of this app's settings; these
 * hand it the two things it has to persist. Read through [ServiceLocator] on every call rather than
 * captured, since these are objects and the locator is attached when the activity starts.
 */
object VectorNavPanelStore : NavPanelStore {

    override val encoded: StateFlow<String>
        get() = ServiceLocator.settings.navPanels

    override fun setEncoded(value: String) {
        ServiceLocator.settings.setNavPanels(value)
    }
}

/** Where the floating ball rests, persisted so a rotation or a fold puts it back. */
object VectorFloatingNavSettings : FloatingNavSettings {

    override fun atEnd(): Boolean = ServiceLocator.settings.floatingNavAtEnd()

    override fun y(): Float = ServiceLocator.settings.floatingNavY()

    override fun setAtEnd(atEnd: Boolean) {
        ServiceLocator.settings.setFloatingNavAtEnd(atEnd)
    }

    override fun setY(fraction: Float) {
        ServiceLocator.settings.setFloatingNavY(fraction)
    }
}
