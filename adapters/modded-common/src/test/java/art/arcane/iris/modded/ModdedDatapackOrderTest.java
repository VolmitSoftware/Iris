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

package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeDatapackSource;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ModdedDatapackOrderTest {
    private static final Set<String> PINNED = Set.of("iris_worldgen");

    @Test
    public void worldDatapacksSavedAfterTheIrisPackNoLongerOverrideIt() {
        List<String> saved = List.of("vanilla", "mod_data", "iris_worldgen", "file/iristest");

        assertEquals(List.of("vanilla", "mod_data", "file/iristest", "iris_worldgen"),
                NativeDatapackSource.orderPinnedLast(saved, Function.identity(), PINNED));
    }

    @Test
    public void alreadyLastOrUnpinnedSelectionsAreReturnedUnchanged() {
        List<String> ordered = List.of("vanilla", "file/iristest", "iris_worldgen");
        List<String> withoutIris = List.of("vanilla", "file/iristest");

        assertSame(ordered, NativeDatapackSource.orderPinnedLast(ordered, Function.identity(), PINNED));
        assertSame(withoutIris, NativeDatapackSource.orderPinnedLast(withoutIris, Function.identity(), PINNED));
        assertSame(withoutIris, NativeDatapackSource.orderPinnedLast(withoutIris, Function.identity(), Set.of()));
    }
}
