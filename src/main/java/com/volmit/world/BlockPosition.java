package com.volmit.world;

/** A block position. Owning contracts specify whether it is local or world space. */
public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
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
