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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of mantle slice types that survive chunk cleanup. Declared through
 * IrisToolbelt/IrisModdedAPI's retainMantleDataForSlice; honored by both EngineMantle cleanup
 * paths (normal trim and pregen force-cleanup). Retained slices are not a leak: they persist to
 * tectonic plates and unload with the region - the cost is larger region files.
 *
 * <p>The block-state slice is deliberately never retainable: it is the largest mantle consumer
 * and is fully regenerable, so retaining it would balloon every region file with no consumer.
 */
public final class MantleSliceRetention {
    private static final Set<String> retained = ConcurrentHashMap.newKeySet();

    private MantleSliceRetention() {
    }

    public static void retain(String className) {
        if (className == null || PlatformBlockState.class.getCanonicalName().equals(className)) {
            return;
        }
        if (retained.add(className)) {
            IrisLogging.debug("Mantle slice retained across chunk cleanup: " + className);
        }
    }

    public static boolean isRetained(String className) {
        return className != null && retained.contains(className);
    }

    public static boolean isRetained(Class<?> sliceType) {
        return sliceType != null && retained.contains(sliceType.getCanonicalName());
    }

    static void deleteUnlessRetained(MantleChunk<Matter> chunk, Class<?> sliceType) {
        if (isRetained(sliceType)) {
            return;
        }
        chunk.deleteSlices(sliceType);
    }

    static void clearForTesting() {
        retained.clear();
    }
}
