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

import com.volmit.block.BlockState;
import com.volmit.world.PackWorld;

import java.util.Optional;

/**
 * Transitional: settings Iris's built-in modules read that still live on Iris's dimension and biome classes while
 * wave 3a moves them into module settings, {@code hideOresForHiddenOre} (agent C) and {@code postProcessingWalls},
 * {@code postProcessingSlabs} and a biome's {@code wall} and {@code slab} (agent T). Iris's worlds implement it. When
 * those settings land, each method gives way to a read of its module setting and this type is deleted; nothing else
 * may use it.
 */
public interface PendingSettings {
    /** The pending settings of {@code world}, which must be a world Iris generates. */
    static PendingSettings of(PackWorld world) {
        if (world instanceof PendingSettings pending) {
            return pending;
        }
        throw new IllegalArgumentException("Built-in modules generate Iris's own worlds, not " + world);
    }

    /** The dimension's {@code hideOresForHiddenOre}. */
    boolean hidesOres();

    /** The dimension's {@code postProcessingWalls}. */
    boolean postProcessingWalls();

    /** The dimension's {@code postProcessingSlabs}. */
    boolean postProcessingSlabs();

    /** The {@code wall} of the surface biome of the column at {@code x}, {@code z}; empty when its palette lists no block. */
    Optional<BiomeLayer> wall(int x, int z);

    /** The {@code slab} of the surface biome of the column at {@code x}, {@code z}. */
    BiomeLayer slab(int x, int z);

    /** One of a biome's palette layers, picking as Iris always picked it, with the world's post-processing seed. */
    interface BiomeLayer {
        /** The block the layer picks at {@code x}, {@code y}, {@code z}; empty when none of its blocks exists. */
        Optional<BlockState> at(double x, double y, double z);

        /** Whether the layer's slope condition holds where the terrain's slope is {@code slope}. */
        boolean allowsSlope(double slope);
    }
}
