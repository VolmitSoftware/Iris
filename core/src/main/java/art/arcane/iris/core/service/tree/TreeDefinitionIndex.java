package art.arcane.iris.core.service.tree;

import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.core.modules.PlacementMarkers;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.SaplingGrowth;

import java.util.Set;

public final class TreeDefinitionIndex {
    private static final String PROCEDURAL_TREE_PREFIX = "procedural/tree/";

    private final PackResources resources;
    private final Set<ResourceId> explicitObjects;
    private final Set<ResourceId> proceduralTrees;

    private TreeDefinitionIndex(PackResources resources, Set<ResourceId> explicitObjects, Set<ResourceId> proceduralTrees) {
        this.resources = resources;
        this.explicitObjects = Set.copyOf(explicitObjects);
        this.proceduralTrees = Set.copyOf(proceduralTrees);
    }

    public static TreeDefinitionIndex build(Engine engine) {
        SaplingGrowth.TreeObjects trees = ModuleServices.treeObjects(engine);
        return new TreeDefinitionIndex(engine.getData().getPackResources(), trees.objects(), trees.procedural());
    }

    public boolean isTreeMarker(String marker) {
        PlacementMarkers.Decoded decoded = PlacementMarkers.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return false;
        }

        String objectKey = decoded.objectKey();
        if (objectKey.startsWith(PROCEDURAL_TREE_PREFIX)) {
            return proceduralTrees.contains(resources.idOf(objectKey));
        }
        if (objectKey.startsWith("procedural/")) {
            return false;
        }
        return objectKey.startsWith("trees/") || explicitObjects.contains(resources.idOf(objectKey));
    }
}
