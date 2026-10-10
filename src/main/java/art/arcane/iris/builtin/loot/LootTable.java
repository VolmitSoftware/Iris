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

package art.arcane.iris.builtin.loot;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;

import java.util.Collections;
import java.util.List;

/** A loot table of the pack, one file of {@code loot/}. */
@Desc("Represents a loot table. Biomes, Regions & Objects can add or replace the virtual table with these loot tables")
public final class LootTable {
    public static final int MAX_PICKED = 64;
    public static final int MAX_TRIES = 256;

    @Required
    @Desc("The name of this loot table")
    private String name = "";

    @MinNumber(1)
    @Desc("The rarity as in 1 in X chance")
    private int rarity = 1;

    @MinNumber(1)
    @MaxNumber(MAX_PICKED)
    @Desc("The maximum amount of loot that can be picked in this table at a time, from 1 to 64")
    private int maxPicked = 5;

    @MinNumber(0)
    @MaxNumber(MAX_PICKED)
    @Desc("The minimum amount of loot that can be picked in this table at a time, from 0 to 64")
    private int minPicked = 1;

    @MinNumber(1)
    @MaxNumber(MAX_TRIES)
    @Desc("The maximum amount of tries to generate loot, from 1 to 256")
    private int maxTries = 10;

    @MinItems(1)
    @Desc("The loot in this table")
    private List<LootEntry> loot = List.of();

    public LootTable() {
    }

    public String name() {
        return name == null ? "" : name;
    }

    public int rarity() {
        return rarity;
    }

    public int maxPicked() {
        return maxPicked;
    }

    public int minPicked() {
        return minPicked;
    }

    public int maxTries() {
        return maxTries;
    }

    public List<LootEntry> loot() {
        return loot == null ? List.of() : Collections.unmodifiableList(loot);
    }
}
