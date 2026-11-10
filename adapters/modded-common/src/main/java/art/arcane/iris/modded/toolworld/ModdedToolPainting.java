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

import art.arcane.iris.modded.ModdedIrisLog;
import com.volmit.tooling.world.ChunkPainter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The chunks of a tool world: whatever the tool's painter paints, and nothing else. The chunk generator of the world asks
 * it to paint every chunk it generates, and the world asks it to paint again over a chunk that is live.
 */
public final class ModdedToolPainting {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final int REPAINT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Set<Heightmap.Types> PRIMED_HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.WORLD_SURFACE_WG,
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

    private final ChunkPainter painter;

    public ModdedToolPainting(ChunkPainter painter) {
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /** Paints {@code chunk}, which the generator is filling and no player can see yet. */
    public ChunkAccess paint(ChunkAccess chunk, ServerLevel level) {
        ChunkPos position = chunk.getPos();
        ChunkBuffer buffer = paintBuffer(position.x(), position.z(), chunk.getMinY(), chunk.getHeight());
        writeSections(chunk, buffer);
        writeBlockEntities(chunk, level, buffer);
        Heightmap.primeHeightmaps(chunk, PRIMED_HEIGHTMAPS);
        return chunk;
    }

    /** Paints again over a live chunk: changes what differs from the paint and sends it to the players watching. */
    void repaint(ServerLevel level, LevelChunk chunk) {
        ChunkPos position = chunk.getPos();
        ChunkBuffer buffer = paintBuffer(position.x(), position.z(), chunk.getMinY(), chunk.getHeight());
        ToolWorldGuard.hostWrite(() -> {
            rewriteDifferences(level, chunk, buffer);
            rewriteBlockEntities(level, position, buffer);
        });
    }

    private ChunkBuffer paintBuffer(int chunkX, int chunkZ, int minY, int height) {
        ChunkBuffer buffer = new ChunkBuffer(minY, height);
        try {
            painter.paint(chunkX, chunkZ, buffer);
        } catch (RuntimeException failure) {
            ModdedIrisLog.error("A tool world painter failed at chunk {},{}.", chunkX, chunkZ, failure);
        }
        return buffer;
    }

    private void writeSections(ChunkAccess chunk, ChunkBuffer buffer) {
        int minY = buffer.minY();
        for (int y = minY; y < buffer.maxY(); ) {
            int sectionIndex = chunk.getSectionIndex(y);
            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionEnd = Math.min(minY + (sectionIndex + 1) * SECTION_SIZE, buffer.maxY());
            section.acquire();
            try {
                writeSection(section, buffer, y, sectionEnd);
            } finally {
                section.release();
            }
            y = sectionEnd;
        }
    }

    private void writeSection(LevelChunkSection section, ChunkBuffer buffer, int fromY, int toY) {
        for (int y = fromY; y < toY; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    BlockState state = buffer.stateAt(x, y, z);
                    if (state != null) {
                        section.setBlockState(x, y & (SECTION_SIZE - 1), z, state, false);
                    }
                }
            }
        }
    }

    private void writeBlockEntities(ChunkAccess chunk, ServerLevel level, ChunkBuffer buffer) {
        ChunkPos position = chunk.getPos();
        for (int y = buffer.minY(); y < buffer.maxY(); y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    BlockState state = buffer.stateAt(x, y, z);
                    if (state != null && state.hasBlockEntity()) {
                        createDefaultBlockEntity(chunk, new BlockPos(position.getMinBlockX() + x, y, position.getMinBlockZ() + z), state);
                    }
                }
            }
        }
        for (ChunkBuffer.Painted painted : buffer.blockEntities()) {
            BlockPos at = new BlockPos(position.getMinBlockX() + painted.x(), painted.y(), position.getMinBlockZ() + painted.z());
            try {
                ToolBlocks.applyPaintedBlockEntity(chunk, level, at, buffer.stateAt(painted.x(), painted.y(), painted.z()), painted.data());
            } catch (RuntimeException failure) {
                ModdedIrisLog.error("A tool world could not write a block entity at {}.", at, failure);
            }
        }
    }

    private static void createDefaultBlockEntity(ChunkAccess chunk, BlockPos at, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(at, state);
        if (blockEntity != null) {
            chunk.setBlockEntity(blockEntity);
        }
    }

    private void rewriteDifferences(ServerLevel level, LevelChunk chunk, ChunkBuffer buffer) {
        ChunkPos position = chunk.getPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = buffer.minY(); y < buffer.maxY(); y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    BlockPos at = new BlockPos(position.getMinBlockX() + x, y, position.getMinBlockZ() + z);
                    BlockState painted = buffer.stateAt(x, y, z);
                    BlockState wanted = painted == null ? air : painted;
                    if (chunk.getBlockState(at) != wanted) {
                        level.setBlock(at, wanted, REPAINT_FLAGS);
                    }
                }
            }
        }
    }

    private void rewriteBlockEntities(ServerLevel level, ChunkPos position, ChunkBuffer buffer) {
        for (ChunkBuffer.Painted painted : buffer.blockEntities()) {
            BlockPos at = new BlockPos(position.getMinBlockX() + painted.x(), painted.y(), position.getMinBlockZ() + painted.z());
            try {
                ToolBlocks.applyBlockEntity(level, at, painted.data());
            } catch (RuntimeException failure) {
                ModdedIrisLog.error("A tool world could not write a block entity at {}.", at, failure);
            }
        }
    }
}
