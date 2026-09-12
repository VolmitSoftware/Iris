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

package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.context.IrisContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ModdedSpawnTableMerger {
    private final IrisModdedChunkGenerator generator;
    private final ConcurrentHashMap<SpawnBiomeKey, Holder<Biome>> vanillaSpawnBiomes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SpawnTableKey, WeightedList<MobSpawnSettings.SpawnerData>> mergedSpawnTables =
            new ConcurrentHashMap<>();
    private final Set<Integer> initializedRuntimeIdentities = ConcurrentHashMap.newKeySet();

    ModdedSpawnTableMerger(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    void initializeVanillaSpawnBiomes(Registry<Biome> registry) {
        Engine current = generator.engineOrNull();
        if (current == null) {
            return;
        }
        int runtimeIdentity = current.getCacheID();
        if (initializedRuntimeIdentities.contains(runtimeIdentity)) {
            return;
        }
        synchronized (generator) {
            if (initializedRuntimeIdentities.contains(runtimeIdentity)) {
                return;
            }

            try (GenerationSessionLease lease = generator.requireGenerationLease(current, "modded_spawn_biomes");
                 IrisContext.Scope ignored = IrisContext.open(current, lease.sessionId(), null)) {
                IrisDimension hostDimension = current.getDimension();
                addVanillaSpawnBiomes(
                        registry,
                        hostDimension,
                        current.getData(),
                        hostDimension.getReachableBiomes(current),
                        runtimeIdentity
                );
                DimensionStackContext stackContext = current.getDimensionStackContext();
                if (stackContext != null) {
                    for (DimensionTerrainContext terrainContext : stackContext.getLayersBottomToTop()) {
                        if (terrainContext.isSelfReferencing()) {
                            continue;
                        }
                        IrisDimension dimension = terrainContext.getDimension();
                        addVanillaSpawnBiomes(
                                registry,
                                dimension,
                                terrainContext.getData(),
                                dimension.getReachableBiomes(terrainContext),
                                runtimeIdentity
                        );
                    }
                }
                initializedRuntimeIdentities.add(runtimeIdentity);
            }
        }
    }

    private void addVanillaSpawnBiomes(
            Registry<Biome> registry,
            IrisDimension dimension,
            IrisData data,
            Iterable<IrisBiome> biomes,
            int runtimeIdentity
    ) {
        for (IrisBiome irisBiome : biomes) {
            if (irisBiome == null || !irisBiome.isCustom()) {
                continue;
            }
            Holder<Biome> vanillaHolder = resolveBiomeHolder(
                    registry, irisBiome.getVanillaDerivativeKey());
            if (vanillaHolder == null) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                Holder<Biome> customHolder = resolveBiomeHolder(
                        registry,
                        data.customBiomeResourceKey(dimension, customBiome)
                );
                if (customHolder == null) {
                    continue;
                }
                vanillaSpawnBiomes.putIfAbsent(
                        new SpawnBiomeKey(runtimeIdentity, customHolder.value()),
                        vanillaHolder
                );
            }
        }
    }

    Holder<Biome> vanillaSpawnBiome(Biome biome) {
        Engine current = generator.engineOrNull();
        return current == null
                ? null
                : vanillaSpawnBiomes.get(new SpawnBiomeKey(current.getCacheID(), biome));
    }

    WeightedList<MobSpawnSettings.SpawnerData> mergedSpawnTable(
            Engine engine,
            Biome biome,
            Biome vanillaBiome,
            MobCategory category,
            WeightedList<MobSpawnSettings.SpawnerData> vanillaSpawns,
            WeightedList<MobSpawnSettings.SpawnerData> explicitSpawns) {
        SpawnTableKey key = new SpawnTableKey(engine.getCacheID(), biome, vanillaBiome, category);
        return mergedSpawnTables.computeIfAbsent(
                key,
                ignored -> NativeSpawnTableMerger.merge(vanillaSpawns, explicitSpawns)
        );
    }

    void evictRuntime(int runtimeIdentity) {
        vanillaSpawnBiomes.keySet().removeIf(key -> key.runtimeIdentity() == runtimeIdentity);
        mergedSpawnTables.keySet().removeIf(key -> key.runtimeIdentity() == runtimeIdentity);
        initializedRuntimeIdentities.remove(runtimeIdentity);
    }

    Holder<Biome> resolveBiomeHolder(Registry<Biome> registry, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        Optional<Holder.Reference<Biome>> reference = registry.get(identifier);
        return reference.<Holder<Biome>>map((Holder.Reference<Biome> value) -> value).orElse(null);
    }

    void resetVanillaSpawnBiomes() {
        synchronized (generator) {
            vanillaSpawnBiomes.clear();
            mergedSpawnTables.clear();
            initializedRuntimeIdentities.clear();
        }
    }

    private record SpawnBiomeKey(int runtimeIdentity, Biome biome) {
    }

    private record SpawnTableKey(int runtimeIdentity, Biome biome, Biome vanillaBiome, MobCategory category) {
    }
}
