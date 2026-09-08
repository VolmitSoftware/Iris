package art.arcane.iris.core.lifecycle;

import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.runtime.RuntimeInjection;
import art.arcane.iris.core.runtime.WorldRuntimeControlService;
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
