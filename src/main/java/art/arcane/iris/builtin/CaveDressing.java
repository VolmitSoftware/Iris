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

package art.arcane.iris.builtin;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;

/**
 * What a cave's floor, ceiling and walls get from the rest of Iris's terrain and decoration: the blocks of a biome's layers
 * and wall, its decorators and the fluid of the dimension's palette. Those features keep their workers per world and are
 * ported on their own; until they are modules this narrow interface is the one thing {@code iris:carve} needs from them, in
 * IrisApi's types, and Iris's worlds implement it ({@link Source}).
 *
 * <p>Transitional: when {@code iris:terrain} and {@code iris:decorant} are modules of the same jar, {@code iris:carve} asks
 * their classes directly and this interface goes.
 *
 * <p>The methods that give a block take the {@code chunk} hunk the block is for. A block read from here and written to that
 * hunk keeps the identity of a content plugin's custom block, as it did when the layers wrote the platform's states themselves.
 */
public interface CaveDressing {
    /** The dressing of {@code world}, which must be a world Iris generates. */
    static CaveDressing of(PackWorld world) {
        if (world instanceof Source source) {
            return source.caveDressing();
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + world);
    }

    /** Iris's worlds answer for their dressing. */
    interface Source {
        CaveDressing caveDressing();
    }

    /** Whether the dimension's fluid palette gives a fluid (water, lava) at the column; false where it gives none. */
    boolean fluidPresentAt(int x, int z);

    /** The block the dimension's fluid palette gives at the column; null where it gives none. */
    BlockState fluidAt(Hunk<BlockState> chunk, int x, int z);

    /** The block the biome's wall gives at the position; null where the biome has no wall or the layer gives none. */
    BlockState wall(Hunk<BlockState> chunk, ResourceId biome, RNG rng, int x, int y, int z);

    /** The blocks the biome's surface layers stack up to at the column, top first, at most {@code depth} of them. */
    List<BlockState> floorLayers(Hunk<BlockState> chunk, ResourceId biome, int x, int z, RNG rng, int depth, int floorY);

    /** The blocks the biome's cave ceiling layers stack up to at the column, nearest the ceiling first, at most {@code depth} of them. */
    List<BlockState> ceilingLayers(Hunk<BlockState> chunk, ResourceId biome, int x, int z, RNG rng, int depth, int ceilingY);

    /** Whether the biome has a decorator for {@code part}. */
    boolean hasDecorators(ResourceId biome, DecorationPart part);

    /**
     * Decorates a cave's floor in the column at {@code chunk}'s {@code localX}, {@code localZ} (world {@code x}, {@code z}) above
     * the block at {@code floorY}, with the biome's decorators for {@link DecorationPart#NONE}, in a space {@code airThickness} high.
     */
    void decorateFloor(Hunk<BlockState> chunk, int localX, int localZ, int x, int z, ResourceId biome, int floorY, int airThickness);

    /** Decorates a cave's ceiling the same way, from the block at {@code ceilingY} down, with the decorators for {@link DecorationPart#CEILING}. */
    void decorateCeiling(Hunk<BlockState> chunk, int localX, int localZ, int x, int z, ResourceId biome, int ceilingY, int airThickness);

    /**
     * {@code layer}, the block a layer wants in the place of the ore {@code existing}, as the deepslate or the stone variant
     * of the ore that fits the rock {@code existing} is; {@code layer} itself where it has no variant.
     */
    BlockState deepSlateOre(Hunk<BlockState> chunk, BlockState existing, BlockState layer);
}
