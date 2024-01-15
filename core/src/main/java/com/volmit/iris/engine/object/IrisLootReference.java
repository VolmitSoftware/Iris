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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.DataProvider;
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
    private final transient AtomicCache<KList<IrisLootTable>> tt = new AtomicCache<>();
    @Desc("Add = add on top of parent tables, Replace = clear first then add these. Clear = Remove all and dont add loot from this or parent.")
    private IrisLootMode mode = IrisLootMode.ADD;
    @RegistryListResource(IrisLootTable.class)
    @ArrayType(min = 1, type = String.class)
    @Desc("Add loot table registries here")
    private KList<String> tables = new KList<>();
    @MinNumber(0)
    @Desc("Increase the chance of loot in this area")
    private double multiplier = 1D;

    public KList<IrisLootTable> getLootTables(DataProvider g) {
        return tt.aquire(() ->
        {
            KList<IrisLootTable> t = new KList<>();

            for (String i : tables) {
                t.add(g.getData().getLootLoader().load(i));
            }

            return t;
        });
    }
}
