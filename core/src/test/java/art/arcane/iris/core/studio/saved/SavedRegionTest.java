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

package art.arcane.iris.core.studio.saved;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.Assert.assertEquals;

public class SavedRegionTest {
    private static final int CHUNK_COUNT = RegionFile.CHUNKS_ACROSS * RegionFile.CHUNKS_ACROSS;

    @Test
    public void readingAllReadsEveryChunkOnceEvenWhenAskedTwice() {
        AtomicIntegerArray reads = new AtomicIntegerArray(CHUNK_COUNT);
        SavedRegion region = new SavedRegion((chunkX, chunkZ) -> {
            reads.incrementAndGet(chunkZ * RegionFile.CHUNKS_ACROSS + chunkX);
            return Optional.empty();
        }, block -> true);

        region.readAll();
        region.readAll();

        for (int chunk = 0; chunk < CHUNK_COUNT; chunk++) {
            assertEquals("chunk " + chunk, 1, reads.get(chunk));
        }
    }
}
