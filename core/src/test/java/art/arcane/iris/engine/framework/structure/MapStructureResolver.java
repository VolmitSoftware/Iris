package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;

import java.util.HashMap;
import java.util.Map;

/** A structure graph resolver for tests: the pools, pieces and object sizes a test sets by key, and nothing else. */
public final class MapStructureResolver implements StructureGraphResolver {
    private final Map<String, IrisJigsawPool> pools = new HashMap<>();
    private final Map<String, IrisJigsawPiece> pieces = new HashMap<>();
    private final Map<String, ObjectFrame> objects = new HashMap<>();

    public MapStructureResolver pool(String key, IrisJigsawPool pool) {
        pools.put(key, pool);
        return this;
    }

    public MapStructureResolver piece(String key, IrisJigsawPiece piece) {
        pieces.put(key, piece);
        return this;
    }

    public MapStructureResolver object(String key, ObjectFrame object) {
        objects.put(key, object);
        return this;
    }

    @Override
    public IrisJigsawPool loadPool(String key) {
        return pools.get(key);
    }

    @Override
    public IrisJigsawPiece loadPiece(String key) {
        return pieces.get(key);
    }

    @Override
    public ObjectFrame loadObject(String key) {
        return objects.get(key);
    }
}
