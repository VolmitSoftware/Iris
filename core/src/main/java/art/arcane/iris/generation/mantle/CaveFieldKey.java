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

import art.arcane.volmlib.util.noise.program.NoiseProgram;
import art.arcane.volmlib.util.noise.program.PrimitiveKind;

record CaveFieldKey(long signature, long probe, NoiseProgram identity) {
    private static final double[][] PROBE_POINTS = {
            {0.5D, 3.25D, -7.75D},
            {-1031.125D, 77.5D, 2049.875D},
            {16D, 255D, -16D},
            {123_456.375D, -40.25D, -98_765.625D}
    };

    static CaveFieldKey of(NoiseProgram program) {
        if (!shareable(program)) {
            return new CaveFieldKey(program.structuralSignature(), 0L, program);
        }
        long probe = 0xCBF29CE484222325L;
        for (double[] point : PROBE_POINTS) {
            probe = (probe ^ Double.doubleToRawLongBits(program.sampleSigned3D(point[0], point[1], point[2]))) * 0x100000001B3L;
        }
        return new CaveFieldKey(program.structuralSignature(), probe, null);
    }

    private static boolean shareable(NoiseProgram program) {
        for (int node = 0; node < program.nodeCount(); node++) {
            if (program.nodeOpaque(node) || program.nodeKernel(node).kind() == PrimitiveKind.GENERIC) {
                return false;
            }
        }
        return true;
    }
}
