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

import art.arcane.iris.builtin.loot.LootMode;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which loot tables a place in the world draws from, and the random streams that draw from them: the part of loot
 * that reads the pack's tables, loot settings and layout. The streams themselves are {@link LootResolver}'s; here a
 * table is named by its {@linkplain #identity identity}, the key the pack names it by.
 */
public final class LootTables {
    private static final String VANILLA_IDENTITY_PREFIX = "Vanilla ";

    private LootTables() {
    }

    /** What names a table in the random streams: the key a pack table is named by. */
    public static String identity(String tableKey) {
        return tableKey;
    }

    /** What names a table of the platform in the random streams. */
    public static String platformIdentity(String platformKey) {
        return VANILLA_IDENTITY_PREFIX + platformKey;
    }

    public static RNG rng(long lootSeed, String identity, int x, int y, int z) {
        return LootResolver.tableRng(lootSeed, identity, x, y, z);
    }

    public static long seed(long lootSeed, String identity, int x, int y, int z) {
        return LootResolver.tableSeed(lootSeed, identity, x, y, z);
    }

    public static boolean spatialOneIn(long lootSeed, String identity, int entryIndex, int x, int y, int z, long rarity) {
        return LootResolver.spatialOneIn(lootSeed, identity, entryIndex, x, y, z, rarity);
    }

    /** The declared loot settings; the defaults, which add nothing, when none are declared. */
    private static LootSettings orDefaults(Optional<LootSettings> declared) {
        return declared.orElseGet(LootSettings::new);
    }

    /** The cave biome of a block under the terrain's surface, when it is not the column's surface biome. */
    private static Optional<ResourceId> distinctCaveBiome(TerrainSampler layout, int x, int relativeY, int z) {
        if (relativeY >= layout.surfaceY(x, z) || !layout.bounds().contains(x, relativeY, z)) {
            return Optional.empty();
        }
        ResourceId surface = layout.surfaceBiome(x, z);
        return layout.caveBiome(x, relativeY, z).filter(cave -> !cave.equals(surface));
    }

    /**
     * Adds the tables of the dimension, the region, the surface biome and (below the surface) the cave biome at a
     * column to {@code sources}, as their loot settings say, then scales their number by the areas' multipliers.
     * {@code mapper} turns the key of a table into the caller's own source; a null result skips it.
     */
    public static <T> void resolveEnvironmentSources(
            List<T> sources,
            PackWorld world,
            RNG rng,
            int x,
            int relativeY,
            int z,
            boolean objectLootDefined,
            Function<String, T> mapper
    ) {
        RunningWorld running = world.running().orElseThrow();
        Optional<ResourceId> distinctCaveBiome = distinctCaveBiome(running.layout(), x, relativeY, z);
        SettingsStack<LootSettings> column = running.settings().at(LootSettings.class, x, z);

        LootSettings dimensionLoot = orDefaults(column.dimension());
        LootSettings regionLoot = orDefaults(column.level(Level.REGION));
        LootSettings surfaceLoot = orDefaults(column.level(Level.BIOME));
        double multiplier = dimensionLoot.multiplier() * regionLoot.multiplier() * surfaceLoot.multiplier();
        LootSettings contextualLoot = distinctCaveBiome
                .map(cave -> orDefaults(running.settings().declaredBy(cave, LootSettings.class)))
                .orElse(null);
        if (contextualLoot != null) {
            multiplier *= contextualLoot.multiplier();
        }

        boolean fallback = !objectLootDefined;
        injectReference(sources, dimensionLoot, world, mapper, fallback);
        injectReference(sources, regionLoot, world, mapper, fallback);
        injectReference(sources, surfaceLoot, world, mapper, fallback);
        if (contextualLoot != null) {
            injectReference(sources, contextualLoot, world, mapper, fallback);
        }
        LootResolver.scaleSources(sources, multiplier, rng);
    }

    /** Adds {@code additions} to {@code sources} the way {@code mode} says; {@code fallback} is whether nothing else defined a table. */
    public static <T> void injectSources(
            List<T> sources,
            List<? extends T> additions,
            LootMode mode,
            boolean fallback
    ) {
        if (mode == LootMode.FALLBACK && !fallback) {
            return;
        }
        if (mode == LootMode.CLEAR) {
            sources.clear();
            return;
        }
        if (mode == LootMode.REPLACE) {
            sources.clear();
        }
        sources.addAll(additions);
    }

    private static <T> void injectReference(
            List<T> sources,
            LootSettings loot,
            PackWorld world,
            Function<String, T> mapper,
            boolean fallback
    ) {
        List<T> additions = loot.tables().stream()
                .filter(key -> LootResources.table(world.resources(), key) != null)
                .map(mapper)
                .filter(Objects::nonNull)
                .toList();
        injectSources(sources, additions, loot.mode(), fallback);
    }
}
