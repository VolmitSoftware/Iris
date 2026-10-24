package com.volmit.support;

import com.volmit.IrisModule;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ModuleRegistry;
import com.volmit.host.ModuleSetups;
import com.volmit.host.StageGraphBuilder;

/** Sets modules up into a stage graph for tests that only need their stages; registrations go to a registry of
 * their own.
 */
public final class TestSetups {
    private TestSetups() {}

    /** A builder with every module set up as a built-in. */
    public static StageGraphBuilder stages(IrisModule... modules) {
        var builder = new StageGraphBuilder();
        for (var module : modules) {
            install(builder, DiscoveredModule.builtIn(module));
        }
        return builder;
    }

    public static void install(StageGraphBuilder builder, DiscoveredModule module) {
        ModuleSetups.run(module, builder, new ModuleRegistry());
    }
}
