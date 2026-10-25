/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.map;

import art.arcane.volmlib.util.function.NoiseProvider;

import java.util.function.DoubleUnaryOperator;

/**
 * How the 3D views turn noise or a world into terrain, kept with the terrain so detail sampled later matches it.
 *
 * @param floorY     where sides on the edge of the sampled ground end
 * @param topY       a block above the highest ground expected
 * @param waterLevel the height water fills up to, or NaN when there is no water
 */
record TerrainSampling(TerrainColumns columns, int floorY, int topY, double waterLevel) {
    // The noise's colour range sets how low and high its terrain goes; the odd value outside it reaches a bit further.
    static TerrainSampling ofNoise(NoiseProvider noise, DoubleUnaryOperator heightOf, NoiseColorScale colorScale, double waterLevel) {
        int floorY = (int) Math.floor(heightOf.applyAsDouble(colorScale.minimum())) - 1;
        int topY = BlockFaces.blockTop(heightOf.applyAsDouble(colorScale.maximum()));
        return new TerrainSampling(new NoiseColumns(noise, heightOf, colorScale), floorY, topY, waterLevel);
    }

    boolean hasWater() {
        return Double.isFinite(waterLevel);
    }
}
