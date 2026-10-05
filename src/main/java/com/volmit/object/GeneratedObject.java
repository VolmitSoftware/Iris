package com.volmit.object;

import com.volmit.mantle.BlockBox;
import com.volmit.world.BlockPosition;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable sparse geometry, safe to cache per pack snapshot.
 * Bounds are local, minima inclusive/maxima exclusive, and include blocks and markers.
 * The anchor may lie outside the bounds. Placement aligns it with a requested world position.
 * Blocks are sorted by x/y/z; duplicates fail. Marker order at an equal position is declaration
 * order. This value contains no placement settings, random state, or native platform objects.
 */
public record GeneratedObject(BlockBox bounds, BlockPosition anchor,
                              List<ObjectBlock> blocks, List<ObjectMarker> markers) {
    public GeneratedObject {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(anchor, "anchor");
        blocks = List.copyOf(blocks).stream().sorted(Comparator.comparing(ObjectBlock::position)).toList();
        markers = List.copyOf(markers).stream().sorted(Comparator.comparing(ObjectMarker::position)).toList();
        var positions = new HashSet<BlockPosition>();
        for (var block : blocks) {
            checkPosition(bounds, block.position());
            if (!positions.add(block.position())) throw new IllegalArgumentException("Duplicate object block: " + block.position());
        }
        for (var marker : markers) checkPosition(bounds, marker.position());
    }

    private static void checkPosition(BlockBox bounds, BlockPosition position) {
        if (!bounds.contains(position.x(), position.y(), position.z())) {
            throw new IllegalArgumentException("Object entry outside bounds: " + position);
        }
    }
}
