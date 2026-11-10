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

package art.arcane.iris.core.studio.noise;

import art.arcane.volmlib.util.function.NoiseProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * A square of noise values, a pixel's value taken at its north west corner, row by row from the north. Tile (0, 0)
 * starts at block (0, 0), so tiles at one scale meet without a seam.
 */
public record NoiseTile(int size, float[] values) {
    /** @param smooth whether to sample a lattice and fill in between, for noise too slow to ask at every pixel */
    public static NoiseTile sample(NoiseProvider noise, int tileX, int tileZ, double blocksPerPixel, int size,
                                   boolean smooth, BooleanSupplier cancelled) {
        double tileBlocks = size * blocksPerPixel;
        double originX = tileX * tileBlocks;
        double originZ = tileZ * tileBlocks;
        if (smooth) {
            return new NoiseTile(size, NoiseLattice.values(noise, originX, originZ, blocksPerPixel, size, cancelled));
        }
        float[] values = new float[size * size];
        for (int row = 0; row < size; row++) {
            requireWanted(cancelled);
            double z = originZ + row * blocksPerPixel;
            for (int column = 0; column < size; column++) {
                values[row * size + column] = (float) noise.noise(originX + column * blocksPerPixel, z);
            }
        }
        return new NoiseTile(size, values);
    }

    static void requireWanted(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Noise tile no longer wanted");
        }
    }

    /** The values as little endian 32 bit floats, an unsampleable value as NaN. */
    public byte[] encode() {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(values);
        return bytes.array();
    }
}
