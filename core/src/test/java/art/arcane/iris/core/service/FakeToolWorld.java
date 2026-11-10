package art.arcane.iris.core.service;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A tool world that keeps its blocks in a map and runs every task at once, on the calling thread. */
final class FakeToolWorld implements ToolWorld {
    private static final BlockState AIR = BlockState.parse("minecraft:air");

    final Map<BlockPosition, BlockState> blocks = new HashMap<>();
    final Map<BlockPosition, BlockEntityData> entities = new HashMap<>();
    final List<ToolPlayer> players = new ArrayList<>();
    final List<ChunkPos> repainted = new ArrayList<>();
    final List<Runnable> held = Collections.synchronizedList(new ArrayList<>());
    /** Every chunk is loaded except these. */
    final List<ChunkPos> unloadedChunks = Collections.synchronizedList(new ArrayList<>());
    volatile boolean schedulingFails;
    volatile boolean holdTasks;
    private final String name;

    FakeToolWorld(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 320;
    }

    @Override
    public List<ToolPlayer> players() {
        return List.copyOf(players);
    }

    @Override
    public boolean owns(BlockPosition at) {
        return !schedulingFails;
    }

    @Override
    public void run(BlockPosition at, Runnable task) {
        if (holdTasks) {
            held.add(task);
            return;
        }
        task.run();
    }

    @Override
    public <T> CompletableFuture<T> call(BlockPosition at, Supplier<T> task) {
        if (schedulingFails) {
            return CompletableFuture.failedFuture(new IllegalStateException("not scheduled"));
        }
        if (holdTasks) {
            CompletableFuture<T> result = new CompletableFuture<>();
            held.add(() -> result.complete(task.get()));
            return result;
        }
        return CompletableFuture.completedFuture(task.get());
    }

    /** Runs the tasks held back so far, the way the region thread gets to them. */
    void runHeld() {
        List<Runnable> tasks;
        synchronized (held) {
            tasks = List.copyOf(held);
            held.clear();
        }
        tasks.forEach(Runnable::run);
    }

    @Override
    public BlockState block(BlockPosition at) {
        return blocks.getOrDefault(at, AIR);
    }

    @Override
    public Optional<BlockEntityData> blockEntity(BlockPosition at) {
        return Optional.ofNullable(entities.get(at));
    }

    @Override
    public void setBlock(BlockPosition at, BlockState state, Optional<BlockEntityData> data) {
        blocks.put(at, state);
        data.ifPresentOrElse(entity -> entities.put(at, entity), () -> entities.remove(at));
    }

    @Override
    public boolean loaded(ChunkPos chunk) {
        return unloadedChunks.stream().noneMatch(chunk::equals);
    }

    @Override
    public CompletableFuture<Void> repaint(Collection<ChunkPos> chunks) {
        repainted.addAll(chunks);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.completedFuture(null);
    }
}
