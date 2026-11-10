package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import com.volmit.resource.PackResources;

import java.util.Objects;

public interface StructureGraphResolver {
    IrisJigsawPool loadPool(String key);

    IrisJigsawPiece loadPiece(String key);

    /** The declared size of the pack object {@code key} names; null when the pack has none. */
    ObjectFrame loadObject(String key);

    static StructureGraphResolver forPack(PackResources pack) {
        return new PackStructureGraphResolver(Objects.requireNonNull(pack));
    }

    static StructureGraphResolver forCompiledGraph(CompiledStructureGraph graph) {
        return new CompiledStructureGraphResolver(Objects.requireNonNull(graph));
    }
}

final class PackStructureGraphResolver implements StructureGraphResolver {
    private final PackResources pack;

    PackStructureGraphResolver(PackResources pack) {
        this.pack = pack;
    }

    @Override
    public IrisJigsawPool loadPool(String key) {
        return StructureResources.pool(pack, key);
    }

    @Override
    public IrisJigsawPiece loadPiece(String key) {
        return StructureResources.piece(pack, key);
    }

    @Override
    public ObjectFrame loadObject(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return pack.objects().sizeOf(pack.idOf(key)).map(ObjectFrame::of).orElse(null);
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
    public ObjectFrame loadObject(String key) {
        return graph.getObjects().get(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim();
    }
}
