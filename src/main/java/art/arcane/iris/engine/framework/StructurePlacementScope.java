package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The structure placements that apply at a chunk: its biome's, its cave biome's cave-anchored ones, its region's and
 * the dimension's. Each world keeps which of those levels declare placements at all, so chunks skip the biome and
 * region lookups a pack never needs.
 */
public final class StructurePlacementScope {
    private StructurePlacementScope() {
    }

    /** The placements {@code owner} declares in its {@code "structures"} settings; empty for none. */
    public static List<StructurePlacement> placementsOf(IrisRegistrant owner) {
        if (owner == null) {
            return List.of();
        }
        return owner.getModuleSettings().find(StructurePlacementSettings.class)
                .map(StructurePlacementSettings::placements)
                .orElse(List.of());
    }

    public static KList<StructurePlacement> placementsAt(Engine engine, int chunkX, int chunkZ) {
        Engine activeEngine = Objects.requireNonNull(engine, "Structure placement scope requires an engine");
        IrisComplex complex = activeEngine.getComplex();
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        KList<StructurePlacement> placements = new KList<>();
        Set<StructurePlacement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ScopeIndex index = scopeIndex(activeEngine);
        if (complex != null) {
            if (index.biome()) {
                IrisBiome biome = complex.getTrueBiomeStream().get(blockX, blockZ);
                addUnique(placements, seen, placementsOf(biome));
            }
            if (index.caveBiome()) {
                IrisBiome caveBiome = complex.getCaveBiomeStream().get(blockX, blockZ);
                addCaveUnique(placements, seen, placementsOf(caveBiome));
            }
            if (index.region()) {
                IrisRegion region = complex.getRegionStream().get(blockX, blockZ);
                addUnique(placements, seen, placementsOf(region));
            }
        }
        if (index.dimension()) {
            addUnique(placements, seen, placementsOf(activeEngine.getDimension()));
        }
        return placements;
    }

    private static ScopeIndex scopeIndex(Engine engine) {
        return engine.getGeneration().state(ScopeIndex.class, StructurePlacementScope::buildScopeIndex);
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
                List<StructurePlacement> structures = placementsOf(candidate);
                if (structures.isEmpty()) {
                    continue;
                }
                biome = true;
                caveBiome |= structures.stream().anyMatch(placement -> placement.resolvedAnchor().isCave());
            }
        }
        boolean region = false;
        if (engine.getDimension() != null) {
            KList<IrisRegion> allRegions = engine.getDimension().getAllRegions(engine);
            region = allRegions == null
                    || allRegions.stream().anyMatch(candidate -> !placementsOf(candidate).isEmpty());
        }
        boolean dimension = !placementsOf(engine.getDimension()).isEmpty();
        return new ScopeIndex(biome, caveBiome, region, dimension);
    }

    private static void addUnique(KList<StructurePlacement> destination, Set<StructurePlacement> seen,
                                  List<StructurePlacement> source) {
        for (StructurePlacement placement : source) {
            if (placement != null && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    private static void addCaveUnique(KList<StructurePlacement> destination, Set<StructurePlacement> seen,
                                      List<StructurePlacement> source) {
        for (StructurePlacement placement : source) {
            if (placement != null && placement.resolvedAnchor().isCave() && seen.add(placement)) {
                destination.add(placement);
            }
        }
    }

    private record ScopeIndex(boolean biome, boolean caveBiome, boolean region, boolean dimension) {
    }
}
