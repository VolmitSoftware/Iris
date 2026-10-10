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

import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.terrain.carving.CaveProfiles;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObjectScale;
import art.arcane.iris.engine.object.terrain.carving.ResolvedCarvingEntry;
import art.arcane.iris.engine.object.terrain.carving.CarvingBands;
import art.arcane.iris.engine.object.terrain.carving.CarvingResolver;
import art.arcane.iris.engine.object.terrain.carving.IrisCaveProfileSampler;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * A {@link FloatingChildBiome} made ready for one engine: the noise its styles build (each from the world seed it is first
 * asked with), the edge taper profile, the biome it targets, the carving profile sampler and the objects it hosts. The
 * settings class held these on itself; the engine now keeps one of these per decoded entry ({@link FloatingChildren}).
 */
public final class ResolvedFloatingChild implements IRare {
    private final FloatingChildBiome settings;
    private final IrisGeneratorStyle footprintStyle;
    private final IrisGeneratorStyle edgeTaperVariationStyle;
    private final IrisGeneratorStyle pickerStyle;
    private final IrisGeneratorStyle altitudeStyle;
    private final IrisGeneratorStyle topShapeStyle;
    private final IrisGeneratorStyle bottomStyle;
    private final IrisGeneratorStyle wallWarpStyle;
    private final IrisGeneratorStyle carveStyle;
    private final AtomicCache<IrisBiome> resolvedBiome = new AtomicCache<>();
    private final AtomicCache<CNG> footprintCache = new AtomicCache<>();
    private final AtomicCache<FloatingIslandEdgeProfile> edgeTaperProfileCache = new AtomicCache<>();
    private final AtomicCache<CNG> pickerCache = new AtomicCache<>();
    private final AtomicCache<CNG> altitudeCache = new AtomicCache<>();
    private final AtomicCache<CNG> topShapeCache = new AtomicCache<>();
    private final AtomicCache<CNG> bottomCache = new AtomicCache<>();
    private final AtomicCache<CNG> wallWarpCache = new AtomicCache<>();
    private final AtomicCache<CNG> carveCache = new AtomicCache<>();
    private final AtomicCache<IrisCaveProfileSampler> carvingProfileSamplerCache = new AtomicCache<>(true);
    private final AtomicCache<IrisObjectScale> shrinkScaleCache = new AtomicCache<>();

    public ResolvedFloatingChild(FloatingChildBiome settings) {
        this.settings = settings;
        this.footprintStyle = IrisValues.generatorStyle(settings.footprintStyle());
        this.edgeTaperVariationStyle = IrisValues.generatorStyle(settings.edgeTaperVariationStyle());
        this.pickerStyle = IrisValues.generatorStyle(settings.pickerStyle());
        this.altitudeStyle = IrisValues.generatorStyle(settings.altitudeStyle());
        this.topShapeStyle = IrisValues.generatorStyle(settings.topShapeStyle());
        this.bottomStyle = IrisValues.generatorStyle(settings.bottomStyle());
        this.wallWarpStyle = settings.wallWarpStyle().map(IrisValues::generatorStyle).orElse(null);
        this.carveStyle = settings.carveStyle().map(IrisValues::generatorStyle).orElse(null);
    }

    public FloatingChildBiome settings() {
        return settings;
    }

    @Override
    public int getRarity() {
        return settings.rarity();
    }

    public CNG getFootprintCng(long baseSeed, IrisData data) {
        return footprintCache.aquire(() -> footprintStyle.create(new RNG(baseSeed ^ 0xF007B17DL), data));
    }

    public FloatingIslandEdgeProfile getEdgeTaperProfile(long baseSeed, IrisData data) {
        return edgeTaperProfileCache.aquire(() -> {
            int width = FloatingIslandEdgeProfile.clampWidth(settings.edgeTaperWidth());
            double amplitude = FloatingIslandEdgeProfile.clampVariationAmplitude(
                    settings.edgeTaperVariationAmplitude(), width);
            CNG variation = amplitude > 0.0D
                    ? edgeTaperVariationStyle.create(new RNG(baseSeed ^ 0xED6E7A9E5L), data)
                    : null;
            return new FloatingIslandEdgeProfile(width, settings.edgeTaperExponent(), amplitude, variation);
        });
    }

    public CNG getPickerCng(long baseSeed, IrisData data) {
        return pickerCache.aquire(() -> pickerStyle.create(new RNG(baseSeed ^ 0x91C4E72DL), data));
    }

