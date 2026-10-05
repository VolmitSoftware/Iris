package com.volmit.hunk;

/** Dense local volume. Coordinates range from zero inclusive to each dimension exclusive.
 * Reads/writes outside that range throw IndexOutOfBoundsException. Null values are forbidden;
 * block volumes represent empty space with an air state. Invocation-owned hunks must not escape.
 * This is a new minimal contract, not a copy of Iris's full Hunk implementation.
 */
public interface Hunk<T> {
    int getWidth();
    int getHeight();
    int getDepth();
    T get(int x, int y, int z);
    void set(int x, int y, int z, T value);
}
