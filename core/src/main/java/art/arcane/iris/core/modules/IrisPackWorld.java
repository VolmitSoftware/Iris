/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.host.WorldStates;
import com.volmit.world.PackWorld;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * One Iris world as modules see it: an engine runtime generating with one snapshot of its pack's resources, and the
 * state modules keep for it. {@link ModuleWorlds} makes one when the runtime is built and a new one whenever the
 * pack's resources reload under it. Iris's built-in modules reach the engine through {@link #of(PackWorld)}, as built-in
 * reaches do through {@link IrisPackSettings#of}.
 */
public final class IrisPackWorld implements PackWorld, IrisSeed.Source {
    private final Engine engine;
    private final IrisPackResources resources;
    private final WorldStates states;
    private final ModuleLog log;
    private final Map<ModuleLog, IrisPackWorld> viewsByLog;

    IrisPackWorld(Engine engine) {
        this(engine, engine.getData().getPackResources(), new WorldStates(), IrisApiLogger.hostLog(), new ConcurrentHashMap<>());
    }

    private IrisPackWorld(Engine engine, IrisPackResources resources, WorldStates states, ModuleLog log,
                          Map<ModuleLog, IrisPackWorld> viewsByLog) {
        this.engine = engine;
        this.resources = resources;
        this.states = states;
        this.log = log;
        this.viewsByLog = viewsByLog;
    }

    /** Iris's own view of {@code world}, which Iris hands to every stage, service and lifecycle hook. */
    public static IrisPackWorld of(PackWorld world) {
        if (!(world instanceof IrisPackWorld irisWorld)) {
            throw new IllegalArgumentException("Built-in modules work in worlds Iris generates, not " + world);
        }
        return irisWorld;
    }

    /** The engine generating this world. */
    public Engine engine() {
        return engine;
    }

    @Override
    public long seed() {
        return engine.getSeedManager().getSeed();
    }

    @Override
    public IrisPackResources resources() {
        return resources;
    }

    @Override
    public PackSettings settings() {
        return new IrisPackSettings(engine);
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        return states.state(type, () -> create.apply(this));
    }

    @Override
    public ModuleLog log() {
        return log;
    }

    /** One view per log, since the services ask for a module's view on every call. */
    @Override
    public IrisPackWorld withLog(ModuleLog log) {
        return viewsByLog.computeIfAbsent(log, moduleLog -> new IrisPackWorld(engine, resources, states, moduleLog, viewsByLog));
    }

    @Override
    public long seed(IrisSeed seed) {
        SeedManager seeds = engine.getSeedManager();
        return switch (seed) {
            case BIOME -> seeds.getBiome();
            case CARVE -> seeds.getCarve();
            case COMPONENT -> seeds.getComponent();
            case DECORATOR -> seeds.getDecorator();
            case DEPOSIT -> seeds.getDeposit();
            case ENTITY -> seeds.getEntity();
            case LOOT -> seeds.getLoot();
            case MANTLE -> seeds.getMantle();
            case POST -> seeds.getPost();
            case TERRAIN -> seeds.getTerrain();
        };
    }

    /** Whether the pack's resources were reloaded since this world was made, so it generates with an old snapshot. */
    boolean isStale() {
        return engine.getData().getPackResources() != resources;
    }

    /** Closes the state modules kept, such as workers that hold engine resources. */
    void closeStates() {
        states.close();
    }
}
