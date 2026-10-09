package com.volmit.world;

/** A block position. Owning contracts specify whether it is local or in the world. A world position has
 * world X and Z and a Y that is the height above the dimension's bottom (0 = bottom).
 */
public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
    /** The origin, which settings start from before the host reads their JSON: packs write {@code {"x": 1, "y": 2, "z": 3}},
     * and an omitted coordinate is 0.
     */
    private BlockPosition() {
        this(0, 0, 0);
    }

    public BlockPosition add(BlockPosition offset) {
        return new BlockPosition(Math.addExact(x, offset.x), Math.addExact(y, offset.y), Math.addExact(z, offset.z));
    }

    public BlockPosition subtract(BlockPosition offset) {
        return new BlockPosition(Math.subtractExact(x, offset.x), Math.subtractExact(y, offset.y), Math.subtractExact(z, offset.z));
    }

    @Override
    public int compareTo(BlockPosition other) {
        int result = Integer.compare(x, other.x);
        if (result == 0) result = Integer.compare(y, other.y);
        return result == 0 ? Integer.compare(z, other.z) : result;
    }
}
