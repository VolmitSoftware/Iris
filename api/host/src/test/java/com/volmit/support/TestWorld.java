package com.volmit.support;

import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.ModuleLogs;
import com.volmit.host.WorldStates;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import java.util.Optional;
import java.util.function.Function;

/** A world for tests: the test seed, no resources, no declared settings, and a real state store. */
public final class TestWorld implements PackWorld, AutoCloseable {
    private final WorldStates states;
    private final ModuleLog log;

    public TestWorld() {
        this(new WorldStates(), ModuleLogs.discarding());
    }

    private TestWorld(WorldStates states, ModuleLog log) {
        this.states = states;
        this.log = log;
    }

    @Override
    public long seed() {
        return TestContexts.SEED;
    }

    @Override
    public PackResources resources() {
        throw new AssertionError("Unused resources");
    }

    @Override
    public PackSettings settings() {
        return new PackSettings() {
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                return SettingsStack.empty();
            }

            public PackResources resources() {
                return TestWorld.this.resources();
            }
        };
    }

    @Override
    public int minWorldY() {
        return 0;
    }

    @Override
    public Optional<RunningWorld> running() {
        return Optional.empty();
    }

    @Override
    public Placements placements() {
        throw new AssertionError("Unused placements");
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        return states.state(type, () -> create.apply(this));
    }

    @Override
    public ModuleLog log() {
        return log;
    }

    @Override
    public void close() {
        states.close();
    }
}
