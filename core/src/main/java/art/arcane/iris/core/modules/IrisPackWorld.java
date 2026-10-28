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

import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.LegacyStreams;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.host.WorldStates;
import com.volmit.placement.Placements;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import java.util.Optional;
import java.util.function.Function;

/**
 * One Iris world as modules see it: an engine runtime generating with one snapshot of its pack's resources, and the
 * state modules keep for it. {@link ModuleWorlds} makes one when the runtime is built and a new one whenever the
 * pack's resources reload under it. Iris's built-in modules reach what they need of Iris through its
 * {@linkplain #extension extensions}: the spawners' cooldowns Iris saves with the world, Iris's legacy random streams,
 * and the floating and structure hosts.
 */
public final class IrisPackWorld implements PackWorld {
    private final Engine engine;
    private final IrisPackResources resources;
    private final WorldStates states;
    private final ModuleLog log;
    private final SpawnCooldowns spawnCooldowns;
    private volatile RunningWorld running;
    private final LegacyStreams legacyStreams;
    private final Placements placements;
    private volatile IrisFloatingHost floatingHost;
    private volatile IrisStructureHost structureHost;

    IrisPackWorld(Engine engine) {
        this.engine = engine;
        this.resources = engine.getData().getPackResources();
        this.states = new WorldStates();
        this.log = IrisApiLogger.hostLog();
        this.spawnCooldowns = new EngineSpawnCooldowns(engine);
        this.legacyStreams = new IrisLegacyStreams(engine.getData(), engine.getSeedManager());
        this.placements = new IrisPlacements(engine.getData(), engine, () -> this);
    }

    @Override
    public Placements placements() {
        return placements;
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
    public long seed(ResourceId purpose) {
        return IrisWorldSeeds.seed(engine.getSeedManager(), purpose);
    }

    @Override
    public IrisPackResources resources() {
        return resources;
    }

    @Override
    public int minWorldY() {
        return engine.getWorld().minHeight();
    }

    @Override
    public PackSettings settings() {
        return new IrisPackSettings(engine);
    }

    @Override
    public Optional<RunningWorld> running() {
        RunningWorld known = running;
        if (known == null) {
            known = new IrisRunningWorld(engine);
            running = known;
        }
        return Optional.of(known);
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
    public <T> Optional<T> extension(Class<T> type) {
        return HostExtensions.as(type, offered(type));
    }

    /** The engine is offered only for the structure built-ins, which drive Iris's structure locator themselves. */
    private Object offered(Class<?> type) {
        if (type == Engine.class) {
            return engine;
        }
        if (type == SpawnCooldowns.class) {
            return spawnCooldowns;
        }
        if (type == LegacyStreams.class) {
            return legacyStreams;
        }
        if (type == FloatingHost.class) {
            return floating();
        }
        if (type == StructureHost.class) {
            return structures();
        }
        return null;
    }

    private FloatingHost floating() {
        IrisFloatingHost host = floatingHost;
        if (host == null) {
            host = new IrisFloatingHost(engine);
            floatingHost = host;
        }
        return host;
    }

    private StructureHost structures() {
        IrisStructureHost host = structureHost;
        if (host == null) {
            host = new IrisStructureHost(engine, log);
            structureHost = host;
        }
        return host;
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
