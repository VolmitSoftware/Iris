package com.volmit.host;

import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.stage.StageGraph;
import com.volmit.support.EmptyResources;
import com.volmit.support.TestWorld;
import com.volmit.world.PackWorld;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleRunnerTest {
    /** A module whose hooks record themselves into {@code calls} and throw on the named hook. */
    private static IrisModule module(String id, List<String> calls, String failingHook) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "Lifecycle " + id, "1"); }
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) {
                registry.registerLifecycle(new ModuleLifecycle() {
                    public void packLoaded(PackResources resources, PackSettings settings) { record("loaded"); }
                    public void worldStarted(PackWorld world) { record("started"); }
                    public void warmUp(PackWorld world) { record("warmed"); }
                    public void worldStopped(PackWorld world) { record("stopped"); }

                    private void record(String hook) {
                        calls.add(hook + " " + id);
                        if (hook.equals(failingHook)) {
                            throw new IllegalStateException(id + " cannot be " + hook);
                        }
                    }
                });
            }
        };
    }

    private static LifecycleRunner runner(IrisModule... modules) {
        var discovered = Arrays.stream(modules).map(DiscoveredModule::builtIn).toList();
        return new LifecycleRunner(LoadedModules.load(discovered, Set.of(), Set.of()).catalog());
    }

    @Test
    void startsInModuleIdOrderAndStopsInReverse() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:zeta", calls, null), module("test:alpha", calls, null));
        try (var world = new TestWorld()) {
            runner.worldStarted(world);
            runner.worldStopped(world);
        }

        assertEquals(List.of("started test:alpha", "started test:zeta", "stopped test:zeta", "stopped test:alpha"), calls);
    }

    @Test
    void aFailedStartStopsTheModulesThatStartedAndNamesTheModule() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:alpha", calls, null), module("test:beta", calls, "started"), module("test:gamma", calls, null));
        try (var world = new TestWorld()) {
            var failure = assertThrows(IllegalStateException.class, () -> runner.worldStarted(world));
            assertTrue(failure.getMessage().startsWith("Module test:beta from host classpath failed while starting a world"),
                    failure.getMessage());
        }

        assertEquals(List.of("started test:alpha", "started test:beta", "stopped test:alpha"), calls);
    }

    @Test
    void everyModuleStopsEvenWhenOneFailsAndEveryFailureIsReported() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:alpha", calls, "stopped"), module("test:beta", calls, null), module("test:gamma", calls, "stopped"));
        try (var world = new TestWorld()) {
            var failure = assertThrows(IllegalStateException.class, () -> runner.worldStopped(world));
            assertTrue(failure.getMessage().startsWith("Module test:gamma from host classpath failed while stopping a world"),
                    failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertTrue(failure.getSuppressed()[0].getMessage().startsWith("Module test:alpha"));
        }

        assertEquals(List.of("stopped test:gamma", "stopped test:beta", "stopped test:alpha"), calls);
    }

    @Test
    void warmsUpInModuleIdOrderAfterTheModulesStarted() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:zeta", calls, null), module("test:alpha", calls, null));
        try (var world = new TestWorld()) {
            runner.worldStarted(world);
            runner.warmUp(world);
        }

        assertEquals(List.of("started test:alpha", "started test:zeta", "warmed test:alpha", "warmed test:zeta"), calls);
    }

    @Test
    void everyModuleWarmsUpEvenWhenOneFailsAndEveryFailureIsReported() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:alpha", calls, "warmed"), module("test:beta", calls, null), module("test:gamma", calls, "warmed"));
        try (var world = new TestWorld()) {
            var failure = assertThrows(IllegalStateException.class, () -> runner.warmUp(world));
            assertTrue(failure.getMessage().startsWith("Module test:alpha from host classpath failed while warming up a world"),
                    failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertTrue(failure.getSuppressed()[0].getMessage().startsWith("Module test:gamma"));
        }

        assertEquals(List.of("warmed test:alpha", "warmed test:beta", "warmed test:gamma"), calls);
    }

    @Test
    void modulesWithoutAWarmUpHookWarmUpNothing() {
        var module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:plain", "Plain", "1"); }
            public void install(StageGraph graph) {}
            public void register(FeatureRegistry registry) { registry.registerLifecycle(new ModuleLifecycle() {}); }
        };
        try (var world = new TestWorld()) {
            runner(module).warmUp(world);
        }
    }

    @Test
    void aFailedPackLoadNamesTheModuleAndSkipsTheRest() {
        var calls = new ArrayList<String>();
        var runner = runner(module("test:alpha", calls, "loaded"), module("test:beta", calls, null));
        PackResources resources = new EmptyResources();

        var failure = assertThrows(IllegalStateException.class, () -> runner.packLoaded(resources, new TestWorld().settings()));

        assertTrue(failure.getMessage().startsWith("Module test:alpha from host classpath failed while loading pack test:pack"),
                failure.getMessage());
        assertEquals(List.of("loaded test:alpha"), calls);
    }
}
