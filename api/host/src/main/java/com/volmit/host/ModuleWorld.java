package com.volmit.host;

import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** A world as one module sees it: everything is the host's world, state and extensions included, except the log, which
 * is the module's own. The host hands such a view to every stage body, service call and lifecycle hook, so a line is
 * labelled with the module that wrote it. A state created through the view is created with the view.
 */
final class ModuleWorld implements PackWorld {
    private final PackWorld world;
    private final ModuleLog log;

    private ModuleWorld(PackWorld world, ModuleLog log) {
        this.world = world;
        this.log = log;
    }

    /** {@code world} as the module whose log is {@code log} sees it; a view of a view wraps the host's world itself. */
    static PackWorld of(PackWorld world, ModuleLog log) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(log, "log");
        PackWorld hostWorld = world instanceof ModuleWorld view ? view.world : world;
        return new ModuleWorld(hostWorld, log);
    }

    @Override
    public long seed() {
        return world.seed();
    }

    @Override
    public long seed(ResourceId purpose) {
        return world.seed(purpose);
    }

    @Override
    public PackResources resources() {
        return world.resources();
    }

    @Override
    public PackSettings settings() {
        return world.settings();
    }

    @Override
    public int minWorldY() {
        return world.minWorldY();
    }

    @Override
    public Optional<RunningWorld> running() {
        return world.running();
    }

    @Override
    public Placements placements() {
        return world.placements();
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        Objects.requireNonNull(create, "create");
        return world.state(type, ignored -> create.apply(this));
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return world.extension(type);
    }

    @Override
    public <S> List<S> services(Class<S> service) {
        return world.services(service);
    }

    @Override
    public ModuleLog log() {
        return log;
    }

    @Override
    public String toString() {
        return world + " as " + log;
    }
}
