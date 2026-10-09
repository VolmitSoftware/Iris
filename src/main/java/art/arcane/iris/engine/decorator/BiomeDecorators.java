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

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the decorant module keeps for one world: the decorators of its biomes made ready ({@link ResolvedDecorator}),
 * sorted into the places they decorate, and how loaded the busiest biome is. Built from the biomes' {@link DecorantSettings}
 * when first asked for, or ahead of generation by {@link #warm()}.
 *
 * <p>A decorator's noise seed is a function of the world's decorator seed, its biome's key and its place in the biome's
 * list, so it does not depend on which column or which warm-up reached it first.
 */
public final class BiomeDecorators {
    private static final ResolvedDecorator[] NO_DECORATORS = new ResolvedDecorator[0];
    private static final int COLUMNS_PER_CHUNK = 256;
    private static final long KEY_SEED_FACTOR = 0x9E3779B97F4A7C15L;
    private static final long INDEX_SEED_FACTOR = 0xC2B2AE3D27D4EB4FL;

    private final Engine engine;
    private final Map<DecorantSettings, Map<DecorationPart, ResolvedDecorator[]>> decorators = new ConcurrentHashMap<>();
    private volatile Double busiestBiomeLoad;

    /** @param world a world of an Iris engine */
    BiomeDecorators(PackWorld world) {
        this(IrisPackWorld.of(world).engine());
    }

    BiomeDecorators(Engine engine) {
        this.engine = engine;
    }

    /** The world {@code engine} generates now. */
    public static BiomeDecorators of(Engine engine) {
        return engine.getGeneration().state(BiomeDecorators.class, BiomeDecorators::new);
    }

    /** The world's decorators, kept in its state store. */
    public static BiomeDecorators of(PackWorld world) {
        return world.state(BiomeDecorators.class, BiomeDecorators::new);
    }

    /** Whether the biome declares any decorator. */
    public boolean hasDecorators(IrisBiome biome) {
        DecorantSettings settings = settingsOf(biome);
        return settings != null && !settings.decorators().isEmpty();
    }

    /** The biome's decorators for {@code part}, in the order the biome lists them. */
    ResolvedDecorator[] decorators(IrisBiome biome, DecorationPart part) {
        DecorantSettings settings = settingsOf(biome);
        if (settings == null) {
            return NO_DECORATORS;
        }
        return decorators.computeIfAbsent(settings, declared -> resolve(biome.getLoadKey(), declared)).getOrDefault(part, NO_DECORATORS);
    }

    /** Whether the biome has a decorator for {@code part}. */
    public boolean hasDecorators(IrisBiome biome, DecorationPart part) {
        return decorators(biome, part).length > 0;
    }

    /** Resolves every decorator of every biome the dimension reaches and builds its noise, so that generation finds them ready. */
    public void warm() {
        List<IrisBiome> biomes = engine.getAllBiomes();
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        for (IrisBiome biome : biomes) {
            for (DecorationPart part : DecorationPart.values()) {
                for (ResolvedDecorator decorator : decorators(biome, part)) {
                    decorator.warm();
                }
            }
        }
    }

    /** The most decorating any reachable biome does, as the sum of its decorators' chances times their stack heights. */
    public double busiestBiomeLoad() {
        Double cached = busiestBiomeLoad;
        if (cached != null) {
            return cached;
        }

        double busiest = 0D;
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            busiest = Math.max(busiest, load(biome));
        }
        busiestBiomeLoad = busiest;
        return busiest;
    }

    /** How loaded the biome is with decorators: the sum of their chances times their stack heights, at least one block each. */
    public double load(IrisBiome biome) {
        DecorantSettings settings = settingsOf(biome);
        if (settings == null) {
            return 0D;
        }

        double load = 0D;
        for (Decorator decorator : settings.decorators()) {
            load += Math.max(decorator.stackMax(), 1) * decorator.chance();
        }
        return load;
    }

    /** How strongly studio's decorator load view colours the biome: the sum of its decorators' chances over a chunk's columns. */
    public double colourLoad(IrisBiome biome) {
        DecorantSettings settings = settingsOf(biome);
        if (settings == null) {
            return 0D;
        }

        double load = 0D;
        for (Decorator decorator : settings.decorators()) {
            load += decorator.chance() * Math.min(1, decorator.stackMax()) * COLUMNS_PER_CHUNK;
        }
        return load;
    }

    private static DecorantSettings settingsOf(IrisBiome biome) {
        return biome.getModuleSettings().find(DecorantSettings.class).orElse(null);
    }

    private Map<DecorationPart, ResolvedDecorator[]> resolve(String biomeKey, DecorantSettings settings) {
        Map<DecorationPart, List<ResolvedDecorator>> staging = new EnumMap<>(DecorationPart.class);
        List<Decorator> declared = settings.decorators();
        for (int index = 0; index < declared.size(); index++) {
            Decorator decorator = declared.get(index);
            if (decorator == null) {
                continue;
            }
            ResolvedDecorator resolved = ResolvedDecorator.of(decorator, engine, decoratorSeed(biomeKey, index));
            staging.computeIfAbsent(decorator.partOf(), part -> new ArrayList<>()).add(resolved);
        }

        Map<DecorationPart, ResolvedDecorator[]> buckets = new EnumMap<>(DecorationPart.class);
        staging.forEach((part, list) -> buckets.put(part, list.toArray(NO_DECORATORS)));
        return buckets;
    }

    private long decoratorSeed(String biomeKey, int index) {
        return engine.getSeedManager().getDecorator() + (biomeKey.hashCode() * KEY_SEED_FACTOR) + (index * INDEX_SEED_FACTOR);
    }
}
