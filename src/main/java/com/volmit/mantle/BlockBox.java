package com.volmit.mantle;

import java.util.Objects;

/** World block bounds, minimum inclusive and maximum exclusive. Empty boxes are valid. */
public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted block bounds");
        }
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }

    public boolean contains(BlockBox area) {
        Objects.requireNonNull(area, "area");
        return area.minX >= minX && area.maxX <= maxX
                && area.minY >= minY && area.maxY <= maxY
                && area.minZ >= minZ && area.maxZ <= maxZ;
    }
}
