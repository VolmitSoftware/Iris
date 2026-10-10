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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Desc;
import com.volmit.config.MinItems;
import com.volmit.config.Range;

import java.util.Collections;
import java.util.List;

/** A spawner of the pack, one file of {@code spawners/}: which entities appear where, when and how often. */
@Desc("Represents an entity spawn during initial chunk generation")
public final class Spawner {
    @MinItems(1)
    @Desc("The entity spawns to add")
    private List<EntitySpawn> spawns = List.of();

    @MinItems(1)
    @Desc("The entity spawns to add initially. EXECUTES PER CHUNK!")
    private List<EntitySpawn> initialSpawns = List.of();

    @Desc("This spawner will not spawn in a given chunk if that chunk has more than the defined amount of living entities.")
    private int maxEntitiesPerChunk = 1;

    @Desc("The block of 24 hour time to contain this spawn in.")
    private SpawnTimeBlock timeBlock = new SpawnTimeBlock();

    @Desc("The weather condition required for this spawner to fire.")
    private SpawnWeather weather = SpawnWeather.ANY;

    @Desc("The maximum rate this spawner can fire")
    private SpawnRate maximumRate = new SpawnRate();

    @Desc("The maximum rate this spawner can fire on a specific chunk")
    private SpawnRate maximumRatePerChunk = new SpawnRate();

    @Desc("The light levels this spawn is allowed to run in (0-15 inclusive)")
    private Range allowedLightLevels = Range.of(0, 15);

    @Desc("Where should these spawns be placed")
    private SpawnGroup group = SpawnGroup.NORMAL;

    public Spawner() {
    }

    public List<EntitySpawn> spawns() {
        return spawns == null ? List.of() : Collections.unmodifiableList(spawns);
    }

    public List<EntitySpawn> initialSpawns() {
        return initialSpawns == null ? List.of() : Collections.unmodifiableList(initialSpawns);
    }

    public int maxEntitiesPerChunk() {
        return maxEntitiesPerChunk;
    }

    public SpawnTimeBlock timeBlock() {
        return timeBlock == null ? new SpawnTimeBlock() : timeBlock;
    }

    public SpawnWeather weather() {
        return weather == null ? SpawnWeather.ANY : weather;
    }

    public SpawnRate maximumRate() {
        return maximumRate == null ? new SpawnRate() : maximumRate;
    }

    public SpawnRate maximumRatePerChunk() {
        return maximumRatePerChunk == null ? new SpawnRate() : maximumRatePerChunk;
    }

    public Range allowedLightLevels() {
        return allowedLightLevels == null ? Range.of(0, 15) : allowedLightLevels;
    }

    public SpawnGroup group() {
        return group == null ? SpawnGroup.NORMAL : group;
    }
}
