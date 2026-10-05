package com.volmit.placement;

import com.volmit.mantle.BlockBox;
import com.volmit.world.BlockPosition;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete proposed writes, constructed before validation or mutation.
 * Block positions are unique and ordered x/y/z. writtenBounds describes block writes only;
 * markers and the original object's declared footprint are tracked separately.
 */
public record PlacementPlan(PlacementRequest request, List<PlacedBlock> blocks, List<PlacedMarker> markers) {
    public PlacementPlan {
        Objects.requireNonNull(request, "request");
        blocks = List.copyOf(blocks).stream().sorted(Comparator.comparing(PlacedBlock::position)).toList();
        markers = List.copyOf(markers).stream().sorted(Comparator.comparing(PlacedMarker::position)).toList();
        var positions = new HashSet<BlockPosition>();
        for (var block : blocks) {
            if (!positions.add(block.position())) throw new IllegalArgumentException("Duplicate placement write: " + block.position());
        }
    }

    public Optional<BlockBox> writtenBounds() {
        if (blocks.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var block : blocks) {
            var position = block.position();
            minX = Math.min(minX, position.x()); minY = Math.min(minY, position.y()); minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x()); maxY = Math.max(maxY, position.y()); maxZ = Math.max(maxZ, position.z());
        }
        return Optional.of(new BlockBox(minX, minY, minZ, Math.addExact(maxX, 1), Math.addExact(maxY, 1), Math.addExact(maxZ, 1)));
    }
}
