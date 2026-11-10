/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.project.IrisProject;
import art.arcane.iris.core.runtime.InPlaceChunkRegenerator;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.StudioPainterSlots;
import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedBlockEntity;
import art.arcane.iris.core.runtime.StudioSlotOccupant;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.platform.studio.TerrainChunkCanvas;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.scheduling.J;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldListener;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One tool world on Bukkit: a studio world whose chunks come from the tool's painter. It exists, registered, from before
 * Bukkit creates the world until the world is unloaded; {@link #bind} gives it the Bukkit world once that exists.
 */
final class BukkitToolWorld implements ToolWorld, StudioSlotOccupant {
    private static final int CHUNK_SHIFT = 4;

    private final String packName;
    private final String name;
    private final String storageName;
    private final ToolWorldListener listener;
    private final StudioPainterSlots.Slot slot;
    private final PendingBlockEntities pending;
    private final ToolWorldPresence presence = new ToolWorldPresence();
    private final OpenToolWorlds registry;
    private final ToolPlayers players;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean gone = new AtomicBoolean();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private final IrisProject project;
    private volatile World world;

    BukkitToolWorld(
            String packName,
            String name,
            String storageName,
            ToolWorldListener listener,
            StudioPainterSlots.Slot slot,
            PendingBlockEntities pending,
            OpenToolWorlds registry,
            ToolPlayers players
    ) {
        this.packName = packName;
        this.name = name;
        this.storageName = storageName;
        this.listener = listener;
        this.slot = slot;
        this.pending = pending;
        this.registry = registry;
        this.players = players;
        this.project = new IrisProject(new File(workspaceFolder(), packName));
    }

    /** The studio project this world is, as the studio service shows the open studio to its commands. */
    @Override
    public IrisProject project() {
        return project;
    }

    private static File workspaceFolder() {
        StudioSVC studios = IrisServices.getOrNull(StudioSVC.class);
        return studios == null ? new File(".") : studios.getWorkspaceFolder();
    }

    String packName() {
        return packName;
    }

    /** The logical name of the world's storage, which the generator finds its painter by. */
    String storageName() {
        return storageName;
    }

    /** Gives the world its Bukkit world, once Bukkit has created it. */
    void bind(World created) {
        world = Objects.requireNonNull(created, "created");
        registry.bound(this);
    }

    boolean isBound() {
        return world != null;
    }

    UUID worldId() {
        return bukkit().getUID();
    }

    World bukkit() {
        World bound = world;
        if (bound == null) {
            throw new IllegalStateException("The tool world " + name + " is not created yet");
        }
        return bound;
    }

    ToolWorldPresence presence() {
        return presence;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int minY() {
        return bukkit().getMinHeight();
    }

    @Override
    public int maxY() {
        return bukkit().getMaxHeight();
    }

    @Override
    public List<ToolPlayer> players() {
        return bukkit().getPlayers().stream().<ToolPlayer>map(players::of).toList();
    }

    @Override
    public boolean owns(BlockPosition at) {
        return J.isOwnedByCurrentRegion(bukkit(), at.x() >> CHUNK_SHIFT, at.z() >> CHUNK_SHIFT);
    }

    @Override
    public void run(BlockPosition at, Runnable task) {
        World target = bukkit();
        Runnable guarded = () -> runGuarded(task, "task at " + at);
        if (!J.runRegion(target, at.x() >> CHUNK_SHIFT, at.z() >> CHUNK_SHIFT, guarded)) {
            IrisLogging.warn("Tool world %s could not schedule a task at %s.", name, at);
        }
    }

    @Override
    public <T> CompletableFuture<T> call(BlockPosition at, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        boolean scheduled = J.runRegion(bukkit(), at.x() >> CHUNK_SHIFT, at.z() >> CHUNK_SHIFT, () -> {
            try {
                result.complete(task.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        if (!scheduled) {
            result.completeExceptionally(new IllegalStateException("Tool world " + name + " could not schedule a task at " + at));
        }
        return result;
    }

    @Override
    public BlockState block(BlockPosition at) {
        return BukkitToolBlocks.stateOf(ownedBlock(at).getBlockData());
    }

    @Override
    public Optional<BlockEntityData> blockEntity(BlockPosition at) {
        return BukkitToolBlocks.blockEntityOf(ownedBlock(at));
    }

    @Override
    public void setBlock(BlockPosition at, BlockState state, Optional<BlockEntityData> data) {
        Block block = ownedBlock(at);
        block.setBlockData(BukkitToolBlocks.dataOf(state), false);
        data.ifPresent(entity -> BukkitToolBlocks.applyBlockEntity(block, entity));
    }

    @Override
    public boolean loaded(ChunkPos chunk) {
        return bukkit().isChunkLoaded(chunk.x(), chunk.z());
    }

    @Override
    public CompletableFuture<Void> repaint(Collection<ChunkPos> chunks) {
        if (isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Tool world " + name + " is closed"));
        }
        List<CompletableFuture<Void>> repaints = chunks.stream().distinct().map(this::repaintChunk).toList();
        return CompletableFuture.allOf(repaints.toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> close() {
        if (!closing.compareAndSet(false, true)) {
            return closed;
        }
        sendPlayersBack()
                .thenCompose(ignored -> StudioOpenCoordinator.get().closeToolWorld(bukkit(), project))
                .whenComplete((result, failure) -> finishClose(result, failure));
        return closed;
    }

    @Override
    public Optional<String> closeRefusal() {
        try {
            return listener.closeRefusal();
        } catch (Throwable failure) {
            IrisLogging.reportError("The listener of tool world " + name + " failed.", failure);
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Void> prepareReplacement(Optional<UUID> requestedBy) {
        Optional<ToolPlayer> by = requestedBy.map(Bukkit::getPlayer).map(players::of);
        try {
            return listener.closing(by);
        } catch (Throwable failure) {
            IrisLogging.reportError("The listener of tool world " + name + " failed.", failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The tool's listener asked, with {@code fallback} as the answer when it throws. Runs on the caller's thread. */
    boolean ask(Function<ToolWorldListener, Boolean> question, boolean fallback) {
        try {
            return question.apply(listener);
        } catch (Throwable failure) {
            IrisLogging.reportError("The listener of tool world " + name + " failed.", failure);
            return fallback;
        }
    }

    /** Tells the listener something. Runs on the caller's thread. */
    void tell(Consumer<ToolWorldListener> news) {
        ask(listener -> {
            news.accept(listener);
            return true;
        }, true);
    }

    /** A chunk of the world loaded: writes the block entities its paint asked for, then tells the tool. */
    void chunkLoaded(Chunk chunk) {
        for (PaintedBlockEntity entity : pending.take(chunk.getX(), chunk.getZ())) {
            applyPainted(chunk, entity);
        }
        tell(toolListener -> toolListener.chunkLoaded(chunk.getX(), chunk.getZ()));
    }

    /** Bukkit unloaded the world, whoever asked: it is gone for the tool and for the host. */
    void unloaded() {
        if (!gone.compareAndSet(false, true)) {
            return;
        }
        StudioPainterSlots.remove(storageName);
        registry.unregister(this);
        releaseStudioSlot();
        tell(ToolWorldListener::closed);
    }

    private void releaseStudioSlot() {
        StudioSVC studios = IrisServices.getOrNull(StudioSVC.class);
        if (studios != null) {
            studios.releaseStudioSlot(this);
        }
    }

    /** Called by the opener when creating the world failed: nothing of it is left to unload. */
    void abandon() {
        StudioPainterSlots.remove(storageName);
        registry.unregister(this);
    }

    private void finishClose(StudioOpenCoordinator.StudioCloseResult result, Throwable failure) {
        unloaded();
        Throwable cause = failure != null ? unwrap(failure) : result == null ? null : result.failureCause();
        if (cause == null) {
            closed.complete(null);
            return;
        }
        closed.completeExceptionally(cause);
    }

    private CompletableFuture<Void> sendPlayersBack() {
        List<CompletableFuture<Boolean>> departures = bukkit().getPlayers().stream()
                .map(player -> players.of(player).returnToOrigin())
                .toList();
        return CompletableFuture.allOf(departures.toArray(CompletableFuture[]::new));
    }

    private record Repainted(TerrainChunk terrain, TerrainChunkCanvas canvas) {
    }

    private CompletableFuture<Void> repaintChunk(ChunkPos position) {
        World target = bukkit();
        AtomicReference<Repainted> repainted = new AtomicReference<>();
        CompletableFuture<Void> painting = J.afut(() -> repainted.set(paintAgain(target, position)));
        CompletableFuture<Chunk> loading = target.getChunkAtAsync(position.x(), position.z());
        return painting.thenCombine(loading, (ignored, chunk) -> chunk)
                .thenCompose(chunk -> J.runRegionFuture(target, position.x(), position.z(),
                        () -> writeRepainted(target, chunk, repainted.get())));
    }

    private Repainted paintAgain(World target, ChunkPos position) {
        TerrainChunk terrain = TerrainChunk.create(target);
        TerrainChunkCanvas canvas = TerrainChunkCanvas.onPlatform(terrain);
        slot.painter().paint(position.x(), position.z(), canvas);
        return new Repainted(terrain, canvas);
    }

    private void writeRepainted(World target, Chunk chunk, Repainted repainted) {
        if (!INMS.get().applyChunkBlocks(chunk, repainted.terrain())) {
            InPlaceChunkRegenerator.applyBlockDiffs(chunk, repainted.terrain().getChunkData(),
                    target.getMinHeight(), target.getMaxHeight());
        }
        repainted.canvas().blockEntities().forEach(entity -> applyPainted(chunk, entity));
        target.refreshChunk(chunk.getX(), chunk.getZ());
    }

    private void applyPainted(Chunk chunk, PaintedBlockEntity entity) {
        Block block = chunk.getBlock(entity.x(), entity.y(), entity.z());
        try {
            BukkitToolBlocks.applyBlockEntity(block, entity.data());
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Tool world " + name + " could not write a block entity at "
                    + block.getX() + ", " + block.getY() + ", " + block.getZ() + ".", failure);
        }
    }

    private Block ownedBlock(BlockPosition at) {
        requireOpen();
        if (!owns(at)) {
            throw new IllegalStateException("The calling thread does not own " + at + " in tool world " + name);
        }
        return BukkitToolBlocks.blockAt(bukkit(), at);
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException("Tool world " + name + " is closed");
        }
        bukkit();
    }

    private boolean isClosed() {
        return closing.get() || gone.get();
    }

    private void runGuarded(Runnable task, String what) {
        try {
            task.run();
        } catch (Throwable failure) {
            IrisLogging.reportError("A task of tool world " + name + " failed: " + what + ".", failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cursor = failure;
        while (cursor instanceof CompletionException && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    @Override
    public String toString() {
        return "tool world " + name;
    }
}
