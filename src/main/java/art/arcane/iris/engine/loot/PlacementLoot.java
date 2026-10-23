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

import art.arcane.iris.builtin.loot.LootResolver;
import art.arcane.iris.builtin.loot.ObjectLootEntry;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.resource.PackResources;
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

    private final PackResources resources;
    private final Supplier<Set<String>> platformTables;
    private final long lootSeed;
    private final ModuleLog log;

    /** @param platformTables the keys of the loot tables the platform has, asked for only when a placement names one */
    PlacementLoot(PackResources resources, Supplier<Set<String>> platformTables, long lootSeed, ModuleLog log) {
        this.resources = resources;
        this.platformTables = platformTables;
        this.lootSeed = lootSeed;
        this.log = log;
    }

    /**
     * The table a placement, whose platform tables are {@code platformLoot} and whose pack tables are {@code packLoot},
     * gives {@code container}, a storage chest, at the spot {@code x}, {@code worldY}, {@code z}; null when it gives
     * it none.
     */
    Loot.Source pick(List<? extends ObjectLootEntry> platformLoot, List<? extends ObjectLootEntry> packLoot, BlockState container,
                     RNG rng, int x, int worldY, int z) {
        Candidates candidates = new Candidates(resources.blocks().resolve(container));
        add(candidates, platformLoot, name -> platformSource(name, x, worldY, z));
        add(candidates, packLoot, this::packSource);
        List<Candidate> pool = candidates.pool();
        Candidate picked = LootResolver.pickWeighted(pool, Candidate::weight, rng);
        return picked == null ? null : picked.source();
    }

    private void add(Candidates candidates, List<? extends ObjectLootEntry> entries, Function<String, Loot.Source> sourceOf) {
        for (ObjectLootEntry entry : entries) {
            if (entry == null || entry.weight() <= 0) {
                continue;
            }
            Loot.Source source = sourceOf.apply(entry.name());
            if (source == null) {
                log.warn("Couldn't find loot table " + entry.name());
                continue;
            }
            candidates.add(entry, resources, new Candidate(source, entry.weight()));
        }
    }

    private Loot.Source packSource(String key) {
        return LootResources.table(resources, key) == null ? null : new Loot.Table(resources.idOf(key));
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

        private Candidates(BlockState container) {
            this.containerKey = container.key();
            this.containerMaterial = materialOf(containerKey);
        }

        void add(ObjectLootEntry entry, PackResources resources, Candidate candidate) {
            List<BlockSpec> filter = entry.filter();
            if (filter.isEmpty()) {
                global.add(candidate);
                return;
            }
            for (BlockSpec filterSpec : filter) {
                String filterKey = resources.blocks().resolve(filterSpec).key();
                if (entry.exact() && filterKey.equals(containerKey)) {
                    exact.add(candidate);
                } else if (!entry.exact() && materialOf(filterKey).equals(containerMaterial)) {
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
