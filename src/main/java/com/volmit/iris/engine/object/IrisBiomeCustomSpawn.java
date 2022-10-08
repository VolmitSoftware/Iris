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

import com.volmit.iris.engine.object.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.entity.EntityType;

@Snippet("custom-biome-spawn")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A custom biome spawn")
@Data
public class IrisBiomeCustomSpawn {
    @Required
    @Desc("The biome's entity type")
    private EntityType type = EntityType.COW;

    @MinNumber(1)
    @MaxNumber(20)
    @Desc("The min to spawn")
    private int minCount = 2;

    @MinNumber(1)
    @MaxNumber(20)
    @Desc("The max to spawn")
    private int maxCount = 5;

    @MinNumber(1)
    @MaxNumber(1000)
    @Desc("The weight in this group. Higher weight, the more common this type is spawned")
    private int weight = 1;

    @Desc("The rarity")
    private IrisBiomeCustomSpawnType group = IrisBiomeCustomSpawnType.MISC;
}
