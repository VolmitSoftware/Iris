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

package art.arcane.iris.core.modules;

import java.nio.charset.StandardCharsets;

/**
 * The seed of one module invocation: a chunk from the world seed and the chunk position, a placement
 * from the world seed and the placement's identity. IrisApi derives each stage's stream from it and the
 * stage id.
 */
final class ModuleSeeds {
    private static final long X_MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long Z_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;
    private static final long Y_MULTIPLIER = 0x165667B19E3779F9L;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private ModuleSeeds() {
    }

    static long chunk(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed ^ (chunkX * X_MULTIPLIER) ^ (chunkZ * Z_MULTIPLIER);
    }

    /** {@code y} is the requested height, negative when the placement samples it. */
    static long placement(long worldSeed, String objectKey, int x, int y, int z) {
        return worldSeed ^ fnv1a(objectKey) ^ (x * X_MULTIPLIER) ^ (y * Y_MULTIPLIER) ^ (z * Z_MULTIPLIER);
    }

    private static long fnv1a(String text) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (value & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }
}
