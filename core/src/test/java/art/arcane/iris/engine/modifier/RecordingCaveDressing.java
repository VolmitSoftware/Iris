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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A cave dressing the tests of the carve module set up by biome: the wall block, the floor and ceiling layers and the fluid, with a record of what was decorated. */
public final class RecordingCaveDressing implements CaveDressing {
    public final Map<ResourceId, BlockState> walls = new HashMap<>();
    public final Map<ResourceId, List<BlockState>> floors = new HashMap<>();
    public final Map<ResourceId, List<BlockState>> ceilings = new HashMap<>();
    public final List<String> decorated = new ArrayList<>();
    public BlockState fluid;

    /** Whether {@link #fluid} is set, for the carving that blends caves towards fluid columns. */
    public boolean fluidPresentAt(int x, int z) {
        return fluid != null;
    }

    @Override
    public BlockState fluidAt(int x, int z) {
        return fluid;
    }

    @Override
    public BlockState wall(ResourceId biome, RNG rng, int x, int y, int z) {
        return walls.get(biome);
    }

    @Override
    public List<BlockState> floorLayers(ResourceId biome, int x, int z, RNG rng, int depth, int floorY) {
        return floors.getOrDefault(biome, List.of());
    }

    @Override
    public List<BlockState> ceilingLayers(ResourceId biome, int x, int z, RNG rng, int depth) {
        return ceilings.getOrDefault(biome, List.of());
    }

    @Override
    public boolean hasDecorators(ResourceId biome, DecorationPart part) {
        return false;
    }

    @Override
    public void decorateFloor(int localX, int localZ, int x, int z, ResourceId biome, int floorY, int airThickness) {
        decorated.add("floor " + localX + "," + floorY + "," + localZ);
    }

    @Override
    public void decorateCeiling(int localX, int localZ, int x, int z, ResourceId biome, int ceilingY, int airThickness) {
        decorated.add("ceiling " + localX + "," + ceilingY + "," + localZ);
    }

    @Override
    public BlockState oreFor(BlockState rock, BlockState ore) {
        return new BlockState("minecraft:deepslate_" + ore.material().path());
    }
}
