package com.volmit.tooling.world;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.object.BlockEntityData;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * One open tool world. Every position here is a block of the world itself: X and Z are world coordinates and Y is the
 * world's own Y, from {@link #minY()} up to but not including {@link #maxY()}. That is not the height above the
 * dimension's bottom that {@link com.volmit.world.PackWorld} uses.
 *
 * <p>Threads. A host may split a world across threads, one for each region of it, as Folia does. {@link #owns} tells
 * whether the calling thread owns a position; reading and writing blocks is allowed only there, and {@link #run} and
 * {@link #call} get a task onto the owning thread from any other. Everything else here is safe from any thread.
 */
public interface ToolWorld {
    /** The name the tool gave in {@link ToolWorldSpec#name()}. Still answers after the world closed. */
    String name();

    /** The lowest Y of the world. */
    int minY();

    /** The first Y above the world. */
    int maxY();

    /** The players standing in this world now, a snapshot. */
    List<ToolPlayer> players();

    /** Whether the calling thread owns the block at {@code at}, so that it may read and write it. */
    boolean owns(BlockPosition at);

    /**
     * Runs {@code task} on the thread that owns {@code at}: at once when the caller is that thread, else later. A
     * task that throws is logged by the host.
     */
    void run(BlockPosition at, Runnable task);

    /** Runs {@code task} like {@link #run} and completes with its result, or fails with what it threw. */
    <T> CompletableFuture<T> call(BlockPosition at, Supplier<T> task);

    /**
     * The block at {@code at}. The state is complete, with every property.
     *
     * @throws IllegalStateException when the calling thread does not own {@code at}, or the world closed
     */
    BlockState block(BlockPosition at);

    /**
     * The block entity at {@code at}; empty when the block has none.
     *
     * @throws IllegalStateException when the calling thread does not own {@code at}, or the world closed
     */
    Optional<BlockEntityData> blockEntity(BlockPosition at);

    /**
     * Replaces the block at {@code at} with {@code state} and {@code data} as its block entity, without block updates
     * or {@link ToolWorldListener#blockChanging} calls, since the tool is the one changing it. Empty data leaves the
     * new block with the platform's default entity, if its state has one.
     *
     * @throws IllegalStateException when the calling thread does not own {@code at}, or the world closed
     */
    void setBlock(BlockPosition at, BlockState state, Optional<BlockEntityData> data);

    /** Whether the platform has {@code chunk} loaded right now. Safe from any thread; a tool treats it as a hint. */
    boolean loaded(ChunkPos chunk);

    /**
     * Runs the painter again for {@code chunks}, writes the differences into the live chunks and sends them to the
     * players. Block entities the painter sets replace those of the chunk. The future completes on a host thread when
     * every chunk is written, and fails if one could not be; chunks written before the failure stay written.
     */
    CompletableFuture<Void> repaint(Collection<ChunkPos> chunks);

    /**
     * Closes the world: sends every player back to where they were before they entered a tool world, restoring their
     * game mode, then unloads the world and deletes its files. {@link ToolWorldListener#closed()} is called once it
     * is unloaded. Closing a closed world completes at once. The future fails when the world could not be unloaded or
     * deleted; the host then deletes what is left when it starts next.
     */
    CompletableFuture<Void> close();
}
