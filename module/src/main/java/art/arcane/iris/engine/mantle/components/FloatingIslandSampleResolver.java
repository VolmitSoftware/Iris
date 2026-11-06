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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.terrain.floating.Column;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.FloatingWorld;
import com.volmit.ModuleLog;
import com.volmit.resource.ResourceId;

import java.util.HashMap;
import java.util.Map;

/** The island samples of the columns one chunk's floating objects ask about, each sampled once; null where no island is. */
final class FloatingIslandSampleResolver implements IslandFace.SampleProvider {
    private final FloatingWorld world;
    private final int worldHeight;
    private final FloatingIslandBoundarySampler boundarySampler;
    private final Map<Column, FloatingIslandSample> samples = new HashMap<>();
    private final ModuleLog log;

    FloatingIslandSampleResolver(FloatingWorld world, int worldHeight, FloatingIslandBoundarySampler boundarySampler, ModuleLog log) {
        this.world = world;
        this.worldHeight = worldHeight;
        this.boundarySampler = boundarySampler;
        this.log = log;
    }

    @Override
    public FloatingIslandSample sample(int x, int z) {
        Column key = new Column(x, z);
        if (samples.containsKey(key)) {
            return samples.get(key);
        }
        FloatingIslandSample sample = resolve(x, z);
        samples.put(key, sample);
        return sample;
    }

    private FloatingIslandSample resolve(int x, int z) {
        ResourceId parent = parent(x, z);
        if (parent == null || world.declaredBy(parent).isEmpty()) {
            return null;
        }
        return FloatingIslandSample.sample(parent, x, z, worldHeight, world, boundarySampler, log);
    }

    ResourceId parent(int x, int z) {
        return boundarySampler.parent(x, z);
    }
}
