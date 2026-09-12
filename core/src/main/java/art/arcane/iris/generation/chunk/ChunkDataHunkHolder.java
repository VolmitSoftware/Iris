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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.hunk.storage.AtomicHunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkDataHunkHolder extends AtomicHunk<PlatformBlockState> {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");

    private final ChunkData chunk;

    public ChunkDataHunkHolder(ChunkData chunk) {
        super(16, chunk.getMaxHeight() - chunk.getMinHeight(), 16);
        this.chunk = chunk;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public void setRaw(int x, int y, int z, PlatformBlockState t) {
        // Block-hunk contract: null means "no write", never "erase" — ChunkDataHunkView and
        // the modded ModdedBlockBuffer already discard nulls, and storing one here made the
        // Bukkit output diverge from the modded loaders for the same engine emission.
        if (t == null) {
            return;
        }
        super.setRaw(x, y, z, t);
    }

    @Override
    public PlatformBlockState getRaw(int x, int y, int z) {
        if (y < 0 || y >= getHeight()) {
            return AIR.get();
        }

        PlatformBlockState b = super.getRaw(x, y, z);

        return b != null ? b : AIR.get();
    }

    public PlatformBlockState getStoredRaw(int x, int y, int z) {
        return super.getRaw(x, y, z);
    }

    public void apply() {
        applyTo(chunk);
    }

    public void applyTo(ChunkData target) {
        if (INMS.get().applyChunkDataBlocks(target, this)) {
            return;
        }

        int height = getHeight();
        for (int x = 0; x < getWidth(); x++) {
            for (int z = 0; z < getDepth(); z++) {
                BlockData activeBlock = null;
                int runStart = -1;

                for (int y = 0; y < height; y++) {
                    PlatformBlockState state = super.getRaw(x, y, z);
                    BlockData block = state == null ? null : (BlockData) state.nativeHandle();
                    // Custom wrappers are not real Bukkit data; write the vanilla base like the
                    // NMS fast path (NMSBinding.applyChunkDataBlocks) does.
                    if (block instanceof IrisCustomData custom) {
                        block = custom.getBase();
                    }
                    if (block == null) {
                        flushRun(target, x, z, runStart, y, activeBlock);
                        activeBlock = null;
                        runStart = -1;
                        continue;
                    }

                    if (activeBlock != null && activeBlock.equals(block)) {
                        continue;
                    }

                    flushRun(target, x, z, runStart, y, activeBlock);
                    activeBlock = block;
                    runStart = y;
                }

                flushRun(target, x, z, runStart, height, activeBlock);
            }
        }
    }

    private void flushRun(ChunkData target, int x, int z, int startY, int endY, BlockData block) {
        if (block == null || startY < 0 || endY <= startY) {
            return;
        }

        int minY = target.getMinHeight();
        if (endY - startY == 1) {
            target.setBlock(x, startY + minY, z, block);
            return;
        }

        target.setRegion(x, startY + minY, z, x + 1, endY + minY, z + 1, block);
    }
}
