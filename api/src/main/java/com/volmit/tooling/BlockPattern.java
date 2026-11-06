package com.volmit.tooling;

import com.volmit.block.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a {@link PatternView} places over a square of columns: the distinct blocks, and which of them each column holds,
 * row by row from the north west.
 */
public final class BlockPattern {
    /** What {@link #pickAt} gives for a column the pattern places nothing at. */
    public static final int NO_BLOCK = -1;

    private final int columns;
    private final List<BlockState> blocks;
    private final int[] picks;

    private BlockPattern(int columns, List<BlockState> blocks, int[] picks) {
        this.columns = columns;
        this.blocks = List.copyOf(blocks);
        this.picks = picks;
    }

    /**
     * The pattern of {@code columns} by {@code columns} columns holding {@code columnBlocks}, row by row from the north
     * west, null where nothing is placed.
     */
    public static BlockPattern of(int columns, BlockState[] columnBlocks) {
        if (columns < 1 || columnBlocks.length != columns * columns) {
            throw new IllegalArgumentException("A pattern " + columns + " columns across holds " + columns * columns
                    + " columns, not " + columnBlocks.length);
        }
        List<BlockState> blocks = new ArrayList<>();
        Map<BlockState, Integer> indexByBlock = new HashMap<>();
        int[] picks = new int[columnBlocks.length];
        for (int column = 0; column < columnBlocks.length; column++) {
            BlockState block = columnBlocks[column];
            picks[column] = block == null ? NO_BLOCK : indexByBlock.computeIfAbsent(block, added -> {
                blocks.add(added);
                return blocks.size() - 1;
            });
        }
        return new BlockPattern(columns, blocks, picks);
    }

    public int columns() {
        return columns;
    }

    /** The distinct blocks the pattern places, in the order they first appear. */
    public List<BlockState> blocks() {
        return blocks;
    }

    /** Which of {@link #blocks()} the column holds, or {@link #NO_BLOCK}. */
    public int pickAt(int column, int row) {
        return picks[row * columns + column];
    }
}
