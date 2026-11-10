package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.IrisStructure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CompiledStructureGraph {
    private static final String UNKEYED = "<unloaded>";

    private final String structureKey;
    private final IrisStructure structure;
    private final Map<String, IrisJigsawPool> pools;
    private final Map<String, IrisJigsawPiece> pieces;
    private final Map<String, ObjectFrame> objects;
    private final Set<String> reachablePools;
    private final Set<String> reachablePieces;

    private CompiledStructureGraph(Builder builder) {
        structureKey = builder.structureKey;
        structure = builder.structure;
        pools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.pools));
        pieces = Collections.unmodifiableMap(new LinkedHashMap<>(builder.pieces));
        objects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.objects));
        reachablePools = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reachablePools));
        reachablePieces = Collections.unmodifiableSet(new LinkedHashSet<>(builder.reachablePieces));
    }

    static Builder builder(String structureKey, IrisStructure structure) {
        return new Builder(structureKey, structure);
    }

    public IrisStructure getStructure() {
        return structure;
    }

    /** The pack key of the structure, or a placeholder when the graph was compiled for a structure without one. */
    public String getStructureKey() {
        return structureKey == null || structureKey.isBlank() ? UNKEYED : structureKey;
    }

    public Map<String, IrisJigsawPool> getPools() {
        return pools;
    }

    public Map<String, IrisJigsawPiece> getPieces() {
        return pieces;
    }

    public Map<String, ObjectFrame> getObjects() {
        return objects;
    }

    public Set<String> getReachablePools() {
        return reachablePools;
    }

    public Set<String> getReachablePieces() {
        return reachablePieces;
    }

    static final class Builder {
        private final String structureKey;
        private final IrisStructure structure;
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, ObjectFrame> objects = new LinkedHashMap<>();
        private final Set<String> reachablePools = new LinkedHashSet<>();
        private final Set<String> reachablePieces = new LinkedHashSet<>();

        private Builder(String structureKey, IrisStructure structure) {
            this.structureKey = structureKey;
            this.structure = Objects.requireNonNull(structure);
        }

        Map<String, IrisJigsawPool> pools() {
            return pools;
        }

        Map<String, IrisJigsawPiece> pieces() {
            return pieces;
        }

        Map<String, ObjectFrame> objects() {
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
