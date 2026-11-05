package art.arcane.iris.core.service.tree;

import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.core.modules.PlacementMarkers;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.SaplingGrowth;

import java.util.Set;

/** Tells tree placements from others: objects in {@code trees/}, and every object a module says it places as a tree. */
public final class TreeDefinitionIndex {
    private static final String TREE_FOLDER = "trees/";

    private final PackResources resources;
    private final Set<ResourceId> treeObjects;

    private TreeDefinitionIndex(PackResources resources, Set<ResourceId> treeObjects) {
        this.resources = resources;
        this.treeObjects = Set.copyOf(treeObjects);
    }

    public static TreeDefinitionIndex build(Engine engine) {
        SaplingGrowth.TreeObjects trees = ModuleServices.treeObjects(engine);
        return new TreeDefinitionIndex(engine.getData().getPackResources(), trees.objects());
    }

    public boolean isTreeMarker(String marker) {
        PlacementMarkers.Decoded decoded = PlacementMarkers.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return false;
        }

        String objectKey = decoded.objectKey();
        return objectKey.startsWith(TREE_FOLDER) || treeObjects.contains(resources.idOf(objectKey));
    }
}
