package com.volmit.iris.scaffold.lighting;

import com.bergerkiller.bukkit.common.collections.BlockFaceSet;

/**
 * Maps {@link BlockFaceSet} values to a 16x16x16 area of blocks
 */
public class BlockFaceSetSection {
    private final byte[] _maskData = new byte[4096];

    public void set(int x, int y, int z, BlockFaceSet faces) {
        _maskData[(y << 8) | (z << 4) | x] = (byte) faces.mask();
    }

    public BlockFaceSet get(int x, int y, int z) {
        return BlockFaceSet.byMask((int) _maskData[(y << 8) | (z << 4) | x]);
    }
}
