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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.engine.object.loot.IObjectLoot;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.service.Loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The loot table an object placement gives to one of its containers: the placement's tables and the platform's own,
 * each with the blocks it applies to and a weight. A table that names the container's exact block wins over one that
 * names the container's kind of block, which wins over one that names no blocks; among equals the weights pick.
 */
final class PlacementLoot {
    private static final String VANILLA_NAMESPACE = "minecraft:";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final char PROPERTIES_START = '[';

    private final IrisData data;
    private final Supplier<Set<String>> platformTables;
    private final long lootSeed;
    private final ModuleLog log;

    /** @param platformTables the keys of the loot tables the platform has, asked for only when a placement names one */
    PlacementLoot(IrisData data, Supplier<Set<String>> platformTables, long lootSeed, ModuleLog log) {
        this.data = data;
        this.platformTables = platformTables;
        this.lootSeed = lootSeed;
        this.log = log;
    }

    /** The platform's loot table keys, which a placement may name, in the form a placement names them. */
    static Set<String> platformTableKeys() {
        return Set.copyOf(IrisPlatforms.get().registries().lootTableKeys());
    }

    /**
     * The table a placement, whose platform tables are {@code platformLoot} and whose pack tables are {@code packLoot},
     * gives {@code container}, a storage chest, at the spot {@code x}, {@code worldY}, {@code z}; null when it gives
     * it none.
     */
    Loot.Source pick(List<? extends IObjectLoot> platformLoot, List<? extends IObjectLoot> packLoot, PlatformBlockState container,
                     RNG rng, int x, int worldY, int z) {
        Candidates candidates = new Candidates(container);
        add(candidates, platformLoot, name -> platformSource(name, x, worldY, z));
        add(candidates, packLoot, this::packSource);
        List<Candidate> pool = candidates.pool();
        Candidate picked = LootResolver.pickWeighted(pool, Candidate::weight, rng);
        return picked == null ? null : picked.source();
    }

    private void add(Candidates candidates, List<? extends IObjectLoot> entries, Function<String, Loot.Source> sourceOf) {
        for (IObjectLoot entry : entries) {
            if (entry == null || entry.getWeight() <= 0) {
                continue;
            }
            Loot.Source source = sourceOf.apply(entry.getName());
            if (source == null) {
                log.warn("Couldn't find loot table " + entry.getName());
                continue;
            }
            candidates.add(entry, data, new Candidate(source, entry.getWeight()));
        }
    }

    private Loot.Source packSource(String key) {
        return LootResources.table(data, key) == null ? null : new Loot.Table(key);
    }

    private Loot.Source platformSource(String name, int x, int worldY, int z) {
        String key = platformKey(name);
        if (key == null || !platformTables.get().contains(key)) {
            return null;
        }
        return new Loot.PlatformTable(key, LootTables.seed(lootSeed, LootTables.platformIdentity(key), x, worldY, z));
    }

    /** A placement's name for a platform table as the platform's key for it, or null for a name that cannot be one. */
    static String platformKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.indexOf(NAMESPACE_SEPARATOR) < 0 ? VANILLA_NAMESPACE + name : name;
    }

    private record Candidate(Loot.Source source, int weight) {
    }

    /** The candidates for one container, in the groups the placement gives them. */
    private static final class Candidates {
        private final String containerKey;
        private final String containerMaterial;
        private final List<Candidate> exact = new ArrayList<>();
        private final List<Candidate> basic = new ArrayList<>();
        private final List<Candidate> global = new ArrayList<>();

        private Candidates(PlatformBlockState container) {
            this.containerKey = container.key();
            this.containerMaterial = materialOf(containerKey);
        }

        void add(IObjectLoot entry, IrisData data, Candidate candidate) {
            List<PlatformBlockState> filter = entry.getFilter(data);
            if (filter.isEmpty()) {
                global.add(candidate);
                return;
            }
            for (PlatformBlockState filterState : filter) {
                if (entry.isExact() && filterState.key().equals(containerKey)) {
                    exact.add(candidate);
                } else if (!entry.isExact() && materialOf(filterState.key()).equals(containerMaterial)) {
                    basic.add(candidate);
                }
            }
        }

        List<Candidate> pool() {
            if (!exact.isEmpty()) {
                return exact;
            }
            return basic.isEmpty() ? global : basic;
        }

        private static String materialOf(String blockKey) {
            int properties = blockKey.indexOf(PROPERTIES_START);
            return properties < 0 ? blockKey : blockKey.substring(0, properties);
        }
    }
}
