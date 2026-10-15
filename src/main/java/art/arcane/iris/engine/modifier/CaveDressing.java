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
import com.volmit.stage.ChunkStageContext;

import java.util.List;

/**
 * What a cave's walls, floor and ceiling get in one chunk: its biome's layers and decorators and the dimension's fluid palette.
 * {@link #of} answers with the terrain and decorant built-ins, which ship in the same jar; tests answer with fixed blocks.
 */
public interface CaveDressing {
    /** The built-ins' dressing of the chunk {@code context} generates. */
    static CaveDressing of(ChunkStageContext context) {
        return new BuiltInCaveDressing(context);
    }

    BlockState fluidAt(int x, int z);

    /** The block of the biome's wall at the position; null when the biome declares no wall. */
    BlockState wall(ResourceId biome, RNG rng, int x, int y, int z);

    List<BlockState> floorLayers(ResourceId biome, int x, int z, RNG rng, int depth, int floorY);

    List<BlockState> ceilingLayers(ResourceId biome, int x, int z, RNG rng, int depth);

    boolean hasDecorators(ResourceId biome, DecorationPart part);

    void decorateFloor(int localX, int localZ, int x, int z, ResourceId biome, int floorY, int airThickness);

    void decorateCeiling(int localX, int localZ, int x, int z, ResourceId biome, int ceilingY, int airThickness);

    /** The ore that suits the {@code rock} it replaces, as the platform decides. */
    BlockState oreFor(BlockState rock, BlockState ore);
}
