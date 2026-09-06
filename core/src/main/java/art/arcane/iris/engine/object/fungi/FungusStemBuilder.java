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

package art.arcane.iris.engine.object.fungi;

import art.arcane.iris.engine.object.IrisFungus;
import art.arcane.iris.engine.object.tree.TreeFunctions;
import art.arcane.iris.util.common.math.Vector3i;

import java.util.Map;

public final class FungusStemBuilder {
    private FungusStemBuilder() {
    }

    public static double[] build(Map<Vector3i, FungusCellRole> roles, IrisFungus fungus, int stemHeight, long seed) {
        int width = Math.max(1, Math.min(3, fungus.getStemWidth()));
        double maxLean = stemHeight * Math.tan(Math.toRadians(Math.max(0.0, fungus.getStemCurve())));
        double leanRad = Math.toRadians(fungus.getStemLeanAzimuth());
        double leanX = Math.sin(leanRad);
        double leanZ = Math.cos(leanRad);
        double waveAmp = Math.max(0.0, fungus.getStemWaveAmplitude());
        double wavePeriods = fungus.getStemWavePeriods() == 0 ? 1.0 : fungus.getStemWavePeriods();
        double waveAzimuth = Math.toRadians(fungus.getStemLeanAzimuth() + 90.0);
        double waveX = Math.sin(waveAzimuth);
        double waveZ = Math.cos(waveAzimuth);
        double wavePhase = TreeFunctions.valueNoise1D(seed, seed) * Math.PI * 2.0;

        double topCx = 0.0;
        double topCz = 0.0;
        int topY = Math.max(0, stemHeight - 1);

        for (int y = 0; y < stemHeight; y++) {
            double t = stemHeight <= 1 ? 0.0 : y / (double) (stemHeight - 1);
            double lean = maxLean * (t * t);
            double wave = waveAmp * Math.sin(2.0 * Math.PI * wavePeriods * t + wavePhase);
            double cx = lean * leanX + wave * waveX;
            double cz = lean * leanZ + wave * waveZ;

            int ox = (width % 2 == 1 ? (int) Math.round(cx) : (int) Math.floor(cx) + 1) - width / 2;
            int oz = (width % 2 == 1 ? (int) Math.round(cz) : (int) Math.floor(cz) + 1) - width / 2;
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    roles.put(new Vector3i(ox + dx, y, oz + dz), FungusCellRole.STEM);
                }
            }

            topCx = cx;
            topCz = cz;
        }

        return new double[]{topCx, topY, topCz};
    }
}
