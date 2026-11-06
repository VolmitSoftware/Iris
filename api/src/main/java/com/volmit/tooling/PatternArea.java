package com.volmit.tooling;

/**
 * A square of columns a {@link PatternView} is asked for: {@code columns} by {@code columns} of them, one every
 * {@code blocksPerColumn} blocks, the first at block ({@code x}, {@code z}), row by row from the north west.
 */
public record PatternArea(int x, int z, int columns, int blocksPerColumn) {
    public PatternArea {
        if (columns < 1) {
            throw new IllegalArgumentException("A pattern area has at least 1 column across, not " + columns);
        }
        if (blocksPerColumn < 1) {
            throw new IllegalArgumentException("A pattern area has a column every block or farther apart, not every " + blocksPerColumn);
        }
    }

    /** The block x of the column {@code column} across. */
    public int blockX(int column) {
        return x + column * blocksPerColumn;
    }

    /** The block z of the row {@code row} down. */
    public int blockZ(int row) {
        return z + row * blocksPerColumn;
    }
}
