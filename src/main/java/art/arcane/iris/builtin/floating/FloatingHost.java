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

package art.arcane.iris.builtin.floating;

import art.arcane.volmlib.util.math.RNG;
import com.volmit.resource.ResourceId;

/**
 * What {@code iris:floating} asks of Iris beyond the API: a biome's own terrain height and the sky biome an island
 * paints. Iris offers it as an extension of its worlds: {@code world.extension(FloatingHost.class)}.
 */
public interface FloatingHost {
    /** The height the terrain generators of {@code biome} give the column, as Iris shapes the biome's terrain with {@code seed}. */
    double biomeHeight(ResourceId biome, int worldX, int worldZ, long seed);

    /**
     * Paints the sky biome {@code biome} stands for at the column into the blocks {@code ys} of the mantle, where the biome
     * pass finds it. {@code random} seeds the biome's generator the first time the biome is asked.
     */
    void paintSkyBiome(ResourceId biome, int worldX, int worldZ, int[] ys, RNG random);
}
