package com.volmit.block;

/** The block a {@link PaletteSpec} picks at a position, built by the host ({@link BlockRegistry#palette}). The
 * coordinates are the noise's input, so they are the same kind the module's other noise uses: world X and Z, and Y as the
 * module counts it, which is the height above the dimension's bottom unless the module documents otherwise. The same
 * position always gives the same block. Safe for concurrent calls.
 */
@FunctionalInterface
public interface PaletteSampler {
    BlockState at(double x, double y, double z);
}
