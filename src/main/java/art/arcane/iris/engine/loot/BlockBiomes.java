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

package art.arcane.iris.engine.loot;

import com.volmit.IrisFeature;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;

import java.util.Optional;

/**
 * The biome of a block in a running world, as block drops and ambient effects read it: the cave biome where the block
 * lies at least two blocks under the column's highest solid block, the surface biome everywhere else.
 */
public final class BlockBiomes {
    private static final int CAVE_DEPTH_BELOW_HIGHEST_BLOCK = 2;

    private BlockBiomes() {
    }

    public static ResourceId biomeAt(RunningWorld running, int x, int y, int z) {
        TerrainSampler layout = running.layout();
        ResourceId surface = layout.surfaceBiome(x, z);
        if (!layout.bounds().contains(x, y, z) || y > running.highest(x, z, true) - CAVE_DEPTH_BELOW_HIGHEST_BLOCK) {
            return surface;
        }
        return layout.caveBiome(x, y, z).orElse(surface);
    }

    /** The settings the biome of the block declares; empty when it declares none. */
    public static <F extends IrisFeature> Optional<F> declaredAt(RunningWorld running, Class<F> type, BlockPosition at) {
        return running.settings().declaredBy(biomeAt(running, at.x(), at.y(), at.z()), type);
    }
}
