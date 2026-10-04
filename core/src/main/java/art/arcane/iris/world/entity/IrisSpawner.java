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

package art.arcane.iris.world.entity;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.pack.value.IrisRate;
import art.arcane.iris.world.IrisTimeBlock;
import art.arcane.iris.world.IrisWeather;

import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Configures initial inhabitants and ongoing entity spawning")
@Data
public class IrisSpawner extends IrisRegistrant {
    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Description("The entity spawns to add")
    private KList<IrisEntitySpawn> spawns = new KList<>();

    @ArrayType(min = 1, type = IrisEntitySpawn.class)
    @Description("The seeded entity pool used once per chunk or marker for initial inhabitants. Live time, weather, light, population, and cooldowns do not restrict this pool.")
    private KList<IrisEntitySpawn> initialSpawns = new KList<>();

    @Description("Maximum ambient entities of the selected entity's native spawn category in one chunk. Ongoing groups use remaining capacity; initial groups use this limit independently of existing mobs.")
    private int maxEntitiesPerChunk = 1;

    @Description("The 24-hour time window for ongoing spawns.")
    private IrisTimeBlock timeBlock = new IrisTimeBlock();

    @Description("The weather condition required for ongoing spawns.")
    private IrisWeather weather = IrisWeather.ANY;

    @Description("The maximum ongoing spawn rate")
    private IrisRate maximumRate = new IrisRate();

    @Description("The maximum ongoing spawn rate in a specific chunk")
    private IrisRate maximumRatePerChunk = new IrisRate();

    @Description("The light levels allowed for ongoing spawns (0-15 inclusive). Initial inhabitants ignore light.")
    private IrisRange allowedLightLevels = new IrisRange(0, 15);

    @Description("Where should these spawns be placed")
    private IrisSpawnGroup group = IrisSpawnGroup.NORMAL;

    public int remainingCapacity(IrisEntity entity, Map<String, Integer> counts) {
        if (entity == null) {
            return 0;
        }
        String category = entity.spawnCategory();
        int count = counts.getOrDefault(category, 0);
        if ("misc".equals(category)) {
            count = 0;
            for (int population : counts.values()) {
                count += population;
            }
        }
        return Math.max(0, maxEntitiesPerChunk - count);
    }

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

    public boolean isValid(NativeWorld world) {
        return timeBlock.isWithin(world) && weather.is(world);
    }

    public boolean canSpawn(Engine engine) {
        NativeWorld world = engine.getWorld().platformWorld();
        if (world == null || !isValid(world))
            return false;

        IrisRate rate = getMaximumRate();
        return rate.isInfinite() || engine.getEngineData().getCooldown(this).canSpawn(rate);
    }

    public boolean canSpawn(Engine engine, int x, int z) {
        if (!canSpawn(engine))
            return false;

        IrisRate rate = getMaximumRatePerChunk();
        return rate.isInfinite() || engine.getEngineData().getChunk(x, z).getCooldown(this).canSpawn(rate);
    }

    public void spawn(Engine engine) {
        if (getMaximumRate().isInfinite())
            return;

        engine.getEngineData().getCooldown(this).spawn(engine);
    }

    public void spawn(Engine engine, int x, int z) {
        spawn(engine);
        if (getMaximumRatePerChunk().isInfinite())
            return;

        engine.getEngineData().getChunk(x, z).getCooldown(this).spawn(engine);
    }

    /**
     * Cascade: a spawn whose entity the gate excluded can never spawn, so it leaves this spawner's lists; a spawner
     * with nothing left to spawn leaves every pool that references it. Loading entities here is safe - entities never
     * load spawners.
     */
    @Override
    public CompatStatus evaluateCompat(ContentGate gate) {
        CompatStatus base = super.evaluateCompat(gate);

        if (base.excluded()) {
            return base;
        }

        IrisData data = getLoader();

        if (data == null || data.getEntityLoader() == null) {
            return base;
        }

        boolean declared = !spawns.isEmpty() || !initialSpawns.isEmpty();
        List<CompatFinding> drops = new ArrayList<>();
        spawns = dropExcludedSpawns(data, spawns, "spawns", drops);
        initialSpawns = dropExcludedSpawns(data, initialSpawns, "initialSpawns", drops);

        if (!declared || !spawns.isEmpty() || !initialSpawns.isEmpty()) {
            return base;
        }

        return CompatPools.cascade(data, base, drops, "spawner", getLoadKey(), "no entity spawns remain");
    }

    private KList<IrisEntitySpawn> dropExcludedSpawns(IrisData data,
                                                      KList<IrisEntitySpawn> declared,
                                                      String field,
                                                      List<CompatFinding> drops) {
        KList<IrisEntitySpawn> kept = new KList<>();

        for (int index = 0; index < declared.size(); index++) {
            IrisEntitySpawn spawn = declared.get(index);

            if (spawn == null) {
                continue;
            }

            IrisEntity entity = data.getEntityLoader().load(spawn.getEntity());

            if (entity != null && entity.isCompatExcluded()) {
                CompatPools.drop(data, entity, "spawner", getLoadKey(),
                        field + "[" + index + "] " + spawn.getEntity(), drops);
                continue;
            }

            kept.add(spawn);
        }

        return kept;
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
