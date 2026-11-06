package com.volmit.placement;

/**
 * The bounds of the platform's structure pieces planned around a placement ({@link PlacementTerrain#structureVolumes}).
 * X and Z are world coordinates; Y is the height above the dimension's bottom.
 */
public interface StructureVolumes {
    boolean isEmpty();

    /** Whether any piece meets the box, bounds inclusive. */
    boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    /** Whether any piece contains the block or comes within {@code margin} blocks of it. */
    boolean containsWithin(int x, int y, int z, int margin);
}
