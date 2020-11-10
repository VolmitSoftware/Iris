package com.volmit.iris.scaffold.lighting;

/**
 * Keeps track of the 6 x/y/z neighbors of cubes
 */
public class LightingCubeNeighboring {
    public final LightingCube[] values = new LightingCube[6];

    /**
     * Generates a key ranging 0 - 5 for fixed x/y/z combinations<br>
     * - Bit 1 is set to contain whether x/y/z is 1 or -1
     * - Bit 2 is set to 1 when the axis is x<br>
     * - Bit 3 is set to 1 when the axis is z<br><br>
     * <p/>
     * This system requires that the x/y/z pairs are one the following:<br>
     * (0, 0, 1) | (0, 0, -1) | (0, 1, 0) | (0, -1, 0) | (1, 0, 0) | (-1, 0, 0)
     *
     * @param x value
     * @param y value
     * @param z value
     * @return key
     */
    private static final int getIndexByCube(int x, int y, int z) {
        return (((x + y + z + 1) & 0x2) >> 1) | ((x & 0x1) << 1) | ((z & 0x1) << 2);
    }

    /**
     * Gets whether all 6 cube neighbors are accessible
     * 
     * @return True if all neighbors are accessible
     */
    public boolean hasAll() {
        for (int i = 0; i < 6; i++) {
            if (values[i] == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the neighbor representing the given relative cube
     *
     * @param deltaCubeX
     * @param deltaCubeY
     * @param deltaCubeZ
     * @return neighbor, null if no neighbor is available here
     */
    public LightingCube get(int deltaCubeX, int deltaCubeY, int deltaCubeZ) {
        return values[getIndexByCube(deltaCubeX, deltaCubeY, deltaCubeZ)];
    }

    /**
     * Sets the neighbor representing the given relative cube
     *
     * @param deltaCubeX
     * @param deltaCubeY
     * @param deltaCubeZ
     * @param neighbor to set to, is allowed to be null to set to 'none'
     */
    public void set(int deltaCubeX, int deltaCubeY, int deltaCubeZ, LightingCube neighbor) {
        values[getIndexByCube(deltaCubeX, deltaCubeY, deltaCubeZ)] = neighbor;
    }
}
