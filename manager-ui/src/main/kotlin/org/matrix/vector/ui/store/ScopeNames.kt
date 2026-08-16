package org.matrix.vector.ui.store

/**
 * A legacy module's declared scope, spelled the way everything else here spells it.
 *
 * Legacy modules name the system server the other way round: their "android" is the daemon's
 * "system", and their "system" is the ordinary "android" package. XposedBridge reported
 * `packageName` as "system" for the system dialogues so that a module testing for "android" found
 * system_server alone, and the scope vocabulary grew up around that; LSPosed later made "system" the
 * system server and left "android" as the real package, which is what every modern module and the
 * whole of the daemon mean by the two words today. The convention is universal among legacy modules,
 * so the swap is unconditional for them.
 *
 * A pure String mapping with no app dependencies, kept here so the shared Details screen can put a
 * catalogue's legacy-vocabulary scope on screen beside the installed copy's already-swapped list
 * without either app's module detection. (Vector's `ModuleDetection` keeps its own copy for the APK
 * read; this one is the store's.)
 */
fun swapLegacyFrameworkNames(scope: List<String>): List<String> =
    scope.map {
        when (it) {
            "android" -> "system"
            "system" -> "android"
            else -> it
        }
    }
