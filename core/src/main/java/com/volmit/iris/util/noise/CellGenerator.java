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

package com.volmit.iris.util.noise;

import com.volmit.iris.util.math.RNG;
import lombok.Getter;
import lombok.Setter;

public class CellGenerator {
    private final FastNoiseDouble fn;
    private final FastNoiseDouble fd;
    private final CNG cng;

    @Getter
    @Setter
    private double cellScale;

    @Getter
    @Setter
    private double shuffle;

    public CellGenerator(RNG rng) {
        shuffle = 128;
        cellScale = 0.73;
        cng = CNG.signature(rng.nextParallelRNG(3204));
        RNG rx = rng.nextParallelRNG(8735652);
        long s = rx.lmax();
        fn = new FastNoiseDouble(s);
        fn.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
        fn.setCellularReturnType(FastNoiseDouble.CellularReturnType.CellValue);
        fn.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
        fd = new FastNoiseDouble(s);
        fd.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
        fd.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
        fd.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);
    }

    public double getDistance(double x, double z) {
        return ((fd.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle)))) + 1f) / 2f;
    }

    public double getDistance(double x, double y, double z) {
        return ((fd.GetCellular(((x * cellScale) + (cng.noise(x, y, z) * shuffle)), ((y * cellScale) + (cng.noise(x, y, z) * shuffle)), ((z * cellScale) + (cng.noise(z, y, x) * shuffle)))) + 1f) / 2f;
    }

    public double getValue(double x, double z, int possibilities) {
        if (possibilities == 1) {
            return 0;
        }

        return ((fn.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
    }

    public double getValue(double x, double y, double z, int possibilities) {
        if (possibilities == 1) {
            return 0;
        }

        return ((fn.GetCellular(((x * cellScale) + (cng.noise(x, z) * shuffle)), ((y * 8 * cellScale) + (cng.noise(x, y * 8) * shuffle)), ((z * cellScale) + (cng.noise(z, x) * shuffle))) + 1f) / 2f) * (possibilities - 1);
    }

    public int getIndex(double x, double z, int possibilities) {
        if (possibilities == 1) {
            return 0;
        }

        return (int) Math.round(getValue(x, z, possibilities));
    }

    public int getIndex(double x, double y, double z, int possibilities) {
        if (possibilities == 1) {
            return 0;
        }

        return (int) Math.round(getValue(x, y, z, possibilities));
    }
}
