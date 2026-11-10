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

package art.arcane.iris.modded.structure;

import art.arcane.iris.core.tooling.ToolingSender;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedPlatformWorld;
import art.arcane.iris.modded.ModdedStructureHooks;
import art.arcane.iris.modded.command.ModdedPackCommands;
import art.arcane.iris.modded.toolworld.ModdedToolPainting;
import art.arcane.iris.modded.toolworld.ToolBlocks;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.CapturedStructure;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.TemplateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * A scratch level that structures are generated in, one after another: an empty runtime dimension of an installed pack, so
 * what is read back is the structure's own blocks. The level lives on the server thread, so each step runs there while
 * the caller, a tool's worker, waits.
 */
final class ModdedCaptureArea implements PlatformStructures.CaptureArea {
    private static final String DIMENSION_PREFIX = "irisworldgen:capture_";
    private static final long SCRATCH_SEED = 1337L;
    private static final long STEP_TIMEOUT_SECONDS = 30L;
    private static final long OPEN_TIMEOUT_SECONDS = 120L;
    private static final int CELL_STRIDE = 128;
    private static final int CELL_COLUMNS = 8;

    private final MinecraftServer server;
    private final String dimensionId;
    private final ServerLevel level;
    private int nextCell;

    private ModdedCaptureArea(MinecraftServer server, String dimensionId, ServerLevel level) {
        this.server = server;
        this.dimensionId = dimensionId;
        this.level = level;
    }

    /** Opens the area; empty when the pack to make it from, or the level itself, could not be had, which the sender has been told. */
    static Optional<PlatformStructures.CaptureArea> open(MinecraftServer server, Sender sender) {
        Optional<String> pack = packFor(sender);
        if (pack.isEmpty()) {
            sender.error("A structure capture needs an installed pack to make its scratch world from");
            return Optional.empty();
        }
        String dimensionId = DIMENSION_PREFIX + UUID.randomUUID().toString().replace("-", "");
        try {
            ModdedDimensionManager.Handle handle = onServerThread(server, () -> ModdedDimensionManager.create(
                    server, dimensionId, pack.get(), pack.get(), SCRATCH_SEED, new ModdedToolPainting((x, z, canvas) -> { })),
                    OPEN_TIMEOUT_SECONDS);
            return Optional.of(new ModdedCaptureArea(server, dimensionId, handle.level()));
        } catch (IllegalStateException failure) {
            sender.error("The scratch world for structure capture could not be made: " + failure.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> packFor(Sender sender) {
        if (sender instanceof ToolingSender tooling && tooling.world().isPresent()) {
            return Optional.of(tooling.world().get().pack().name());
        }
        File[] packs = ModdedPackCommands.packsRoot().listFiles(File::isDirectory);
        if (packs == null) {
            return Optional.empty();
        }
        for (File pack : packs) {
            if (new File(pack, "dimensions/" + pack.getName() + ".json").isFile()) {
                return Optional.of(pack.getName());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<CapturedStructure> capture(ResourceId structure, long seed, int maxSpan) {
        int cellIndex = nextCell++;
        int originX = (cellIndex % CELL_COLUMNS) * CELL_STRIDE;
        int originZ = (cellIndex / CELL_COLUMNS) * CELL_STRIDE;
        int anchorChunkX = (originX + CELL_STRIDE / 2) >> 4;
        int anchorChunkZ = (originZ + CELL_STRIDE / 2) >> 4;
        String key = structure.toString();
        try {
            return Optional.ofNullable(onServerThread(server,
                    () -> placeAndRead(key, anchorChunkX, anchorChunkZ, seed, maxSpan), STEP_TIMEOUT_SECONDS));
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("Structure capture failed for '" + key + "' at chunk " + anchorChunkX + ","
                    + anchorChunkZ + ": " + failure.getMessage(), failure);
        }
    }

    private CapturedStructure placeAndRead(String key, int anchorChunkX, int anchorChunkZ, long seed, int maxSpan) {
        int[] box = new ModdedStructureHooks(() -> server)
                .placeStructure(new ModdedPlatformWorld(level), anchorChunkX, anchorChunkZ, key, seed, maxSpan);
        if (box == null) {
            return null;
        }
        int width = box[3] - box[0] + 1;
        int height = box[4] - box[1] + 1;
        int depth = box[5] - box[2] + 1;
        if (width <= 0 || height <= 0 || depth <= 0) {
            return null;
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                for (int dz = 0; dz < depth; dz++) {
                    BlockPos at = new BlockPos(box[0] + dx, box[1] + dy, box[2] + dz);
                    if (level.getBlockState(at).is(Blocks.AIR)) {
                        continue;
                    }
                    blocks.add(blockOf(at, new BlockPosition(dx, dy, dz)));
                }
            }
        }
        return blocks.isEmpty() ? null : new CapturedStructure(new BlockPosition(width, height, depth), blocks);
    }

    private TemplateBlock blockOf(BlockPos at, BlockPosition relative) {
        BlockState state = ToolBlocks.apiStateOf(level.getBlockState(at));
        return TemplateBlock.of(relative, state, ToolBlocks.blockEntityOf(level, at));
    }

    @Override
    public void close() {
        try {
            onServerThread(server, () -> ModdedDimensionManager.remove(server, dimensionId, true), STEP_TIMEOUT_SECONDS);
        } catch (IllegalStateException failure) {
            throw new IllegalStateException("The scratch world for structure capture could not be removed: " + failure.getMessage(), failure);
        }
    }

    private static <T> T onServerThread(MinecraftServer server, Supplier<T> task, long timeoutSeconds) {
        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        try {
            return result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the server thread", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            throw new IllegalStateException(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(), cause);
        } catch (TimeoutException timedOut) {
            throw new IllegalStateException("the server thread did not finish within " + timeoutSeconds + " seconds", timedOut);
        }
    }
}
