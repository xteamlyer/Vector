package org.matrix.vector.ui.logs

import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Names the process behind a uid, for a host to answer [LogSource.writerLabel] with.
 *
 * Both hosts read logs written by installed packages, so both need the same answer and neither should invent its own.
 * One package holding the uid is named by its label; a uid several packages share is named by the platform instead, so
 * that a chip is a name rather than a list.
 *
 * The platform only names what it has a package setting for, and the uids that matter most in a privileged daemon's log
 * have none: root writes as 0 and is named by nothing at all, while the system's shared id comes back spelled as the
 * setting that holds it. Those few are spelled out here, from the platform's own fixed assignments, so the log of a
 * daemon running as root does not read as a column of numbers. Anything still unnamed is left to the screen, which
 * shows the number -- and a number still separates one process from another.
 *
 * A uid outside the first user carries the user it belongs to, since the same application in a second profile is a
 * different writer with the same label and would otherwise appear twice with nothing to tell the two apart.
 */
class WriterLabeler(private val packageManager: PackageManager) {

    /**
     * Answers and refusals alike, because both cost the same to find out.
     *
     * Names are asked once per scan rather than once per frame, but a scan happens on every filter change and on every
     * pause in typing, and a device-wide log carries dozens of uids the platform will never name; re-asking for each of
     * them every time would be binder traffic that can only ever produce the same silence. The price is that a name
     * only learnable later -- an app installed while the screen is open, or renamed by an update -- waits for the next
     * process. That is the smaller cost of the two.
     */
    private val cache = ConcurrentHashMap<Int, String>()

    fun label(uid: Int): String? = cache.getOrPut(uid) { withUser(uid, resolve(uid)) }.takeIf { it.isNotEmpty() }

    private fun resolve(uid: Int): String {
        WELL_KNOWN[uid % PER_USER_RANGE]?.let {
            return it
        }
        val packages = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull().orEmpty()
        if (packages.size == 1) {
            val label =
                runCatching { packageManager.getApplicationInfo(packages[0], 0).loadLabel(packageManager).toString() }
                    .getOrNull()
            if (!label.isNullOrBlank()) return label
        }
        // The platform spells a shared id as the setting that holds it -- "android.uid.system:1000" --
        // whose number the chip already states beside the name, and whose prefix says nothing.
        return runCatching { packageManager.getNameForUid(uid) }
            .getOrNull()
            .orEmpty()
            .substringBefore(':')
            .removePrefix("android.uid.")
    }

    /** Marks which user a writer belongs to, for the profiles where the same label appears twice. */
    private fun withUser(uid: Int, name: String): String =
        if (name.isEmpty() || uid < PER_USER_RANGE) name else "$name · u${uid / PER_USER_RANGE}"

    private companion object {
        /** The platform's own spacing of uids per user; `UserHandle.PER_USER_RANGE` is not public. */
        const val PER_USER_RANGE = 100_000

        /**
         * The fixed assignments a reader actually meets in a log.
         *
         * Only the ones that write: a daemon running as root, the platform itself, the shell a rootless host reads
         * through, and the media and audio servers whose lines fill any device log. The rest of the platform's table is
         * of no use to a reader and is left to the platform to answer for.
         */
        val WELL_KNOWN: Map<Int, String> =
            mapOf(
                // Written out rather than read from android.os.Process, which exposes only some of
                // them; the assignments themselves are fixed and have never moved.
                0 to "root",
                1000 to "system",
                1001 to "radio",
                1013 to "media",
                1041 to "audioserver",
                2000 to "shell",
                9999 to "nobody",
            )
    }
}
