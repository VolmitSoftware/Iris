package com.volmit.example.placement;

import com.volmit.world.BlockPosition;

/** Placement-wide rotation chosen once per attempt, like Iris's spin kernel. */
public record Transforms(int quarterTurns) {
    /** Rotates a local position clockwise around the pivot's vertical axis. */
    public BlockPosition rotate(BlockPosition local, BlockPosition pivot) {
        int dx = local.x() - pivot.x();
        int dz = local.z() - pivot.z();
        for (int turn = 0; turn < quarterTurns; turn++) {
            int rotatedX = -dz;
            dz = dx;
            dx = rotatedX;
        }
        return new BlockPosition(pivot.x() + dx, local.y(), pivot.z() + dz);
    }
}
