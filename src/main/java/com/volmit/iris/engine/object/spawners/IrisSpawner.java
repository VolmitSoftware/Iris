/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object.spawners;

import com.volmit.iris.core.project.loader.IrisRegistrant;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.basic.IrisRate;
import com.volmit.iris.engine.object.basic.IrisTimeBlock;
import com.volmit.iris.engine.object.basic.IrisWeather;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.entity.IrisEntitySpawn;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.World;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisSpawner extends IrisRegistrant {
    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Desc("The entity spawns to add")
    private KList<IrisEntitySpawn> spawns = new KList<>();

    @Desc("The energy multiplier when calculating spawn energy usage")
    private double energyMultiplier = 1;

    @Desc("The block of 24 hour time to contain this spawn in.")
    private IrisTimeBlock timeBlock = new IrisTimeBlock();

    @Desc("The block of 24 hour time to contain this spawn in.")
    private IrisWeather weather = IrisWeather.ANY;

    @Desc("The maximum rate this spawner can fire")
    private IrisRate maximumRate = new IrisRate();

    @Desc("Where should these spawns be placed")
    private IrisSpawnGroup group = IrisSpawnGroup.NORMAL;

    public boolean isValid(IrisBiome biome) {
        return switch (group) {
            case NORMAL -> switch (biome.getInferredType()) {
                case SHORE, SEA, CAVE, RIVER, LAKE, DEFER -> false;
                case LAND -> true;
            };
            case CAVE -> true;
            case UNDERWATER -> switch (biome.getInferredType()) {
                case SHORE, LAND, CAVE, RIVER, LAKE, DEFER -> false;
                case SEA -> true;
            };
            case BEACH -> switch (biome.getInferredType()) {
                case SHORE -> true;
                case LAND, CAVE, RIVER, LAKE, SEA, DEFER -> false;
            };
        };
    }

    public boolean isValid(World world) {
        return timeBlock.isWithin(world) && weather.is(world);
    }

    @Override
    public String getFolderName() {
        return "spawners";
    }

    @Override
    public String getTypeName() {
        return "Spawner";
    }
}
