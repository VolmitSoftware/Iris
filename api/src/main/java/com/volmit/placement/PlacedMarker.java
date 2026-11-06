package com.volmit.placement;

import com.volmit.geometry.BlockPosition;
import java.util.Objects;

/** A mark a placement leaves at a block, of one of the kinds the host keeps. The position has world X and Z and a Y
 * that is the height above the dimension's bottom (0 = bottom).
 */
public sealed interface PlacedMarker permits PlacedMarker.PackMarker, PlacedMarker.TreeMaterial, PlacedMarker.BuriedTreasure {
    BlockPosition position();

    /** A marker the pack declares, by its key such as {@code spawn/zombie}. */
    record PackMarker(BlockPosition position, String key) implements PlacedMarker {
        public PackMarker {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("A pack marker needs a key");
            }
        }
    }

    /** A block of a tree: the host keeps the block's material so the tree can be felled and grown. */
    record TreeMaterial(BlockPosition position) implements PlacedMarker {
        public TreeMaterial {
            Objects.requireNonNull(position, "position");
        }
    }

    /** A buried treasure chest: the host keeps it as a point of interest that treasure maps lead to. */
    record BuriedTreasure(BlockPosition position) implements PlacedMarker {
        public BuriedTreasure {
            Objects.requireNonNull(position, "position");
        }
    }
}
