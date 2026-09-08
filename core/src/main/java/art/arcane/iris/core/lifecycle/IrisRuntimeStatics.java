package art.arcane.iris.core.lifecycle;

import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.runtime.RuntimeInjection;
import art.arcane.iris.core.runtime.WorldRuntimeControlService;
import art.arcane.iris.spi.IrisServices;

/**
 * Drops every process-wide cache core keeps about the running server, so a disable leaves nothing an
 * enable in the same JVM could answer from.
 * <p>
 * The capability singletons are the reason this exists: each holds reflective handles onto the live
 * CraftServer and MinecraftServer, and a boot that reused them would be talking to the previous server's
 * objects. The platform binding itself is not cleared here - core still logs through it while the adapter
 * finishes its own teardown - and is unbound last by the adapter.
 */
public final class IrisRuntimeStatics {
    private IrisRuntimeStatics() {
    }

    public static void reset() {
        IrisServices.clear();
        WorldLifecycleService.reset();
        WorldRuntimeControlService.reset();
        RuntimeInjection.reset();
        ServerConfigurator.resetLoadedDatapackRuntime();
        IrisLanguage.shutdown();
    }
}
