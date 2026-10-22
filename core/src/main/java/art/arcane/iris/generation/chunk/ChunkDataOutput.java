/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public final class ChunkDataOutput {
    private ChunkDataOutput() {
    }

    public static void apply(ChunkData target, SectionBuffer blocks) {
        if (INMS.get().applyChunkDataBlocks(target, blocks)) {
            return;
        }

        int height = blocks.getHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockData activeBlock = null;
                int runStart = -1;

                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.getStoredRaw(x, y, z);
                    BlockData block = state == null ? null : (BlockData) state.nativeHandle();
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

    private static void flushRun(ChunkData target, int x, int z, int startY, int endY, BlockData block) {
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
