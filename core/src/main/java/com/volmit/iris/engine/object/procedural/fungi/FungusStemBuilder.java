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

package com.volmit.iris.engine.object.procedural.fungi;

import com.volmit.iris.engine.object.procedural.tree.TreeFunctions;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.util.math.Vector3i;

import java.util.Map;

public final class FungusStemBuilder {
    private FungusStemBuilder() {
    }

    public static double[] build(Map<Vector3i, BlockData> stemCells, Fungus fungus, int stemHeight, long seed) {
        int width = Math.max(1, Math.min(3, fungus.stemWidth()));
        double maxLean = stemHeight * Math.tan(Math.toRadians(Math.max(0.0, fungus.stemCurve())));
        double leanRad = Math.toRadians(fungus.stemLeanAzimuth());
        double leanX = Math.sin(leanRad);
        double leanZ = Math.cos(leanRad);
        double waveAmp = Math.max(0.0, fungus.stemWaveAmplitude());
        double wavePeriods = fungus.stemWavePeriods() == 0 ? 1.0 : fungus.stemWavePeriods();
        double waveAzimuth = Math.toRadians(fungus.stemLeanAzimuth() + 90.0);
        double waveX = Math.sin(waveAzimuth);
        double waveZ = Math.cos(waveAzimuth);

        double topCx = 0.0;
        double topCz = 0.0;
        int topY = Math.max(0, stemHeight - 1);

        for (int y = 0; y < stemHeight; y++) {
            double t = stemHeight <= 1 ? 0.0 : y / (double) (stemHeight - 1);
            double lean = maxLean * (t * t);
            double wave = waveAmp * Math.sin(2.0 * Math.PI * wavePeriods * t + TreeFunctions.valueNoise1D(seed, seed) * Math.PI * 2.0);
            double cx = lean * leanX + wave * waveX;
            double cz = lean * leanZ + wave * waveZ;

            for (int[] xz : squarePositions(cx, cz, width)) {
                stemCells.put(new Vector3i(xz[0], y, xz[1]), null);
            }

            topCx = cx;
            topCz = cz;
        }

        return new double[]{topCx, topY, topCz};
    }

    static int[][] squarePositions(double cx, double cz, int width) {
        if (width % 2 == 1) {
            int half = width / 2;
            int icx = (int) Math.round(cx);
            int icz = (int) Math.round(cz);
            int[][] out = new int[width * width][2];
            int idx = 0;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    out[idx][0] = icx + dx;
                    out[idx][1] = icz + dz;
                    idx++;
                }
            }
            return out;
        }
        int ox = (int) Math.floor(cx) - width / 2 + 1;
        int oz = (int) Math.floor(cz) - width / 2 + 1;
        int[][] out = new int[width * width][2];
        int idx = 0;
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                out[idx][0] = ox + dx;
                out[idx][1] = oz + dz;
                idx++;
            }
        }
        return out;
    }
}
