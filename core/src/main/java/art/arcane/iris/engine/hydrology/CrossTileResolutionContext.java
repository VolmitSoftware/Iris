package art.arcane.iris.engine.hydrology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CrossTileResolutionContext {
    final Map<HydrologyTileKey, CrossTileResolvedOwner> resolved;
    final Set<HydrologyTileKey> resolving;
    final HydrologyTileKey root;
    final long maximumTileOffset;
    final int maximumOwners;
    int iterations;

    CrossTileResolutionContext(
            HydrologyTileKey root,
            long maximumTileOffset,
            int maximumOwners
    ) {
        this.resolved = new HashMap<>();
        this.resolving = new HashSet<>();
        this.root = Objects.requireNonNull(root, "root");
        this.maximumTileOffset = maximumTileOffset;
        this.maximumOwners = maximumOwners;
    }

    CrossTileResolvedOwner resolved(HydrologyTileKey key) {
        return resolved.get(key);
    }

    void remember(HydrologyTileKey key, CrossTileResolvedOwner owner) {
        long offsetX = Math.abs((long) key.tileX() - root.tileX());
        long offsetZ = Math.abs((long) key.tileZ() - root.tileZ());
        if (offsetX > maximumTileOffset || offsetZ > maximumTileOffset) {
            throw new IllegalStateException("Cross-tile owner dependency exceeded its color-ranked geometry bound.");
        }
        CrossTileResolvedOwner existing = resolved.putIfAbsent(key, owner);
        if (existing != null
                && !existing.withoutFootprintCompiler().equals(owner.withoutFootprintCompiler())) {
            throw new IllegalStateException("Cross-tile owner resolution produced inconsistent cached results.");
        }
        if (resolved.size() > maximumOwners) {
            throw new IllegalStateException("Cross-tile owner dependencies exceeded their color-ranked count bound.");
        }
    }

    boolean begin(HydrologyTileKey key) {
        return resolving.add(key);
    }

    void end(HydrologyTileKey key) {
        resolving.remove(key);
    }

    void recordIteration() {
        iterations++;
    }

    int iterations() {
        return iterations;
    }

    int ownerCount() {
        return resolved.size();
    }

    HydrologyTileKey root() {
        return root;
    }
}
