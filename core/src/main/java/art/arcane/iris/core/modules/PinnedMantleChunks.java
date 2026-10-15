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

import java.util.HashMap;
import java.util.Map;

/**
 * The mantle chunks one placement touches: each is pinned the first time a stage asks for it and all are
 * released together once the placement is committed or discarded.
 */
final class PinnedMantleChunks implements IrisModuleMantle.Chunks, AutoCloseable {
    /** A tectonic plate holds 32 by 32 chunks. */
    private static final int PLATE_SHIFT = 5;
    private static final int PLATE_MASK = (1 << PLATE_SHIFT) - 1;

    private final Engine engine;
    private final Map<Long, MantleChunk<Matter>> pinned = new HashMap<>();

    PinnedMantleChunks(Engine engine) {
        this.engine = engine;
    }

    @Override
    public synchronized MantleChunk<Matter> chunk(int chunkX, int chunkZ) {
        return pinned.computeIfAbsent(key(chunkX, chunkZ), ignored -> engine.getMantle().getMantle().getChunk(chunkX, chunkZ).use());
    }

    @Override
    public MantleChunk<Matter> loadedChunk(int chunkX, int chunkZ) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        TectonicPlate<Matter> plate = mantle.getLoadedRegions().get(Mantle.key(chunkX >> PLATE_SHIFT, chunkZ >> PLATE_SHIFT));
        if (plate == null || plate.isClosed()) {
            return null;
        }
        return plate.get(chunkX & PLATE_MASK, chunkZ & PLATE_MASK);
    }

    @Override
    public synchronized void close() {
        pinned.values().forEach(MantleChunk::release);
        pinned.clear();
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) ^ (chunkZ & 0xffffffffL);
    }
}
