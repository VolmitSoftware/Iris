package com.volmit.world;

import com.volmit.block.BlockFace;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;

/** The live blocks of an area of the platform's world, for code that reads and changes them in place while the
 * platform decorates them, such as a module fitting a structure into its terrain. X and Z are world coordinates; Y is
 * the height above the dimension's bottom (0 = bottom), also in {@link #bounds()}. Reading or writing outside the bounds
 * throws IndexOutOfBoundsException.
 *
 * <p>Built for many calls: {@link #get} returns the same {@link BlockState} instance every time it reads the same
 * platform state, {@link #column} reads a whole column into an array the caller keeps, and writing a state this access
 * returned, or one the caller keeps as a constant, allocates nothing. Questions about states ({@link #is},
 * {@link #inTag}, {@link #fluid}) take any state, also one the access never returned.
 *
 * <p>Not thread-safe, and valid only while the call that handed it out runs.
 */
public interface BlockAccess {
    BlockBox bounds();

    BlockState get(int x, int y, int z);

    /** Reads {@code into.length} blocks of a column upward: {@code into[i]} is the block at height {@code fromY + i}. */
    void column(int x, int z, int fromY, BlockState[] into);

    /** Replaces the block, without updating neighbours, the way the platform's own decoration writes. */
    void set(int x, int y, int z, BlockState state);

    /**
     * Whether {@code state} has {@code trait}, as the platform's world answers it: {@link BlockTrait#AIR} for air,
     * {@link BlockTrait#SOLID} for what the platform counts as solid, {@link BlockTrait#FLUID} for a state holding any
     * fluid (a source, a flowing fluid, a waterlogged block), {@link BlockTrait#LOG} for logs, wood, stems and hyphae and
     * {@link BlockTrait#LEAVES} for leaves, by the platform's tags or names. These can differ in corner cases from
     * {@link com.volmit.block.BlockRegistry#hasTrait}, which describes blocks the way generation picks them. Other
     * traits are not answered: the platform's world has no native question for them, so asking throws
     * {@link UnsupportedOperationException}.
     */
    boolean is(BlockState state, BlockTrait trait);

    /** Whether {@code state} is in the platform's block tag {@code tag}, such as {@code minecraft:logs}. */
    boolean inTag(BlockState state, ResourceId tag);

    /** Whether the block at the position can carry a full block on its {@code face}, as the platform decides support. */
    boolean sturdy(int x, int y, int z, BlockFace face);

    /** The block of the fluid {@code state} holds, such as water for a waterlogged stair; air when it holds none. */
    BlockState fluid(BlockState state);
}
