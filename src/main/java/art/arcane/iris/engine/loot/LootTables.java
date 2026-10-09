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

package art.arcane.iris.engine.loot;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.object.loot.IrisLootMode;
import art.arcane.iris.engine.object.loot.IrisLootReference;
import art.arcane.iris.engine.object.loot.IrisLootTable;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Which loot tables a place in the world draws from, and the random streams that draw from them: the part of loot
 * that reads the pack's tables, loot references and layout. The streams themselves are
 * {@link LootResolver}'s; here a table is named by its {@linkplain #identity identity}.
 */
public final class LootTables {
    private LootTables() {
    }

    /** What names a table in the random streams: its key, else its name. */
    public static String identity(IrisLootTable table) {
        if (table == null) {
            return "";
        }
        String loadKey = table.getLoadKey();
        if (loadKey != null && !loadKey.isBlank()) {
            return loadKey;
        }
        String name = table.getName();
        return name == null ? "" : name;
    }

    public static RNG rng(long lootSeed, IrisLootTable table, int x, int y, int z) {
        return LootResolver.tableRng(lootSeed, identity(table), x, y, z);
    }

    public static long seed(long lootSeed, IrisLootTable table, int x, int y, int z) {
        return LootResolver.tableSeed(lootSeed, identity(table), x, y, z);
    }

    public static boolean spatialOneIn(long lootSeed, IrisLootTable table, int entryIndex, int x, int y, int z, long rarity) {
        return LootResolver.spatialOneIn(lootSeed, identity(table), entryIndex, x, y, z, rarity);
    }

    /**
     * Adds the tables of the dimension, the region, the surface biome and (below the surface) the cave biome at a
     * column to {@code sources}, as their loot references say, then scales their number by the areas' multipliers.
     * {@code mapper} turns a table into the caller's own source; a null result skips it.
     */
    public static <T> void resolveEnvironmentSources(
            List<T> sources,
            Engine engine,
            RNG rng,
            int x,
            int relativeY,
            int z,
            boolean objectLootDefined,
            Function<IrisLootTable, T> mapper
    ) {
        IrisRegion region = engine.getComplex().getRegionStream().get(x, z);
        IrisBiome surfaceBiome = engine.getComplex().getTrueBiomeStream().get(x, z);
        double terrainHeight = engine.getComplex().getHeightStream().get(x, z);
        IrisBiome contextualBiome = relativeY < terrainHeight
                ? engine.getCaveBiome(x, relativeY, z)
                : surfaceBiome;
        if (contextualBiome == null) {
            contextualBiome = surfaceBiome;
        }

        boolean distinctContextualBiome = !sameBiome(surfaceBiome, contextualBiome);
        double multiplier = engine.getDimension().getLoot().getMultiplier()
                * region.getLoot().getMultiplier()
                * surfaceBiome.getLoot().getMultiplier();
        if (distinctContextualBiome) {
            multiplier *= contextualBiome.getLoot().getMultiplier();
        }

        boolean fallback = !objectLootDefined;
        injectReference(sources, engine.getDimension().getLoot(), engine, mapper, fallback);
        injectReference(sources, region.getLoot(), engine, mapper, fallback);
        injectReference(sources, surfaceBiome.getLoot(), engine, mapper, fallback);
        if (distinctContextualBiome) {
            injectReference(sources, contextualBiome.getLoot(), engine, mapper, fallback);
        }
        LootResolver.scaleSources(sources, multiplier, rng);
    }

    /** Adds {@code additions} to {@code sources} the way {@code mode} says; {@code fallback} is whether nothing else defined a table. */
    public static <T> void injectSources(
            List<T> sources,
            List<? extends T> additions,
            IrisLootMode mode,
            boolean fallback
    ) {
        if (mode == IrisLootMode.FALLBACK && !fallback) {
            return;
        }
        if (mode == IrisLootMode.CLEAR) {
            sources.clear();
            return;
        }
        if (mode == IrisLootMode.REPLACE) {
            sources.clear();
        }
        sources.addAll(additions);
    }

    static boolean sameBiome(IrisBiome first, IrisBiome second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(first.getLoadKey(), second.getLoadKey());
    }

    private static <T> void injectReference(
            List<T> sources,
            IrisLootReference reference,
            Engine engine,
            Function<IrisLootTable, T> mapper,
            boolean fallback
    ) {
        KList<IrisLootTable> tables = reference.getLootTables(engine.getComplex());
        KList<T> additions = new KList<>();
        for (IrisLootTable table : tables) {
            if (table == null) {
                continue;
            }
            T source = mapper.apply(table);
            if (source != null) {
                additions.add(source);
            }
        }
        injectSources(sources, additions, reference.getMode(), fallback);
    }
}
