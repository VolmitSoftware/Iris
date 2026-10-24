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

import art.arcane.iris.engine.framework.SeedManager;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.host.WorldStates;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.Optional;
import java.util.function.Function;

/**
 * The world of one placement a tool makes outside the worlds Iris generates, such as a studio paste: the pack's
 * resources and settings, the seed the placement draws, and state that lives as long as the placement. It has no
 * dimension, so its bottom is Y 0, and no lifecycle hook runs for it. Closing it closes the state modules kept.
 */
final class ToolWorld implements PackWorld, AutoCloseable {
    private final SeedManager seeds;
    private final PackResources resources;
    private final PackSettings settings;
    private final WorldStates states;
    private final ModuleLog log;

    ToolWorld(long seed, PackResources resources, PackSettings settings) {
        this.seeds = new SeedManager(seed);
        this.resources = resources;
        this.settings = settings;
        this.states = new WorldStates();
        this.log = IrisApiLogger.hostLog();
    }

    @Override
    public long seed() {
        return seeds.getSeed();
    }

    @Override
    public long seed(ResourceId purpose) {
        return IrisWorldSeeds.seed(seeds, purpose);
    }

    @Override
    public PackResources resources() {
        return resources;
    }

    @Override
    public PackSettings settings() {
        return settings;
    }

    /** A tool's world has no dimension: its bottom is Y 0. */
    @Override
    public int minWorldY() {
        return 0;
    }

    /** Nothing runs a tool's world. */
    @Override
    public Optional<RunningWorld> running() {
        return Optional.empty();
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
