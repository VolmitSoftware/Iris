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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.modules.carve.CarvingEntry;
import art.arcane.volmlib.util.cache.AtomicCache;
import com.volmit.ModuleLog;
import com.volmit.config.Range;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import com.volmit.world.PackBiome;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link CarvingEntry} made ready for one world: the biome it names, found in the pack when first asked for, and the
 * noise that splits it into child entries, built once per seed. What the entry describes stays in the settings.
 */
public final class ResolvedCarvingEntry {
    private static final long CHILD_GENERATOR_SALT = 2137L;
    private static final int CHILD_GENERATOR_SIGNATURE = 2137;
    private static final double MIN_CHILD_SCALE = 0.0001D;

    private final CarvingEntry settings;
    private final AtomicCache<PackBiome> realBiome = new AtomicCache<>(true);
    private final Map<Long, NoiseSampler> childGenerators = new ConcurrentHashMap<>();

    ResolvedCarvingEntry(CarvingEntry settings) {
        this.settings = settings;
    }

    public String id() {
        return settings.id();
    }

    public boolean enabled() {
        return settings.enabled();
    }

    /** The biome key the entry names, as the pack wrote it. */
    public String biomeKey() {
        return settings.biome();
    }

    public Range worldYRange() {
        return settings.worldYRange();
    }

    public List<String> children() {
        return settings.children();
    }

    public int childRecursionDepth() {
        return settings.childRecursionDepth();
    }

    /** The pack biome the entry names; null when it names none or the pack has no such biome. */
    public PackBiome realBiome(PackResources resources, ModuleLog log) {
        return realBiome.aquire(() -> {
            String biomeKey = settings.biome();
            if (biomeKey.isBlank()) {
                return null;
            }

            try {
                return resources.find(PackResources.BIOMES, resources.idOf(biomeKey.trim())).orElse(null);
            } catch (IllegalStateException unreadable) {
                log.warnOnce("carving-biome:" + biomeKey, "The biome " + biomeKey + " of a carving entry cannot be loaded: " + unreadable.getMessage());
                return null;
            }
        });
    }

    /** The noise that picks between the entry and its children, seeded by {@code seed} and the entry's id. */
    public NoiseSampler childrenGenerator(long seed, PackResources resources) {
        long idHash = settings.id().trim().hashCode();
        long generatorSeed = seed ^ (idHash << 32) ^ CHILD_GENERATOR_SALT;
        return childGenerators.computeIfAbsent(generatorSeed, key -> {
            double scale = Math.max(MIN_CHILD_SCALE, settings.childShrinkFactor());
            return resources.noise().sampler(settings.childStyle(), key + CHILD_GENERATOR_SIGNATURE).scaled(scale);
        });
    }
}
