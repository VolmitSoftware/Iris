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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("noise-carving")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Sculpts the rock behind cliff faces and slopes with 3D noise, carving overhangs, arches and ragged cliffs into tall terrain such as mountains. Flat ground and the top of the terrain stay intact, so trees and grass keep their footing.")
@Data
public class IrisNoiseCarving {
    private static final int CRUST_FADE_BLOCKS = 3;
    private static final int CLIFF_CRUST_THICKNESS = 1;
    private static final double GENTLE_SLOPE_RISE_PER_BLOCK = 1;
    private static final double CLIFF_RISE_PER_BLOCK = 2;

    @Desc("The 3D noise that shapes the hollows. Rock is removed wherever this noise is below the threshold. Raise the zoom for larger hollows.")
    private IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.3);

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much rock is removed. Rock is carved wherever the noise is below this value, so 0 carves nothing and higher values carve more.")
    private double threshold = 0.3;

    @MinNumber(0.1)
    @MaxNumber(10)
    @Desc("Above 1 stretches the hollows upward into tall arches and spires. Below 1 flattens them into ledges and layered overhangs.")
    private double verticalStretch = 1;

    @MinNumber(1)
    @Desc("How many blocks of rock under gentle ground are never carved, so it keeps its trees and decoration. Steep cliffs keep only their outer block, so overhangs can open up in them.")
    private int crustThickness = 3;

    @MinNumber(3)
    @MaxNumber(64)
    @Desc("How many blocks into the rock carving reaches from a cliff face or slope. Carving is strongest at the face and fades out toward this distance.")
    private int reach = 16;

    /**
     * @param strength how strongly carving applies here, from 0 for none to 1 for full
     */
    @BlockCoordinates
    public boolean carves(RNG rng, IrisData data, int x, int y, int z, double strength) {
        double localThreshold = threshold * strength;
        if (localThreshold <= 0) {
            return false;
        }
        return style.create(rng, data).noise(x, y / verticalStretch, z) < localThreshold;
    }

    /**
     * How strongly carving applies at a depth under the surface: 0 inside the crust, easing in below it so the hollows
     * don't get a flat ceiling that follows the surface.
     *
     * @param depth     how many blocks below the column's surface the block is
     * @param steepness how many blocks the surface around the column drops per block outward
     */
    public double strengthBelowCrust(int depth, double steepness) {
        double belowCrust = (depth - crustThicknessOn(steepness) + 1) / CRUST_FADE_BLOCKS;
        return Math.max(0, Math.min(1, belowCrust));
    }

    private double crustThicknessOn(double steepness) {
        double cliffness = (steepness - GENTLE_SLOPE_RISE_PER_BLOCK) / (CLIFF_RISE_PER_BLOCK - GENTLE_SLOPE_RISE_PER_BLOCK);
        cliffness = Math.max(0, Math.min(1, cliffness));
        return crustThickness + (CLIFF_CRUST_THICKNESS - crustThickness) * cliffness;
    }
}
