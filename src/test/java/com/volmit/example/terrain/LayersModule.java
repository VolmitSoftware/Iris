package com.volmit.example.terrain;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;

/** A built-in module whose settings use host types; it only registers them. */
public final class LayersModule implements IrisModule {
    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:layers", "Layers", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.registry().registerSettings(LayersSettings.class);
    }
}
