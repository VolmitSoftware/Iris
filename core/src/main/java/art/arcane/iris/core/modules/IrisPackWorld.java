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
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.host.WorldStates;
import com.volmit.world.PackWorld;
import java.util.Optional;
import java.util.function.Function;

/**
 * One Iris world as modules see it: an engine runtime generating with one snapshot of its pack's resources, and the
 * state modules keep for it. {@link ModuleWorlds} makes one when the runtime is built and a new one whenever the
 * pack's resources reload under it. Iris's built-in modules reach what they need of Iris through its
 * {@linkplain #extension extensions}: the world's seeds, its live view and the object, floating and structure hosts.
 */
public final class IrisPackWorld implements PackWorld {
    private final Engine engine;
    private final IrisPackResources resources;
    private final WorldStates states;
    private final ModuleLog log;
    private final LiveWorld liveWorld;
    private final IrisSeeds seeds;
    private volatile IrisObjectHost objectHost;
    private volatile IrisFloatingHost floatingHost;
    private volatile IrisStructureHost structureHost;

    IrisPackWorld(Engine engine) {
        this.engine = engine;
        this.resources = engine.getData().getPackResources();
        this.states = new WorldStates();
        this.log = IrisApiLogger.hostLog();
        this.liveWorld = new IrisLiveWorld(engine, resources);
        this.seeds = this::seedOf;
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
    public int minHeight() {
        return engine.getWorld().minHeight();
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

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return HostExtensions.as(type, offered(type));
    }

    /** The engine is offered only for the structure built-ins, which drive Iris's structure locator themselves. */
    private Object offered(Class<?> type) {
        if (type == Engine.class) {
            return engine;
        }
        if (type == IrisSeeds.class) {
            return seeds;
        }
        if (type == LiveWorld.class) {
            return liveWorld;
        }
        if (type == ObjectHost.class) {
            return objects();
        }
        if (type == FloatingHost.class) {
            return floating();
        }
        if (type == StructureHost.class) {
            return structures();
        }
        return null;
    }

    private long seedOf(IrisSeed seed) {
        SeedManager seeds = engine.getSeedManager();
        return switch (seed) {
            case BIOME -> seeds.getBiome();
            case CARVE -> seeds.getCarve();
            case COMPLEX -> seeds.getComplex();
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

    private ObjectHost objects() {
        IrisObjectHost host = objectHost;
        if (host == null) {
            host = new IrisObjectHost(engine, log);
            objectHost = host;
        }
        return host;
    }

    private FloatingHost floating() {
        IrisFloatingHost host = floatingHost;
        if (host == null) {
            host = new IrisFloatingHost(engine, log);
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
