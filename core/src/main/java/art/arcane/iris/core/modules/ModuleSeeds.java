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

/**
 * The seed of one module chunk invocation, from the world seed and the chunk position. IrisApi derives
 * each stage's stream from it and the stage id.
 */
final class ModuleSeeds {
    private static final long CHUNK_X_MULTIPLIER = 0x9E3779B97F4A7C15L;
    private static final long CHUNK_Z_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;

    private ModuleSeeds() {
    }

    static long chunk(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed ^ (chunkX * CHUNK_X_MULTIPLIER) ^ (chunkZ * CHUNK_Z_MULTIPLIER);
    }
}
