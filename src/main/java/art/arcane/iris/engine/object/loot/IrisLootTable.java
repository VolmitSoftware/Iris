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

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a loot table. Biomes, Regions & Objects can add or replace the virtual table with these loot tables")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisLootTable extends IrisRegistrant {
    public static final int MAX_PICKED = 64;
    public static final int MAX_TRIES = 256;

    @Required
    @Desc("The name of this loot table")
    @MinNumber(2)
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

    @Desc("The loot in this table")
    @ArrayType(min = 1, type = IrisLoot.class)
    private KList<IrisLoot> loot = new KList<>();

    @Override
    public String getFolderName() {
        return "loot";
    }

    @Override
    public String getTypeName() {
        return "Loot";
    }
}
