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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.Matter;

/**
 * The one mantle chunk a generating chunk's stages may read: pinned the first time a stage asks for it and
 * released once the chunk's stages are done.
 */
final class PinnedMantleChunk implements IrisModuleMantle.Chunks, AutoCloseable {
    /** A tectonic plate holds 32 by 32 chunks. */
    private static final int PLATE_SHIFT = 5;
    private static final int PLATE_MASK = (1 << PLATE_SHIFT) - 1;

    private final Engine engine;
    private final int chunkX;
    private final int chunkZ;
    private MantleChunk<Matter> pinned;

    PinnedMantleChunk(Engine engine, int chunkX, int chunkZ) {
        this.engine = engine;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public synchronized MantleChunk<Matter> chunk(int x, int z) {
        if (x != chunkX || z != chunkZ) {
            return null;
        }
        if (pinned == null) {
            pinned = engine.getMantle().getMantle().getChunk(chunkX, chunkZ).use();
        }
        return pinned;
    }

    @Override
    public MantleChunk<Matter> loadedChunk(int x, int z) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        TectonicPlate<Matter> plate = mantle.getLoadedRegions().get(Mantle.key(x >> PLATE_SHIFT, z >> PLATE_SHIFT));
        if (plate == null || plate.isClosed()) {
            return null;
        }
        return plate.get(x & PLATE_MASK, z & PLATE_MASK);
    }

    @Override
    public synchronized void close() {
        if (pinned != null) {
            pinned.release();
            pinned = null;
        }
    }
}
