package com.volmit.tooling.world;

/**
 * One dust particle at a position of a tool world, for {@link ToolPlayer#particles}. The position is in world
 * coordinates with the world's own Y.
 *
 * @param rgb  the colour as {@code 0xRRGGBB}
 * @param size the size of the speck, above zero
 */
public record Particle(double x, double y, double z, int rgb, float size) {
    private static final int MAX_RGB = 0xFFFFFF;

    public Particle {
        if (rgb < 0 || rgb > MAX_RGB) {
            throw new IllegalArgumentException("A particle colour is 0xRRGGBB: " + Integer.toHexString(rgb));
        }
        if (!(size > 0)) {
            throw new IllegalArgumentException("A particle size is above zero: " + size);
        }
    }
}
