package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.placement.IrisStructure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CompiledStructureGraph {
    private final IrisStructure structure;
    private final Map<String, IrisJigsawPool> pools;
    private final Map<String, IrisJigsawPiece> pieces;
    private final Map<String, IrisObject> objects;
    private final Set<String> reachablePools;
    private final Set<String> reachablePieces;

    private CompiledStructureGraph(Builder builder) {
        structure = builder.structure;
        pools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.pools));
        pieces = Collections.unmodifiableMap(new LinkedHashMap<>(builder.pieces));
        objects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.objects));
        reachablePools = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reachablePools));
        reachablePieces = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reachablePieces));
    }

    static Builder builder(IrisStructure structure) {
        return new Builder(structure);
    }

    public IrisStructure getStructure() {
        return structure;
    }

    public String getStructureKey() {
        String key = structure.getLoadKey();
        return key == null || key.isBlank() ? "<unloaded>" : key;
    }

    public Map<String, IrisJigsawPool> getPools() {
        return pools;
    }

    public Map<String, IrisJigsawPiece> getPieces() {
        return pieces;
    }

    public Map<String, IrisObject> getObjects() {
        return objects;
    }

    public Set<String> getReachablePools() {
        return reachablePools;
    }

    public Set<String> getReachablePieces() {
        return reachablePieces;
    }

    static final class Builder {
        private final IrisStructure structure;
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, IrisObject> objects = new LinkedHashMap<>();
        private final Set<String> reachablePools = new LinkedHashSet<>();
        private final Set<String> reachablePieces = new LinkedHashSet<>();

        private Builder(IrisStructure structure) {
            this.structure = Objects.requireNonNull(structure);
        }

        Map<String, IrisJigsawPool> pools() {
            return pools;
        }

        Map<String, IrisJigsawPiece> pieces() {
            return pieces;
        }

        Map<String, IrisObject> objects() {
            return objects;
        }

        Set<String> reachablePools() {
            return reachablePools;
        }

        Set<String> reachablePieces() {
            return reachablePieces;
        }

        CompiledStructureGraph build() {
            return new CompiledStructureGraph(this);
        }
    }
}
