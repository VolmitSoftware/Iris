package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.prefab.IrisObject;

import java.util.Objects;

public interface StructureGraphResolver {
    IrisJigsawPool loadPool(String key);

    IrisJigsawPiece loadPiece(String key);

    IrisObject loadObject(String key);

    static StructureGraphResolver forData(IrisData data) {
        return new IrisDataStructureGraphResolver(Objects.requireNonNull(data));
    }

    static StructureGraphResolver forCompiledGraph(CompiledStructureGraph graph) {
        return new CompiledStructureGraphResolver(Objects.requireNonNull(graph));
    }
}

final class IrisDataStructureGraphResolver implements StructureGraphResolver {
    private final IrisData data;

    IrisDataStructureGraphResolver(IrisData data) {
        this.data = data;
    }

    @Override
    public IrisJigsawPool loadPool(String key) {
        return StructureResources.pool(data.getPackResources(), key);
    }

    @Override
    public IrisJigsawPiece loadPiece(String key) {
        return StructureResources.piece(data.getPackResources(), key);
    }

    @Override
    public IrisObject loadObject(String key) {
        return data.load(IrisObject.class, key, false);
    }
}

final class CompiledStructureGraphResolver implements StructureGraphResolver {
    private final CompiledStructureGraph graph;

    CompiledStructureGraphResolver(CompiledStructureGraph graph) {
        this.graph = graph;
    }

    @Override
    public IrisJigsawPool loadPool(String key) {
        return graph.getPools().get(normalize(key));
    }

    @Override
    public IrisJigsawPiece loadPiece(String key) {
        return graph.getPieces().get(normalize(key));
    }

    @Override
    public IrisObject loadObject(String key) {
        return graph.getObjects().get(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim();
    }
}
