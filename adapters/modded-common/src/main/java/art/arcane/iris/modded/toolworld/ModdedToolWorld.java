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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedIrisLog;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.geometry.ChunkPos;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One tool world on a modded server: a runtime dimension of the pack whose chunks come from the tool's painter. It exists,
 * registered, from before the level is made until the level is gone; {@link #bind} gives it the level once it exists.
 * The server thread owns everything on a modded server, so a position is owned by whoever is on it.
 */
final class ModdedToolWorld implements ToolWorld {
    private static final int SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final String packName;
    private final String name;
    private final String dimensionId;
    private final ToolWorldListener listener;
    private final ModdedToolPainting painting;
    private final ToolPlayers players;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean gone = new AtomicBoolean();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private volatile ServerLevel level;

    ModdedToolWorld(String packName, String name, String dimensionId, ToolWorldListener listener,
                    ModdedToolPainting painting, ToolPlayers players) {
        this.packName = packName;
        this.name = name;
        this.dimensionId = dimensionId;
        this.listener = listener;
        this.painting = painting;
        this.players = players;
    }

    String packName() {
        return packName;
    }

    String dimensionId() {
        return dimensionId;
    }

    ModdedToolPainting painting() {
        return painting;
    }

    void bind(ServerLevel created) {
        level = Objects.requireNonNull(created, "created");
    }

    boolean isBound() {
        return level != null;
    }

    ServerLevel level() {
        ServerLevel bound = level;
        if (bound == null) {
            throw new IllegalStateException("The tool world " + name + " is not created yet");
        }
        return bound;
    }

    private static MinecraftServer server() {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            throw new IllegalStateException("No server is running");
        }
        return server;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int minY() {
        return level().getMinY();
    }

    @Override
    public int maxY() {
        return level().getMinY() + level().getHeight();
    }

    @Override
    public List<ToolPlayer> players() {
        return level().players().stream().<ToolPlayer>map(players::of).toList();
    }

    @Override
    public boolean owns(BlockPosition at) {
        return server().isSameThread();
    }

    @Override
    public void run(BlockPosition at, Runnable task) {
        server().execute(() -> runGuarded(task, "task at " + at));
    }

    @Override
    public <T> CompletableFuture<T> call(BlockPosition at, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        server().execute(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override
    public BlockState block(BlockPosition at) {
        requireOwned(at);
        return ToolBlocks.apiStateOf(level().getBlockState(ToolBlocks.blockPosOf(at)));
    }

    @Override
    public Optional<BlockEntityData> blockEntity(BlockPosition at) {
        requireOwned(at);
        return ToolBlocks.blockEntityOf(level(), ToolBlocks.blockPosOf(at));
    }

    @Override
    public void setBlock(BlockPosition at, BlockState state, Optional<BlockEntityData> data) {
        requireOwned(at);
        ServerLevel target = level();
        ToolWorldGuard.hostWrite(() -> {
            target.setBlock(ToolBlocks.blockPosOf(at), ToolBlocks.minecraftStateOf(state), SET_BLOCK_FLAGS);
            data.ifPresent(entity -> ToolBlocks.applyBlockEntity(target, ToolBlocks.blockPosOf(at), entity));
        });
    }

    @Override
    public boolean loaded(ChunkPos chunk) {
        return level().getChunkSource().hasChunk(chunk.x(), chunk.z());
    }

    @Override
    public CompletableFuture<Void> repaint(Collection<ChunkPos> chunks) {
        if (isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Tool world " + name + " is closed"));
        }
        List<ChunkPos> distinct = chunks.stream().distinct().toList();
        CompletableFuture<Void> repainted = new CompletableFuture<>();
        server().execute(() -> {
            try {
                ServerLevel target = level();
                for (ChunkPos position : distinct) {
                    LevelChunk chunk = target.getChunk(position.x(), position.z());
                    painting.repaint(target, chunk);
                }
                repainted.complete(null);
            } catch (Throwable failure) {
                repainted.completeExceptionally(failure);
            }
        });
        return repainted;
    }

    @Override
    public CompletableFuture<Void> close() {
        if (!closing.compareAndSet(false, true)) {
            return closed;
        }
        server().execute(this::sendPlayersBackThenRemove);
        return closed;
    }

    private void sendPlayersBackThenRemove() {
        List<CompletableFuture<Boolean>> departures = level().players().stream()
                .map(player -> players.playerOf(player).returnToOrigin())
                .toList();
        CompletableFuture.allOf(departures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> server().execute(this::removeLevel));
    }

    private void removeLevel() {
        try {
            ModdedDimensionManager.remove(server(), dimensionId, true);
        } catch (Throwable failure) {
            unloaded();
            closed.completeExceptionally(failure);
            return;
        }
        unloaded();
        closed.complete(null);
    }

    /** Removes the level at once on the server thread, for a server that is stopping; players leave with it. */
    void shutDownNow(MinecraftServer server) {
        closing.set(true);
        try {
            if (isBound()) {
                ModdedDimensionManager.remove(server, dimensionId, true);
            }
        } catch (Throwable failure) {
            ModdedIrisLog.error("Tool world {} could not be removed when Iris stopped.", name, failure);
        }
        unloaded();
        closed.complete(null);
    }

    /** Minecraft unloaded the level, whoever asked: it is gone for the tool and for the host. */
    void unloaded() {
        if (!gone.compareAndSet(false, true)) {
            return;
        }
        ToolWorldRegistry.INSTANCE.unregister(this);
        tell(ToolWorldListener::closed);
    }

    /** Called by the opener when creating the level failed: nothing of it is left to unload. */
    void abandon() {
        ToolWorldRegistry.INSTANCE.unregister(this);
    }

    Optional<String> closeRefusal() {
        try {
            return listener.closeRefusal();
        } catch (Throwable failure) {
            ModdedIrisLog.error("The listener of tool world {} failed.", name, failure);
            return Optional.empty();
        }
    }

    CompletableFuture<Void> prepareReplacement(Optional<ToolPlayer> requestedBy) {
        try {
            return listener.closing(requestedBy);
        } catch (Throwable failure) {
            ModdedIrisLog.error("The listener of tool world {} failed.", name, failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The tool's listener asked, with {@code fallback} as the answer when it throws. Runs on the caller's thread. */
    boolean ask(Function<ToolWorldListener, Boolean> question, boolean fallback) {
        try {
            return question.apply(listener);
        } catch (Throwable failure) {
            ModdedIrisLog.error("The listener of tool world {} failed.", name, failure);
            return fallback;
        }
    }

    /** Tells the listener something. Runs on the caller's thread. */
    void tell(Consumer<ToolWorldListener> news) {
        ask(toolListener -> {
            news.accept(toolListener);
            return true;
        }, true);
    }

    boolean isClosed() {
        return closing.get() || gone.get();
    }

    private void requireOwned(BlockPosition at) {
        if (isClosed()) {
            throw new IllegalStateException("Tool world " + name + " is closed");
        }
        if (!owns(at)) {
            throw new IllegalStateException("The calling thread does not own " + at + " in tool world " + name);
        }
        level();
    }

    private void runGuarded(Runnable task, String what) {
        try {
            task.run();
        } catch (Throwable failure) {
            ModdedIrisLog.error("A task of tool world {} failed: {}.", name, what, failure);
        }
    }

    @Override
    public String toString() {
        return "tool world " + name;
    }
}
