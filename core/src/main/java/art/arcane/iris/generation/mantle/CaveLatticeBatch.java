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

package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.noise.program.NoiseScratch;

final class CaveLatticeBatch {
    private static final ThreadLocal<CaveLatticeBatch> CURRENT = ThreadLocal.withInitial(CaveLatticeBatch::new);

    final NoiseScratch noise = new NoiseScratch();
    final int[] cornerStart = new int[CaveDensityLattice.CORNERS + 1];
    final int[] snapStart = new int[CaveDensityLattice.CORNERS + 1];
    int[] y = new int[0];
    int[] snapY = new int[0];
    boolean[] snapMissing = new boolean[0];
    double[] warpA = new double[0];
    double[] warpB = new double[0];
    double[] warpedX = new double[0];
    double[] warpedY = new double[0];
    double[] warpedZ = new double[0];
    double[] density = new double[0];
    double[] values = new double[0];
    double[] pointX = new double[0];
    double[] pointY = new double[0];
    double[] pointZ = new double[0];
    int[] pointIndex = new int[0];
    private CaveLatticeBatch nested;
    private boolean active;

    static CaveLatticeBatch acquire() {
        CaveLatticeBatch batch = CURRENT.get();
        while (batch.active) {
            if (batch.nested == null) {
                batch.nested = new CaveLatticeBatch();
            }
            batch = batch.nested;
        }
        batch.active = true;
        return batch;
    }

    void release() {
        active = false;
    }

    void ensure(int size) {
        if (y.length >= size) {
            return;
        }
        y = new int[size];
        snapY = new int[size];
        snapMissing = new boolean[size];
        warpA = new double[size];
        warpB = new double[size];
        warpedX = new double[size];
        warpedY = new double[size];
        warpedZ = new double[size];
        density = new double[size];
        values = new double[size * 2];
        pointX = new double[size * 2];
        pointY = new double[size * 2];
        pointZ = new double[size * 2];
        pointIndex = new int[size * 2];
    }
}
