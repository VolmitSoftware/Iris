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
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
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

    private transient IrisMarker referenceMarker;

    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Desc("The entity spawns to add")
    private KList<IrisEntitySpawn> spawns = new KList<>();

    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Desc("The entity spawns to add initially. EXECUTES PER CHUNK!")
    private KList<IrisEntitySpawn> initialSpawns = new KList<>();

    @Desc("The energy multiplier when calculating spawn energy usage")
    private double energyMultiplier = 1;

    @Desc("This spawner will not spawn in a given chunk if that chunk has more than the defined amount of living entities.")
    private int maxEntitiesPerChunk = 1;

    @Desc("The block of 24 hour time to contain this spawn in.")
    private IrisTimeBlock timeBlock = new IrisTimeBlock();

    @Desc("The block of 24 hour time to contain this spawn in.")
    private IrisWeather weather = IrisWeather.ANY;

    @Desc("The maximum rate this spawner can fire")
    private IrisRate maximumRate = new IrisRate();

    @Desc("The maximum rate this spawner can fire on a specific chunk")
    private IrisRate maximumRatePerChunk = new IrisRate();

    @Desc("The light levels this spawn is allowed to run in (0-15 inclusive)")
    private IrisRange allowedLightLevels = new IrisRange(0, 15);

    @Desc("Where should these spawns be placed")
    private IrisSpawnGroup group = IrisSpawnGroup.NORMAL;

    public boolean isValid(IrisBiome biome) {
        return switch (group) {
            case NORMAL -> switch (biome.getInferredType()) {
                case SHORE, SEA, CAVE -> false;
                case LAND -> true;
            };
            case CAVE -> true;
            case UNDERWATER -> switch (biome.getInferredType()) {
                case SHORE, LAND, CAVE -> false;
                case SEA -> true;
            };
            case BEACH -> switch (biome.getInferredType()) {
                case SHORE -> true;
                case LAND, CAVE, SEA -> false;
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

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
