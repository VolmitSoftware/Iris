package art.arcane.iris.world.tree;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.HashSet;
import java.util.Set;

public final class TreeDefinitionIndex {
    private static final String PROCEDURAL_TREE_PREFIX = "procedural/tree/";

    private final Set<String> explicitObjectKeys;
    private final Set<String> proceduralTreeKeys;

    private TreeDefinitionIndex(Set<String> explicitObjectKeys, Set<String> proceduralTreeKeys) {
        this.explicitObjectKeys = Set.copyOf(explicitObjectKeys);
        this.proceduralTreeKeys = Set.copyOf(proceduralTreeKeys);
    }

    public static TreeDefinitionIndex build(Engine engine) {
        Set<String> explicitObjectKeys = new HashSet<>();
        Set<String> proceduralTreeKeys = new HashSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region == null) {
                continue;
            }
            collectPlacements(region.getObjects(), explicitObjectKeys);
            collectProceduralTrees(region.getProceduralObjects(), proceduralTreeKeys);
        }

        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            if (biome == null) {
                continue;
            }
            collectPlacements(biome.getObjects(), explicitObjectKeys);
            collectProceduralTrees(biome.getProceduralObjects(), proceduralTreeKeys);
        }

        return new TreeDefinitionIndex(explicitObjectKeys, proceduralTreeKeys);
    }

    public boolean isTreeMarker(String marker) {
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return false;
        }

        String objectKey = decoded.objectKey();
        if (objectKey.startsWith(PROCEDURAL_TREE_PREFIX)) {
            return proceduralTreeKeys.contains(objectKey);
        }
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

    private static void collectProceduralTrees(IrisProceduralObjects proceduralObjects, Set<String> objectKeys) {
        if (proceduralObjects == null || proceduralObjects.getTrees() == null) {
            return;
        }
        for (IrisProceduralTree tree : proceduralObjects.getTrees()) {
            if (tree == null || tree.getName() == null || tree.getName().isBlank()) {
                continue;
            }
            int variants = Math.max(1, tree.getVariants());
            for (int index = 0; index < variants; index++) {
                objectKeys.add(tree.getVariantLoadKey(index));
            }
        }
    }
}
