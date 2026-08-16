package org.matrix.vector;

import org.matrix.vector.ipc.IFrameworkService;
import org.matrix.vector.util.Utils;
import org.matrix.vector.impl.core.VectorStartup;
import org.matrix.vector.impl.di.VectorBootstrap;
import org.matrix.vector.legacy.LegacyDelegateImpl;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedInit;

public class Startup {

    public static void bootstrapXposed(boolean systemServerStarted) {
        try {
            VectorStartup.bootstrap(XposedInit.startsSystemServer, systemServerStarted);
            XposedInit.loadLegacyModules();
        } catch (Throwable t) {
            Utils.logE("Error during framework initialization", t);
        }
    }

    /**
     * Registers a pre-built {@code LoadedApk} (the LSPatch rootless target) with the modern hooks so
     * its package lifecycle is dispatched when its class loader is realized. See
     * {@link VectorStartup#trackLoadedApk(Object)}.
     */
    public static void trackLoadedApk(Object loadedApk) {
        VectorStartup.trackLoadedApk(loadedApk);
    }

    public static void initXposed(boolean isSystem, String processName, String appDir, IFrameworkService service) {
        // Establish the Dependency Injection contract
        VectorBootstrap.INSTANCE.init(new LegacyDelegateImpl());

        // Initialize legacy resources and state
        XposedBridge.initXResources();
        XposedInit.startsSystemServer = isSystem;

        // Hand off execution to the modern framework initialization
        VectorStartup.init(isSystem, processName, appDir, service);
    }
}
