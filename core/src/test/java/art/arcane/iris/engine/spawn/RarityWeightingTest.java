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

package art.arcane.iris.engine.spawn;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Rarity weights a pool exactly once: a spawner's spawns are picked from the expanded pool. */
public class RarityWeightingTest {
    private record Spawn(int rarity) {
    }

    @Test
    public void rarityIsAppliedOnceAsPoolWeightOnly() {
        Spawn common = new Spawn(1);
        Spawn rare = new Spawn(4);

        KList<Spawn> expanded = RarityWeighting.expand(List.of(common, rare), Spawn::rarity);

        // totalRarity 5 -> common appears 5/1 = 5 times, rare 5/4 = 1 time.
        assertEquals(6, expanded.size());
        assertEquals(5, expanded.stream().filter(entry -> entry == common).count());
        assertEquals(1, expanded.stream().filter(entry -> entry == rare).count());
    }

    @Test
    public void rarityZeroAndNegativeCountAsOne() {
        Spawn zero = new Spawn(0);
        Spawn negative = new Spawn(-5);

        KList<Spawn> expanded = RarityWeighting.expand(List.of(zero, negative, new Spawn(3)), Spawn::rarity);

        // totalRarity 1 + 1 + 3 = 5 -> 5, 5 and 1 entries.
        assertEquals(11, expanded.size());
        assertEquals(5, expanded.stream().filter(entry -> entry == zero).count());
        assertEquals(5, expanded.stream().filter(entry -> entry == negative).count());
    }
}
