package com.volmit.object;

/** How a scaled object fills the blocks between its source blocks when it grows ({@link PackObjects#scaled}). */
public enum ScaleMethod {
    /** Each source block becomes a box of blocks. */
    NONE,
    /** Linear interpolation in three dimensions. */
    TRILINEAR,
    /** Cubic spline interpolation in three dimensions; smoother, much slower. */
    TRICUBIC,
    /** Hermite spline interpolation in three dimensions; smoother, much slower. */
    TRIHERMITE
}
