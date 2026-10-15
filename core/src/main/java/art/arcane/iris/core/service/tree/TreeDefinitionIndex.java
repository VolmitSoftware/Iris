package art.arcane.iris.core.service.tree;

import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.builtin.structure.StructurePlacementMarker;
import com.volmit.service.SaplingGrowth;

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
        SaplingGrowth.TreeObjects trees = ModuleServices.treeObjects(engine);
        return new TreeDefinitionIndex(trees.objects(), trees.procedural());
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
}
