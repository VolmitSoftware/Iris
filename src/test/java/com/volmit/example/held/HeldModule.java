package com.volmit.example.held;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;

/** A built-in module whose settings class is held by a class of the host, not by a feature. */
public final class HeldModule implements IrisModule {
    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:held", "Held", "test");
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.registry().registerHeldType(ClampSettings.class);
        setup.registry().registerHeldType(ClampSettings.class);
    }
}
