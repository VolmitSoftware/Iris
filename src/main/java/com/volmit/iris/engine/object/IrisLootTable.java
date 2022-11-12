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

import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot table. Biomes, Regions & Objects can add or replace the virtual table with these loot tables")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisLootTable extends IrisRegistrant {
    @Required
    @Desc("The name of this loot table")
    @MinNumber(2)
    private String name = "";

    @MinNumber(1)
    @Desc("The rarity as in 1 in X chance")
    private int rarity = 1;

    @MinNumber(1)
    @Desc("The maximum amount of loot that can be picked in this table at a time.")
    private int maxPicked = 5;

    @MinNumber(0)
    @Desc("The minimum amount of loot that can be picked in this table at a time.")
    private int minPicked = 1;

    @Desc("The loot in this table")
    @ArrayType(min = 1, type = IrisLoot.class)
    private KList<IrisLoot> loot = new KList<>();

    public KList<ItemStack> getLoot(boolean debug, RNG rng, InventorySlotType slot, int x, int y, int z) {
        KList<ItemStack> lootf = new KList<>();

        int m = 0;
        int mx = rng.i(getMinPicked(), getMaxPicked());

        while (m < mx) {
            int num = rng.i(loot.size());

            IrisLoot l = loot.get(num);

            if (l.getSlotTypes() == slot) {
                ItemStack item = l.get(debug, false, this, rng, x, y, z);

                if (item != null && item.getType() != Material.AIR) {
                    lootf.add(item);
                    m++;
                }
            }
        }

        return lootf;
    }

    @Override
    public String getFolderName() {
        return "loot";
    }

    @Override
    public String getTypeName() {
        return "Loot";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
