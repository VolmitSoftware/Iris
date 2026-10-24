package com.volmit.host;

import com.volmit.FeatureRegistry;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.stage.StageGraph;
import java.util.Objects;

/** Runs a module's {@link com.volmit.IrisModule#setup} against one pack load's stage graph and registry. While it
 * runs, the graph accepts the module's stages and the registry its registrations, both scoped to the module; after
 * it returns, every stage the module defined must have a body.
 */
public final class ModuleSetups {
    private ModuleSetups() {}

    public static void run(DiscoveredModule discovered, StageGraphBuilder stages, ModuleRegistry registry) {
        Objects.requireNonNull(discovered, "discovered");
        Objects.requireNonNull(stages, "stages");
        Objects.requireNonNull(registry, "registry");
        stages.beginSetup(discovered);
        registry.beginSetup(discovered);
        boolean completed = false;
        try {
            discovered.module().setup(new Setup(stages, registry, discovered.log()));
            completed = true;
        } finally {
            registry.endSetup();
            stages.endSetup(completed);
        }
    }

    private record Setup(StageGraph stages, FeatureRegistry registry, ModuleLog log) implements ModuleSetup {}
}
