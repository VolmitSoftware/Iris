/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Drops the deferred custom-block and tile records that a native structure write has overwritten, so the
 * chunk's later materialization pass does not replay them onto the structure's blocks.
 */
public final class DeferredPlacementRecords {
    private DeferredPlacementRecords() {
    }

    public static void clearWritten(Engine engine, WrittenBlocks written) {
        if (written.isEmpty()) {
            return;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int minHeight = engine.getMinHeight();
        Long2ObjectOpenHashMap<MantleChunk<Matter>> acquired = new Long2ObjectOpenHashMap<>();
        try {
            for (int index = 0; index < written.size(); index++) {
                int x = written.x(index);
                int z = written.z(index);
                MantleChunk<Matter> chunk = acquired.computeIfAbsent(chunkKey(x >> 4, z >> 4),
                        key -> mantle.getChunk(x >> 4, z >> 4).use());
                synchronized (chunk) {
                    clearRecords(chunk, x & 15, written.y(index) - minHeight, z & 15);
                }
            }
        } finally {
            acquired.values().forEach(MantleChunk::release);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static void clearRecords(MantleChunk<Matter> chunk, int x, int y, int z) {
        if (y < 0 || (y >> 4) >= chunk.sectionCount() || !chunk.exists(y >> 4)) {
            return;
        }
        Matter section = chunk.get(y >> 4);
        if (section == null) {
            return;
        }
        clearRecord(section, Identifier.class, x, y & 15, z);
        clearRecord(section, TileWrapper.class, x, y & 15, z);
    }

    private static void clearRecord(Matter section, Class<?> type, int x, int y, int z) {
        if (section.hasSlice(type)) {
            section.getSlice(type).set(x, y, z, null);
        }
    }

    /** World positions a native structure wrote, in write order, duplicates allowed. */
    public static final class WrittenBlocks {
        private final IntArrayList xs = new IntArrayList();
        private final IntArrayList ys = new IntArrayList();
        private final IntArrayList zs = new IntArrayList();

        public void add(int x, int y, int z) {
            xs.add(x);
            ys.add(y);
            zs.add(z);
        }

        public boolean isEmpty() {
            return xs.isEmpty();
        }

        int size() {
            return xs.size();
        }

        int x(int index) {
            return xs.getInt(index);
        }

        int y(int index) {
            return ys.getInt(index);
        }

        int z(int index) {
            return zs.getInt(index);
        }
    }
}
