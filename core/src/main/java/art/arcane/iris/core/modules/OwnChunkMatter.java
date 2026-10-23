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

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

/**
 * The matter mantle of a chunk invocation: its own chunk, which a chunk stage writes as it writes the mantle layers of
 * its own chunk, and its neighbours only as far as the world already holds them in memory.
 */
final class OwnChunkMatter implements MatterMantle {
    private final MatterMantle mantle;
    private final int chunkX;
    private final int chunkZ;

    OwnChunkMatter(MatterMantle mantle, int chunkX, int chunkZ) {
        this.mantle = mantle;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
        if (chunkX != this.chunkX || chunkZ != this.chunkZ) {
            return null;
        }
        return mantle.chunk(chunkX, chunkZ);
    }

    @Override
    public MantleChunk<Matter> loadedChunk(int chunkX, int chunkZ) {
        return mantle.loadedChunk(chunkX, chunkZ);
    }
}
