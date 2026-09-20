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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.world.loot.IrisLootTable;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import lombok.experimental.Accessors;

@Snippet("object-loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents loot within this object")
@Data
public class IrisObjectLoot implements IObjectLoot {
    private final transient AtomicCache<KList<NativeBlockState>> filterCache = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The list of blocks this loot table should apply to")
    private KList<IrisBlockData> filter = new KList<>();
    @Description("Exactly match the block data or not")
    private boolean exact = false;
    @Description("The loot table name")
    @Required
    @RegistryListResource(IrisLootTable.class)
    private String name;
    @Description("The weight of this loot table being chosen")
    @MinNumber(1)
    private int weight = 1;

    public KList<NativeBlockState> getFilter(IrisData rdata) {
        return filterCache.aquire(() ->
        {
            KList<NativeBlockState> b = new KList<>();

            for (IrisBlockData i : filter) {
                NativeBlockState bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }

    public boolean matchesFilter(IrisData manager, NativeBlockState data) {
        for (NativeBlockState filterState : getFilter(manager)) {
            if (B.matches(filterState, data)) return true;
        }
        return false;
    }
}
