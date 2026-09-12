package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StructurePlacementScope {
    private static final List<CachedScopeIndex> SCOPE_INDEXES = new ArrayList<>();

    private StructurePlacementScope() {
    }

    public static KList<IrisStructurePlacement> placementsAt(Engine engine, int chunkX, int chunkZ) {
        Engine activeEngine = Objects.requireNonNull(engine, "Structure placement scope requires an engine");
        IrisComplex complex = activeEngine.getComplex();
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        KList<IrisStructurePlacement> placements = new KList<>();
        Set<IrisStructurePlacement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ScopeIndex index = scopeIndex(activeEngine);
        if (complex != null) {
            if (index.biome()) {
                IrisBiome biome = complex.getTrueBiomeStream().get(blockX, blockZ);
                addUnique(placements, seen, biome == null ? null : biome.getStructures());
            }
            if (index.caveBiome()) {
                IrisBiome caveBiome = complex.getCaveBiomeStream().get(blockX, blockZ);
                addCaveUnique(placements, seen, caveBiome == null ? null : caveBiome.getStructures());
            }
            if (index.region()) {
                IrisRegion region = complex.getRegionStream().get(blockX, blockZ);
                addUnique(placements, seen, region == null ? null : region.getStructures());
            }
        }
        if (index.dimension()) {
            addUnique(placements, seen, activeEngine.getDimension().getStructures());
        }
        return placements;
    }

    private static ScopeIndex scopeIndex(Engine engine) {
        int revision = engine.getCacheID();
        synchronized (SCOPE_INDEXES) {
            for (int i = SCOPE_INDEXES.size() - 1; i >= 0; i--) {
                CachedScopeIndex cached = SCOPE_INDEXES.get(i);
                Engine indexedEngine = cached.engine().get();
                if (indexedEngine == null) {
                    SCOPE_INDEXES.remove(i);
                    continue;
                }
                if (indexedEngine == engine) {
                    if (cached.revision() == revision) {
                        return cached.index();
                    }
                    SCOPE_INDEXES.remove(i);
                    break;
                }
            }
            ScopeIndex index = buildScopeIndex(engine);
            SCOPE_INDEXES.add(new CachedScopeIndex(new WeakReference<>(engine), revision, index));
            return index;
        }
    }

    private static ScopeIndex buildScopeIndex(Engine engine) {
        boolean biome = false;
        boolean caveBiome = false;
        KList<IrisBiome> allBiomes = engine.getAllBiomes();
        if (allBiomes == null) {
            biome = true;
            caveBiome = true;
        } else {
            for (IrisBiome candidate : allBiomes) {
                KList<IrisStructurePlacement> structures = candidate.getStructures();
                if (structures == null || structures.isEmpty()) {
                    continue;
                }
                biome = true;
                for (IrisStructurePlacement placement : structures) {
                    if (placement != null && placement.resolvedAnchor().isCave()) {
                        caveBiome = true;
                        break;
                    }
                }
            }
        }
        boolean region = false;
        if (engine.getDimension() != null) {
            KList<IrisRegion> allRegions = engine.getDimension().getAllRegions(engine);
            if (allRegions == null) {
                region = true;
            } else {
                for (IrisRegion candidate : allRegions) {
                    if (candidate.getStructures() != null && !candidate.getStructures().isEmpty()) {
                        region = true;
                        break;
                    }
                }
            }
        }
        boolean dimension = engine.getDimension() != null
                && engine.getDimension().getStructures() != null
                && !engine.getDimension().getStructures().isEmpty();
        return new ScopeIndex(biome, caveBiome, region, dimension);
    }

    private static void addUnique(KList<IrisStructurePlacement> destination,
                                  Set<IrisStructurePlacement> seen,
                                  KList<IrisStructurePlacement> source) {
        if (source == null) {
            return;
        }
        for (IrisStructurePlacement placement : source) {
            if (placement != null && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    private static void addCaveUnique(KList<IrisStructurePlacement> destination,
                                      Set<IrisStructurePlacement> seen,
                                      KList<IrisStructurePlacement> source) {
        if (source == null) {
            return;
        }
        for (IrisStructurePlacement placement : source) {
            if (placement != null && placement.resolvedAnchor().isCave() && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    private record CachedScopeIndex(WeakReference<Engine> engine, int revision, ScopeIndex index) {
    }

    private record ScopeIndex(boolean biome, boolean caveBiome, boolean region, boolean dimension) {
    }
}
