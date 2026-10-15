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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.terrain.carving.CaveProfileSampler;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/**
 * A {@link FloatingChildBiome} made ready for one world: the noise its styles build (each from the world's floating seed),
 * the edge taper profile, the biome it targets, the carving profile sampler and the objects it hosts. One per decoded entry
 * ({@link FloatingWorld#resolve}).
 */
public final class ResolvedFloatingChild {
    private static final long FOOTPRINT_SALT = 0xF007B17DL;
    private static final long EDGE_VARIATION_SALT = 0xED6E7A9E5L;
    private static final long PICKER_SALT = 0x91C4E72DL;
    private static final long ALTITUDE_SALT = 0xA17DEBBL;
    private static final long TOP_SHAPE_SALT = 0x70970601DEFL;
    private static final long BOTTOM_SALT = 0xB0770075CAFEL;
    private static final long WALL_WARP_SALT = 0xA117BA17E0FL;
    private static final long CARVE_SALT = 0xCA5EC1EE5EL;
    private static final double MIN_SHRINK_FACTOR = 0.01;

    private final FloatingChildBiome settings;
    private final FloatingWorld world;
    private final AtomicCache<Optional<ResourceId>> namedBiome = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> footprintCache = new AtomicCache<>();
    private final AtomicCache<FloatingIslandEdgeProfile> edgeTaperProfileCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> pickerCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> altitudeCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> topShapeCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> bottomCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> wallWarpCache = new AtomicCache<>();
    private final AtomicCache<NoiseSampler> carveCache = new AtomicCache<>();
    private final AtomicCache<CaveProfileSampler> carvingProfileSamplerCache = new AtomicCache<>(true);

    ResolvedFloatingChild(FloatingChildBiome settings, FloatingWorld world) {
        this.settings = settings;
        this.world = world;
    }

    public FloatingChildBiome settings() {
        return settings;
    }

    public int rarity() {
        return settings.rarity();
    }

    public NoiseSampler footprintNoise() {
        return footprintCache.aquire(() -> noise(settings.footprintStyle(), FOOTPRINT_SALT));
    }

    public FloatingIslandEdgeProfile edgeTaperProfile() {
        return edgeTaperProfileCache.aquire(() -> {
            int width = FloatingIslandEdgeProfile.clampWidth(settings.edgeTaperWidth());
            double amplitude = FloatingIslandEdgeProfile.clampVariationAmplitude(settings.edgeTaperVariationAmplitude(), width);
            NoiseSampler variation = amplitude > 0.0D ? noise(settings.edgeTaperVariationStyle(), EDGE_VARIATION_SALT) : null;
            return new FloatingIslandEdgeProfile(width, settings.edgeTaperExponent(), amplitude, variation);
        });
    }

    public NoiseSampler pickerNoise() {
        return pickerCache.aquire(() -> noise(settings.pickerStyle(), PICKER_SALT));
    }

    public NoiseSampler altitudeNoise() {
        return altitudeCache.aquire(() -> noise(settings.altitudeStyle(), ALTITUDE_SALT));
    }

    public NoiseSampler topShapeNoise() {
        return topShapeCache.aquire(() -> noise(settings.topShapeStyle(), TOP_SHAPE_SALT));
    }

    public NoiseSampler bottomNoise() {
        return bottomCache.aquire(() -> noise(settings.bottomStyle(), BOTTOM_SALT));
    }

    public NoiseSampler wallWarpNoise() {
        Optional<NoiseStyle> style = settings.wallWarpStyle();
        return style.isEmpty() ? null : wallWarpCache.aquire(() -> noise(style.get(), WALL_WARP_SALT));
    }

    public NoiseSampler carveNoise() {
        Optional<NoiseStyle> style = settings.carveStyle();
        return style.isEmpty() ? null : carveCache.aquire(() -> noise(style.get(), CARVE_SALT));
    }

    private NoiseSampler noise(NoiseStyle style, long salt) {
        return world.noise().sampler(style, world.baseSeed() ^ salt);
    }

    public CaveProfileSampler carvingProfileSampler() {
        if (!hasCarvingReference()) {
            return null;
        }

        return carvingProfileSamplerCache.aquire(() -> {
            CaveProfile profile = carvingBiome().map(world::caveProfile).orElse(null);
            if (profile == null || !profile.enabled()) {
                return null;
            }

            return new CaveProfileSampler(world.noise(), world.carveSeed(), profile);
        });
    }

    public boolean hasCarvingReference() {
        return !settings.carving().isBlank();
    }

    /** The biome whose cave profile carves the island: the biome of the dimension's carving entry the id names, or the biome the id names. */
    Optional<ResourceId> carvingBiome() {
        String carving = settings.carving();
        Optional<CarvingEntry> entry = world.dimensionCarving(carving);
        if (entry.isEmpty()) {
            return world.host().biome(carving);
        }
        String biomeKey = entry.get().biome().trim();
        return biomeKey.isEmpty() ? Optional.empty() : world.host().biome(biomeKey);
    }

    public KList<ObjectPlacement> resolveTopObjects(ResourceId target) {
        List<ObjectPlacement> surface = settings.inheritObjects() && target != null ? FloatingWorld.surfaceObjects(world.objects(), target) : List.of();
        return topObjects(settings, surface);
    }

    /** The placements on the island's top: the inherited {@code surfaceObjects}, the child's overrides, or both, as its mode says. */
    public static KList<ObjectPlacement> topObjects(FloatingChildBiome settings, List<ObjectPlacement> surfaceObjects) {
        return switch (settings.topObjectMode()) {
            case REPLACE -> new KList<>(settings.topObjectOverrides());
            case MERGE -> {
                KList<ObjectPlacement> merged = new KList<>();
                merged.addAll(surfaceObjects);
                merged.addAll(settings.topObjectOverrides());
                yield merged;
            }
            case INHERIT_ONLY -> new KList<>(surfaceObjects);
        };
    }

    public KList<ObjectPlacement> resolveBottomObjects() {
        return bottomObjects(settings);
    }

    /** The placements hung under the island: the child's overrides, unless its mode only inherits, which hangs nothing. */
    public static KList<ObjectPlacement> bottomObjects(FloatingChildBiome settings) {
        return switch (settings.bottomObjectMode()) {
            case INHERIT_ONLY -> new KList<>();
            case MERGE, REPLACE -> new KList<>(settings.bottomObjectOverrides());
        };
    }

    public boolean hasObjectShrink() {
        double shrink = settings.objectShrinkFactor();
        return shrink > 0 && shrink < 1.0;
    }

    /** How much the island shrinks the objects it hosts. */
    public double shrinkFactor() {
        return Math.max(MIN_SHRINK_FACTOR, Math.min(1.0, settings.objectShrinkFactor()));
    }

    /** The biome the island's palette, decorators and objects come from: the biome the child names, or {@code parent}. */
    public ResourceId realBiome(ResourceId parent) {
        if (settings.biome().isBlank()) {
            return parent;
        }
        return namedBiome.aquire(() -> world.host().biome(settings.biome())).orElse(parent);
    }
}
