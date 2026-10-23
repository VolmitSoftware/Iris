package com.volmit.block;

/** The block a {@link PaletteSpec} picks at a position, built by the host ({@link BlockRegistry#palette}). The
 * coordinates are the noise's input and follow the API's coordinate rule: world X and Z, and Y as the height above the
 * dimension's bottom (0 = bottom block). The same position always gives the same block. Safe for concurrent calls.
 */
@FunctionalInterface
public interface PaletteSampler {
    BlockState at(double x, double y, double z);
}
