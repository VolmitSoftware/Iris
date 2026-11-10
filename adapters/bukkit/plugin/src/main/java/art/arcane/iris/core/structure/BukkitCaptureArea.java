/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure;

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.CapturedStructure;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.TemplateBlock;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A throwaway Bukkit world structures are generated in one after another, one cell of a grid each, and removed on close. */
final class BukkitCaptureArea implements PlatformStructures.CaptureArea {
    private static final int CELL_STRIDE = 128;
    private static final int CELL_COLUMNS = 8;
    private static final long REGION_TIMEOUT_SECONDS = 30L;

    private final World world;
    private final VolmitSender sender;
    private int nextCell;

    BukkitCaptureArea(World world, VolmitSender sender) {
        this.world = world;
        this.sender = sender;
    }

    @Override
    public Optional<CapturedStructure> capture(ResourceId structure, long seed, int maxSpan) {
        int cellIndex = nextCell++;
        int originX = (cellIndex % CELL_COLUMNS) * CELL_STRIDE;
        int originZ = (cellIndex / CELL_COLUMNS) * CELL_STRIDE;
        int anchorChunkX = (originX + CELL_STRIDE / 2) >> 4;
        int anchorChunkZ = (originZ + CELL_STRIDE / 2) >> 4;
        String key = structure.toString();

        AtomicReference<CapturedStructure> capturedRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        boolean scheduled = J.runRegion(world, anchorChunkX, anchorChunkZ, () -> {
            try {
                capturedRef.set(placeAndRead(key, anchorChunkX, anchorChunkZ, seed, maxSpan));
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        if (!scheduled) {
            throw captureFailure(key, anchorChunkX, anchorChunkZ, "region task was not accepted", null);
        }

        try {
            if (!latch.await(REGION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw captureFailure(key, anchorChunkX, anchorChunkZ,
                        "region task did not complete within " + REGION_TIMEOUT_SECONDS + " seconds", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw captureFailure(key, anchorChunkX, anchorChunkZ, "interrupted while waiting for the region task", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw captureFailure(key, anchorChunkX, anchorChunkZ, "platform placement failed", error);
        }
        return Optional.ofNullable(capturedRef.get());
    }

    private CapturedStructure placeAndRead(String key, int anchorChunkX, int anchorChunkZ, long seed, int maxSpan) {
        int[] box = INMS.get().placeStructure(world, anchorChunkX, anchorChunkZ, key, seed, maxSpan);
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
                    Block block = world.getBlockAt(box[0] + dx, box[1] + dy, box[2] + dz);
                    if (block.getType() == Material.AIR) {
                        continue;
                    }
                    blocks.add(blockOf(block, dx, dy, dz));
                }
            }
        }
        unloadChunksOf(box);
        return blocks.isEmpty() ? null : new CapturedStructure(new BlockPosition(width, height, depth), blocks);
    }

    private static TemplateBlock blockOf(Block block, int x, int y, int z) {
        BlockState state = BlockState.parse(
                PlatformBlockStates.namespaced(BukkitBlockState.of(block.getBlockData()).key()));
        TileData tile = TileData.getTileState(block, false);
        Optional<BlockEntityData> blockEntity = tile == null ? Optional.empty() : Optional.of(BlockEntities.of(tile));
        return TemplateBlock.of(new BlockPosition(x, y, z), state, blockEntity);
    }

    private void unloadChunksOf(int[] box) {
        int minCX = box[0] >> 4;
        int maxCX = box[3] >> 4;
        int minCZ = box[2] >> 4;
        int maxCZ = box[5] >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                try {
                    world.unloadChunk(cx, cz, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void close() {
        FeatureImporter.destroyScratchWorld(world, sender);
    }

    static IllegalStateException captureFailure(String key, int chunkX, int chunkZ, String detail, Throwable cause) {
        String message = "Structure capture failed for '" + key + "' at chunk "
                + chunkX + "," + chunkZ + ": " + detail;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
