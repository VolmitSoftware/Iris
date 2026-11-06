package com.volmit.block;

/**
 * How an object's rotation moves a direction its blocks face ({@link BlockRegistry#rotate}). A direction is a vector
 * such as (0, 0, -1) for north, or (1, 0, -2) for one of a sign's sixteen; the turned vector need not be whole, and the
 * platform takes the direction closest to it.
 */
@FunctionalInterface
public interface BlockTurn {
    Direction turn(double x, double y, double z);

    /** A direction vector. */
    record Direction(double x, double y, double z) {
    }
}
