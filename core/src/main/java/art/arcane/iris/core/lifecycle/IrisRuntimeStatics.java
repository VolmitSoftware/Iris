package art.arcane.iris.core.lifecycle;

import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.runtime.WorldRuntimeControlService;
import art.arcane.iris.spi.IrisServices;

/**
 * Drops every process-wide cache core keeps about the running server, so a disable leaves nothing an
 * enable in the same JVM could answer from. The host bindings are not cleared here: core still logs
 * through them while the adapter finishes its own teardown.
 */
public final class IrisRuntimeStatics {
    private IrisRuntimeStatics() {
    }

    public static void reset() {
        IrisServices.clear();
        WorldLifecycleService.reset();
        WorldRuntimeControlService.reset();
        ServerConfigurator.resetLoadedDatapackRuntime();
        IrisLanguage.shutdown();
    }
}
