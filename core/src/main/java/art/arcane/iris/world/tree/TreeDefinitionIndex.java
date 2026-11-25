package art.arcane.iris.world.tree;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.HashSet;
import java.util.Set;

public final class TreeDefinitionIndex {
    private final Set<String> explicitObjectKeys;

    private TreeDefinitionIndex(Set<String> explicitObjectKeys) {
        this.explicitObjectKeys = Set.copyOf(explicitObjectKeys);
    }

    public static TreeDefinitionIndex build(Engine engine) {
        Set<String> explicitObjectKeys = new HashSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region == null) {
                continue;
            }
            collectPlacements(region.getObjects(), explicitObjectKeys);
        }

        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (biome == null) {
                continue;
            }
            collectPlacements(biome.getObjects(), explicitObjectKeys);
        }

        return new TreeDefinitionIndex(explicitObjectKeys);
    }

    public boolean isTreeMarker(String marker) {
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return false;
        }

        String objectKey = decoded.objectKey();
        if (objectKey.startsWith("procedural/")) {
            return false;
        }
        return objectKey.startsWith("trees/") || explicitObjectKeys.contains(objectKey);
    }

    private static void collectPlacements(Iterable<IrisObjectPlacement> placements, Set<String> objectKeys) {
        if (placements == null) {
            return;
        }
        for (IrisObjectPlacement placement : placements) {
            if (placement == null
                    || placement.getTrees() == null
                    || placement.getTrees().isEmpty()
                    || placement.getPlace() == null) {
                continue;
            }
            for (String objectKey : placement.getPlace()) {
                if (objectKey != null && !objectKey.isBlank()) {
                    objectKeys.add(objectKey);
                }
            }
        }
    }
}
