package com.volmit.iris.util.math;

import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class Vector3i extends BlockVector {
    public Vector3i(int x, int y, int z) {
        super(x, y, z);
    }

    public Vector3i(Vector vec) {
        super(vec);
    }

    @NotNull
    @Override
    public Vector3i clone() {
        return (Vector3i) super.clone();
    }

    @Override
    public int hashCode() {
        return (((int) x & 0x3FF) << 2) | (((int) y & 0x3FF) << 1) | ((int) z & 0x3FF);
    }
}
