package com.volmit.vanilla;

import com.volmit.block.BlockState;
import java.util.Objects;

/** The blocks a structure piece's template places, at their world positions (Y above the dimension's bottom), after the
 * platform ran the template's processors and turned it with the piece. A cell whose template block the processors
 * removed has no state: nothing is placed there, but the template still claims it. Cells come in no particular order,
 * each position once.
 *
 * <p>{@link #resolved()} is false for a piece whose blocks the host cannot read from a template (a piece the platform
 * builds in code, or a jigsaw element that places a feature); it then has no cells. {@link #readBlocks()} says how many
 * template blocks the host read and processed for the answer, so a caller can bound the work it allows.
 */
public final class TemplateCells {
    /** The answer for a piece without a template. */
    public static final TemplateCells UNRESOLVED = new TemplateCells(false, new int[0], new int[0], new int[0], new BlockState[0], 0);

    private final boolean resolved;
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;
    private final BlockState[] states;
    private final int readBlocks;

    private TemplateCells(boolean resolved, int[] xs, int[] ys, int[] zs, BlockState[] states, int readBlocks) {
        this.resolved = resolved;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.states = states;
        this.readBlocks = readBlocks;
    }

    /** Cells at {@code (xs[i], ys[i], zs[i])} holding {@code states[i]}, null for a removed block. The arrays are kept,
     * not copied.
     */
    public static TemplateCells of(int[] xs, int[] ys, int[] zs, BlockState[] states, int readBlocks) {
        int size = Objects.requireNonNull(states, "states").length;
        if (xs.length != size || ys.length != size || zs.length != size) {
            throw new IllegalArgumentException("Template cell coordinates and states differ in length");
        }
        if (readBlocks < 0) {
            throw new IllegalArgumentException("Read block count cannot be negative: " + readBlocks);
        }
        return new TemplateCells(true, xs, ys, zs, states, readBlocks);
    }

    public boolean resolved() {
        return resolved;
    }

    public int readBlocks() {
        return readBlocks;
    }

    public int size() {
        return states.length;
    }

    public int x(int cell) {
        return xs[cell];
    }

    public int y(int cell) {
        return ys[cell];
    }

    public int z(int cell) {
        return zs[cell];
    }

    /** Whether the processors removed the template's block of this cell. */
    public boolean removed(int cell) {
        return states[cell] == null;
    }

    /** The cell's processed block; null when {@link #removed}. */
    public BlockState state(int cell) {
        return states[cell];
    }
}
