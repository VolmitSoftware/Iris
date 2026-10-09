package com.volmit.example.held;

import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.StageGraph;

/** A built-in module whose settings class is held by a class of the host, not by a feature. */
public final class HeldModule implements IrisModule {
    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:held", "Held", "test");
    }

    @Override
    public void install(StageGraph graph) {}

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(ClampSettings.class);
        registry.registerHeldType(ClampSettings.class);
    }
}
