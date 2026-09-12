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

package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.collection.KList;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Determines which vanilla/datapack structures can ever generate in a world, using the same biome
 * gate vanilla applies: a structure is reachable when its biome filter intersects the set of vanilla
 * biomes the pack actually produces (its {@code possibleBiomes}). Structures whose required biomes are
 * never produced cannot generate and must not be scanned for by {@code /locate} or {@code /iris find}
 * (an unbounded scan for an absent biome is what stalls the server).
 *
 * <p>The reachable set is fixed per live engine/world and is rebuilt after hotload.
 */
public final class StructureReachability {
    private static final Cache<Engine, Set<String>> REACHABLE_CACHE = Caffeine.newBuilder().weakKeys().build();

    private StructureReachability() {
    }

    public static Set<String> reachableKeys(Engine engine) {
        Engine activeEngine = Objects.requireNonNull(engine, "Structure reachability requires an engine");
        if (activeEngine.getData() == null) {
            throw new IllegalStateException("Structure reachability requires a bound pack data loader");
        }
        return REACHABLE_CACHE.get(activeEngine, ignored -> build(activeEngine));
    }

    public static boolean isReachable(Engine engine, String structureKey) {
        String normalizedStructureKey = normalizeStructureKey(structureKey);
        return reachableKeys(engine).contains(normalizedStructureKey);
    }

    public static void invalidate(Engine engine) {
        if (engine == null) {
            return;
        }
        REACHABLE_CACHE.invalidate(engine);
    }

    private static Set<String> build(Engine engine) {
        IrisWorld world = engine.getWorld();
        if (world == null) {
            throw new IllegalStateException("Structure reachability requires a bound Iris world");
        }
        if (world.platformWorld() == null) {
            throw new IllegalStateException("Structure reachability requires a bound platform world");
        }
        Set<String> reachable = new LinkedHashSet<>();
        for (String key : IrisPlatforms.get().structureHooks().reachableStructureKeys(world.platformWorld())) {
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Platform structure reachability returned a blank registry key");
            }
            reachable.add(key.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(reachable);
    }

    /**
     * The vanilla biome keys (e.g. {@code minecraft:taiga}) from a structure's filter that the pack
     * does not produce. An empty list means the structure is reachable. Used by the structure verify
     * diagnostic to explain why a structure cannot generate.
     */
    public static KList<String> missingBiomeKeys(Engine engine, String structureKey) {
        KList<String> missing = new KList<>();
        Engine activeEngine = Objects.requireNonNull(engine, "Structure biome diagnostics require an engine");
        String normalizedStructureKey = normalizeStructureKey(structureKey);
        IrisWorld world = activeEngine.getWorld();
        if (world == null) {
            throw new IllegalStateException("Structure biome diagnostics require a bound Iris world");
        }
        if (world.platformWorld() == null) {
            throw new IllegalStateException("Structure biome diagnostics require a bound platform world");
        }
        Set<String> possible = new LinkedHashSet<>();
        for (String key : IrisPlatforms.get().structureHooks().possibleBiomeKeys(world.platformWorld())) {
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("Platform structure biome query returned a blank possible-biome key");
            }
            possible.add(key.toLowerCase(Locale.ROOT));
        }
        for (String biomeKey : IrisPlatforms.get().structureHooks().structureBiomeKeys(normalizedStructureKey)) {
            if (biomeKey == null || biomeKey.isBlank()) {
                throw new IllegalStateException("Platform structure biome query returned a blank required-biome key for "
                        + normalizedStructureKey);
            }
            if (!possible.contains(biomeKey.toLowerCase(Locale.ROOT))) {
                missing.add(biomeKey);
            }
        }
        return missing;
    }

    private static String normalizeStructureKey(String structureKey) {
        if (structureKey == null || structureKey.isBlank()) {
            throw new IllegalArgumentException("Registered structure key must not be blank");
        }
        return structureKey.trim().toLowerCase(Locale.ROOT);
    }
}
