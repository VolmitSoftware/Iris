/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class RuntimeTableTest {
    @Test
    public void buildsOncePerPackObject() {
        AtomicInteger builds = new AtomicInteger();
        RuntimeTable<List<String>, Object> table = new RuntimeTable<>(config -> {
            builds.incrementAndGet();
            return new Object();
        });
        List<String> config = List.of("a");

        assertSame(table.get(config), table.get(config));
        assertEquals(1, builds.get());
    }

    @Test
    public void equalPackObjectsGetTheirOwnRuntime() {
        RuntimeTable<List<String>, Object> table = new RuntimeTable<>(config -> new Object());

        assertNotSame(table.get(List.of("a")), table.get(new ArrayList<>(List.of("a"))));
    }

    @Test
    public void builderMayResolveOtherEntriesOfTheSameTable() {
        RuntimeTable<Integer, Integer>[] holder = new RuntimeTable[1];
        holder[0] = new RuntimeTable<>(depth -> depth == 0 ? 0 : holder[0].get(depth - 1) + 1);

        assertEquals(Integer.valueOf(3), holder[0].get(3));
    }
}
