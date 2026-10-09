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

package art.arcane.iris.engine.object.loot;

import art.arcane.iris.engine.loot.LootResources;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.DataProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("loot-registry")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot entry")
@Data
public class IrisLootReference {
    public static final double MAX_MULTIPLIER = 16D;

    private final transient AtomicCache<KList<IrisLootTable>> tt = new AtomicCache<>();
    @Desc("ADD = add on top of parent tables. REPLACE = clear parent tables, then add these. CLEAR = clear parent tables and add nothing, even if tables are listed. FALLBACK = only used when nothing else defined a table.")
    private IrisLootMode mode = IrisLootMode.ADD;
    @RegistryListResource(IrisLootTable.class)
    @ArrayType(min = 1, type = String.class)
    @Desc("Add loot table registries here")
    private KList<String> tables = new KList<>();
    @MinNumber(0)
    @MaxNumber(MAX_MULTIPLIER)
    @Desc("Scale loot sources in this area from 0 to 16 times their normal count")
    private double multiplier = 1D;

    public KList<IrisLootTable> getLootTables(DataProvider g) {
        return tt.aquire(() ->
        {
            KList<IrisLootTable> t = new KList<>();

            for (String i : tables) {
                t.add(LootResources.table(g.getData(), i));
            }

            return t;
        });
    }
}