    public CNG getAltitudeCng(long baseSeed, IrisData data) {
        return altitudeCache.aquire(() -> altitudeStyle.create(new RNG(baseSeed ^ 0xA17DEBBL), data));
    }

    public CNG getTopShapeCng(long baseSeed, IrisData data) {
        return topShapeCache.aquire(() -> topShapeStyle.create(new RNG(baseSeed ^ 0x70970601DEFL), data));
    }

    public CNG getBottomCng(long baseSeed, IrisData data) {
        return bottomCache.aquire(() -> bottomStyle.create(new RNG(baseSeed ^ 0xB0770075CAFEL), data));
    }

    public CNG getWallWarpCng(long baseSeed, IrisData data) {
        if (wallWarpStyle == null) {
            return null;
        }
        return wallWarpCache.aquire(() -> wallWarpStyle.create(new RNG(baseSeed ^ 0xA117BA17E0FL), data));
    }

    public CNG getCarveCng(long baseSeed, IrisData data) {
        if (carveStyle == null) {
            return null;
        }
        return carveCache.aquire(() -> carveStyle.create(new RNG(baseSeed ^ 0xCA5EC1EE5EL), data));
    }

    public IrisCaveProfileSampler getCarvingProfileSampler(Engine engine, IrisData data) {
        if (!hasCarvingReference()) {
            return null;
        }

        return carvingProfileSamplerCache.aquire(() -> {
            IrisBiome resolved = resolveCarvingBiome(settings.carving(), engine, data);
            CaveProfile profile = resolved == null ? null : CaveProfiles.declaredBy(resolved);
            if (profile == null || !profile.enabled()) {
                return null;
            }

            return new IrisCaveProfileSampler(engine, profile);
        });
    }

    public boolean hasCarvingReference() {
        String carving = settings.carving();
        return !carving.isBlank();
    }

    static IrisBiome resolveCarvingBiome(String carving, Engine engine, IrisData data) {
        if (carving == null || carving.isBlank() || engine == null || data == null) {
            return null;
        }

        ResolvedCarvingEntry entry = CarvingBands.of(engine).index().get(carving);
        if (entry != null) {
            return CarvingResolver.resolveEntryBiome(engine, entry);
        }

        if (data.getBiomeLoader() == null) {
            return null;
        }

        return data.getBiomeLoader().load(carving);
    }

    public KList<IrisObjectPlacement> resolveTopObjects(IrisBiome target) {
        KList<IrisObjectPlacement> surfaceObjects = (settings.inheritObjects() && target != null) ? target.getSurfaceObjects() : new KList<>();
        return resolveTopObjectsFromSurface(surfaceObjects);
    }

    KList<IrisObjectPlacement> resolveTopObjectsFromSurface(KList<IrisObjectPlacement> surfaceObjects) {
        return switch (settings.topObjectMode()) {
            case REPLACE -> new KList<>(settings.topObjectOverrides());
            case MERGE -> {
                KList<IrisObjectPlacement> merged = new KList<>();
                merged.addAll(surfaceObjects);
                merged.addAll(settings.topObjectOverrides());
                yield merged;
            }
            case INHERIT_ONLY -> surfaceObjects;
        };
    }

    public KList<IrisObjectPlacement> resolveBottomObjects(IrisBiome target) {
        return switch (settings.bottomObjectMode()) {
            case INHERIT_ONLY -> new KList<>();
            case MERGE, REPLACE -> new KList<>(settings.bottomObjectOverrides());
        };
    }

    public boolean hasObjectShrink() {
        double shrink = settings.objectShrinkFactor();
        return shrink > 0 && shrink < 1.0;
    }

    public IrisObjectScale getShrinkScale() {
        return shrinkScaleCache.aquire(() -> {
            IrisObjectScale s = new IrisObjectScale();
            s.setSize(Math.max(0.01, Math.min(1.0, settings.objectShrinkFactor())));
            return s;
        });
    }

    public IrisBiome getRealBiome(IrisBiome parent, IrisData data) {
        return resolvedBiome.aquire(() -> {
            String biome = settings.biome();
            if (biome.isBlank() || biome.equals(parent.getLoadKey())) {
                return parent;
            }

            IrisBiome loaded = data.getBiomeLoader().load(biome);
            if (loaded == null) {
                return parent;
            }

            return loaded;
        });
    }
}
