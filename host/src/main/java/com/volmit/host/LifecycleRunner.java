package com.volmit.host;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Objects;

/** Calls the lifecycle hooks of one pack's modules ({@link FeatureCatalog#lifecycles()}) the way
 * {@link com.volmit.ModuleLifecycle} promises: loading and starting in module id order, stopping in reverse.
 *
 * <pre>{@code
 * var lifecycles = new LifecycleRunner(pack.catalog());
 * lifecycles.packLoaded(resources, packSettings);   // once per pack snapshot, before its first world starts
 * lifecycles.worldStarted(world);                   // before the world generates anything
 * lifecycles.warmUp(world);                         // then, possibly on another thread, before its first generation
 * ...
 * lifecycles.worldStopped(world);                   // then close the world's state
 * }</pre>
 *
 * A failure names the module whose hook failed. The runner keeps no state: the host decides when a snapshot is loaded
 * and which worlds run, and calls one method at a time per world.
 */
public final class LifecycleRunner {
    private final List<LifecycleRegistration> lifecycles;

    public LifecycleRunner(FeatureCatalog catalog) {
        this.lifecycles = Objects.requireNonNull(catalog, "catalog").lifecycles();
    }

    /** Runs every {@code packLoaded} hook in order, each with a {@link com.volmit.PackContext} whose log is its module's;
     * the first that throws ends the run with an IllegalStateException naming its module, and the hooks after it do not
     * run.
     */
    public void packLoaded(PackResources resources, PackSettings settings) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(settings, "settings");
        for (var registration : lifecycles) {
            try {
                registration.lifecycle().packLoaded(registration.pack(resources, settings));
            } catch (RuntimeException failure) {
                throw failed("loading pack " + resources.packId(), registration, failure);
            }
        }
    }

    /** Runs every {@code worldStarted} hook in order. When one throws, the modules started before it are stopped again,
     * in reverse, and an IllegalStateException naming the failed module is thrown, with failures of those stops
     * suppressed in it.
     */
    public void worldStarted(PackWorld world) {
        Objects.requireNonNull(world, "world");
        for (int index = 0; index < lifecycles.size(); index++) {
            var registration = lifecycles.get(index);
            try {
                registration.lifecycle().worldStarted(registration.scope(world));
            } catch (RuntimeException failure) {
                var startFailure = failed("starting a world", registration, failure);
                stopInReverse(world, index - 1, startFailure);
                throw startFailure;
            }
        }
    }

    /** Runs every {@code warmUp} hook in order, each even when an earlier one threw; afterwards throws the first
     * failure, naming its module, with the later ones suppressed. The caller picks the thread and waits for this
     * before the world generates.
     */
    public void warmUp(PackWorld world) {
        Objects.requireNonNull(world, "world");
        IllegalStateException failure = null;
        for (var registration : lifecycles) {
            try {
                registration.lifecycle().warmUp(registration.scope(world));
            } catch (RuntimeException warmFailure) {
                var named = failed("warming up a world", registration, warmFailure);
                if (failure == null) {
                    failure = named;
                } else {
                    failure.addSuppressed(named);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Runs every {@code worldStopped} hook in reverse order, each even when an earlier one threw; afterwards throws the
     * first failure, naming its module, with the later ones suppressed.
     */
    public void worldStopped(PackWorld world) {
        Objects.requireNonNull(world, "world");
        IllegalStateException failure = stopInReverse(world, lifecycles.size() - 1, null);
        if (failure != null) {
            throw failure;
        }
    }

    /** Stops the modules from {@code last} down to the first, collecting failures into {@code failure}. */
    private IllegalStateException stopInReverse(PackWorld world, int last, IllegalStateException failure) {
        for (int index = last; index >= 0; index--) {
            var registration = lifecycles.get(index);
            try {
                registration.lifecycle().worldStopped(registration.scope(world));
            } catch (RuntimeException stopFailure) {
                var named = failed("stopping a world", registration, stopFailure);
                if (failure == null) {
                    failure = named;
                } else {
                    failure.addSuppressed(named);
                }
            }
        }
        return failure;
    }

    private static IllegalStateException failed(String action, LifecycleRegistration registration, RuntimeException cause) {
        return new IllegalStateException("Module " + registration.module() + " failed while " + action + ": " + cause, cause);
    }
}
