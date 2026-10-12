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
package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import com.volmit.config.FeatureSettings;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the decorant module keeps for one world: the decorators of its biomes made ready ({@link ResolvedDecorator}), sorted into
 * the places they decorate. Built from the biomes' {@link DecorantSettings} when first asked for, or ahead of generation by
 * {@link #warm()}.
 *
 * <p>A decorator's noise seed is a function of the world's decorator seed, its biome's key and its place in the biome's
 * list, so it does not depend on which column or which warm-up reached it first.
 */
public final class BiomeDecorators {
    private static final ResolvedDecorator[] NO_DECORATORS = new ResolvedDecorator[0];
    private static final String BIOME_PATH_PREFIX = "biome/";
    private static final long KEY_SEED_FACTOR = 0x9E3779B97F4A7C15L;
    private static final long INDEX_SEED_FACTOR = 0xC2B2AE3D27D4EB4FL;

    private final PackWorld world;
    private final PackResources resources;
    private final ModuleLog log;
    private final long decoratorSeed;
    private final Map<DecorantSettings, Map<DecorationPart, ResolvedDecorator[]>> decorators = new ConcurrentHashMap<>();

    BiomeDecorators(PackWorld world) {
        this.world = world;
        this.resources = world.resources();
        this.log = world.log();
        this.decoratorSeed = IrisSeed.DECORATOR.of(world);
    }

    /** The world's decorators, kept in its state store. */
    public static BiomeDecorators of(PackWorld world) {
        return world.state(BiomeDecorators.class, BiomeDecorators::new);
    }

    /** The key of the biome a settings entry was declared by, such as {@code desert/dunes} for {@code overworld:biome/desert/dunes}. */
    public static String biomeKey(ResourceId source) {
        String path = source.path();
        return path.startsWith(BIOME_PATH_PREFIX) ? path.substring(BIOME_PATH_PREFIX.length()) : path;
    }

    /** The biome of the entry a column's settings gave, which is the column's surface biome; null when the biome declares nothing. */
    public static DecoratedBiome biomeOf(FeatureSettings settings, int worldX, int worldZ, com.volmit.world.BiomeKind kind) {
        List<Entry<DecorantSettings>> entries = settings.at(DecorantSettings.class, worldX, worldZ);
        if (entries.isEmpty()) {
            return null;
        }
        Entry<DecorantSettings> entry = entries.getLast();
        return new DecoratedBiome(biomeKey(entry.source()), entry.value(), kind);
    }

    /** The log of the module that decorates, which the decorators write to. */
    ModuleLog log() {
        return log;
    }

    /** Whether the biome declares any decorator. */
    public boolean hasDecorators(DecoratedBiome biome) {
        return biome != null && biome.settings() != null && !biome.settings().decorators().isEmpty();
    }

    /** The biome's decorators for {@code part}, in the order the biome lists them. */
    ResolvedDecorator[] decorators(DecoratedBiome biome, DecorationPart part) {
        if (biome == null || biome.settings() == null) {
            return NO_DECORATORS;
        }
        return decorators.computeIfAbsent(biome.settings(), declared -> resolve(biome.key(), declared)).getOrDefault(part, NO_DECORATORS);
    }

    /** Whether the biome has a decorator for {@code part}. */
    public boolean hasDecorators(DecoratedBiome biome, DecorationPart part) {
        return decorators(biome, part).length > 0;
    }

    /** Resolves every decorator of every biome the dimension reaches and builds its noise, so that generation finds them ready. */
    public void warm() {
        List<Entry<DecorantSettings>> declared = new ArrayList<>(world.settings().declared(DecorantSettings.class));
        declared.sort(Comparator.comparing(entry -> biomeKey(entry.source())));
        for (Entry<DecorantSettings> entry : declared) {
            DecoratedBiome biome = new DecoratedBiome(biomeKey(entry.source()), entry.value(), null);
            for (DecorationPart part : DecorationPart.values()) {
                for (ResolvedDecorator decorator : decorators(biome, part)) {
                    decorator.warm();
                }
            }
        }
    }

    private Map<DecorationPart, ResolvedDecorator[]> resolve(String biomeKey, DecorantSettings settings) {
        Map<DecorationPart, List<ResolvedDecorator>> staging = new EnumMap<>(DecorationPart.class);
        List<Decorator> declared = settings.decorators();
        for (int index = 0; index < declared.size(); index++) {
            Decorator decorator = declared.get(index);
            if (decorator == null) {
                continue;
            }
            ResolvedDecorator resolved = ResolvedDecorator.of(decorator, resources, seedOf(biomeKey, index), log, biomeKey);
            staging.computeIfAbsent(decorator.partOf(), part -> new ArrayList<>()).add(resolved);
        }

        Map<DecorationPart, ResolvedDecorator[]> buckets = new EnumMap<>(DecorationPart.class);
        staging.forEach((part, list) -> buckets.put(part, list.toArray(NO_DECORATORS)));
        return buckets;
    }

    private long seedOf(String biomeKey, int index) {
        return decoratorSeed + (biomeKey.hashCode() * KEY_SEED_FACTOR) + (index * INDEX_SEED_FACTOR);
    }
}
