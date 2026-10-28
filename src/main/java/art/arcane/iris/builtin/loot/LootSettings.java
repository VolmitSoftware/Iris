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

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/**
 * The loot tables one dimension, region or biome adds to the containers it covers, written as
 * {@code "loot": {"mode": "ADD", "tables": [...], "multiplier": 1}}. Levels stack, dimension first, then region and
 * biome; {@link LootMode} says what each does with the tables before it. An entity's {@code loot} has the same shape.
 */
@Snippet("iris/loot/loot-registry")
@Desc("Reference loot tables in this area")
@Feature(id = "iris:loot", name = "Loot", key = "loot", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class LootSettings implements IrisFeature {
    public static final double MAX_MULTIPLIER = 16D;

    @Desc("ADD = add on top of parent tables. REPLACE = clear parent tables, then add these. CLEAR = clear parent tables and add nothing, even if tables are listed. FALLBACK = only used when nothing else defined a table.")
    private LootMode mode = LootMode.ADD;

    @Ref(kind = LootTable.class)
    @Desc("Add loot table registries here")
    private List<String> tables = List.of();

    @MinNumber(0)
    @MaxNumber(MAX_MULTIPLIER)
    @Desc("Scale loot sources in this area from 0 to 16 times their normal count")
    private double multiplier = 1D;

    /** Nothing added, nothing scaled: what a level without loot settings means. */
    public LootSettings() {
    }

    public LootMode mode() {
        return mode == null ? LootMode.ADD : mode;
    }

    /** The keys of the loot tables, in order. */
    public List<String> tables() {
        return tables == null ? List.of() : Collections.unmodifiableList(tables);
    }

    public double multiplier() {
        return multiplier;
    }
}
