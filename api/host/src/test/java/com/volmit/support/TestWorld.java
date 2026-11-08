package com.volmit.support;

import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.ModuleLogs;
import com.volmit.host.WorldStates;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** A world for tests: the test seed, no resources, no declared settings, a real state store, and the service providers of
 * a catalog when it is given one.
 */
public final class TestWorld implements PackWorld, AutoCloseable {
    private final WorldStates states;
    private final ModuleLog log;
    private final FeatureCatalog catalog;

    public TestWorld() {
        this(null);
    }

    /** A world whose services are those {@code catalog} holds. */
    public TestWorld(FeatureCatalog catalog) {
        this(new WorldStates(), ModuleLogs.discarding(), catalog);
    }

    private TestWorld(WorldStates states, ModuleLog log, FeatureCatalog catalog) {
        this.states = states;
        this.log = log;
        this.catalog = catalog;
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
    public <S> List<S> services(Class<S> service) {
        return catalog == null ? List.of() : catalog.services(service);
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
