package art.arcane.iris.world.lifecycle;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.world.runtime.RuntimeInjection;
import art.arcane.iris.world.runtime.WorldRuntimeControlService;
import art.arcane.iris.spi.IrisServices;

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
