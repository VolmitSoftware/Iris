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

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** Blocks, positions and block entities between the tool world api and Minecraft. */
public final class ToolBlocks {
    private static final int SILENT_BLOCK_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;
    private static final PlatformBlockStates PLATFORM_STATES = new PlatformBlockStates(ToolBlocks::platformStateOf);

    private ToolBlocks() {
    }

    static BlockPosition positionOf(BlockPos position) {
        return new BlockPosition(position.getX(), position.getY(), position.getZ());
    }

    static BlockPos blockPosOf(BlockPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    /** The complete state of {@code state}, with every property. */
    public static BlockState apiStateOf(net.minecraft.world.level.block.state.BlockState state) {
        return BlockState.parse(ModdedBlockState.serialize(state));
    }

    /**
     * The platform's state for {@code state}.
     *
     * @throws IllegalArgumentException when the platform has no such block or property
     */
    static net.minecraft.world.level.block.state.BlockState minecraftStateOf(BlockState state) {
        return (net.minecraft.world.level.block.state.BlockState) PLATFORM_STATES.toPlatform(state).nativeHandle();
    }

    private static PlatformBlockState platformStateOf(String key) {
        PlatformBlockState state = B.getStateOrNull(key, false);
        if (state == null) {
            throw new IllegalArgumentException("The platform has no block state " + key);
        }
        return state;
    }

    static Optional<BlockFace> faceOf(Direction direction) {
        return direction == null ? Optional.empty() : Optional.of(BlockFace.valueOf(direction.name()));
    }

    /** The block entity at {@code position}, in Iris's tile format; empty for a block without one. */
    public static Optional<BlockEntityData> blockEntityOf(ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
            String blockKey = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()).toString();
            TileData tile = ModdedTileData.capture(blockKey, NbtUtils.structureToSnbt(tag));
            return Optional.of(BlockEntities.of(tile));
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot read the block entity at " + position, failure);
        }
    }

    /**
     * Writes {@code data} into the block entity at {@code position}, which holds the block it was made for already; makes
     * the block entity when the block has none yet.
     *
     * @throws IllegalArgumentException when the block cannot take it, or the data is not in the format Iris stores
     */
    static void applyBlockEntity(Level level, BlockPos position, BlockEntityData data) {
        ModdedTileData tile = tileOf(data);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(position);
        net.minecraft.world.level.block.state.BlockState adjusted = tile.adjustBlockState(state);
        if (adjusted != state) {
            level.setBlock(position, adjusted, SILENT_BLOCK_FLAGS);
            state = adjusted;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null && state.getBlock() instanceof EntityBlock entityBlock) {
            blockEntity = entityBlock.newBlockEntity(position, state);
        }
        if (blockEntity == null || !tile.isApplicable(state, blockEntity)) {
            throw new IllegalArgumentException("The block " + state + " at " + position
                    + " cannot take block entity data of " + tile.getMaterialKey());
        }
        level.setBlockEntity(blockEntity);
        applyTile(tile, blockEntity, level);
    }

    /** Makes the block entity of a block painted into a chunk that is not live yet. */
    static void applyPaintedBlockEntity(net.minecraft.world.level.chunk.ChunkAccess chunk, ServerLevel level, BlockPos position,
                                        net.minecraft.world.level.block.state.BlockState state, BlockEntityData data) {
        ModdedTileData tile = tileOf(data);
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            throw new IllegalArgumentException("The block " + state + " has no block entity for " + tile.getMaterialKey());
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(position, state);
        if (blockEntity == null || !tile.isApplicable(state, blockEntity)) {
            throw new IllegalArgumentException("The block " + state + " cannot take block entity data of " + tile.getMaterialKey());
        }
        chunk.setBlockEntity(blockEntity);
        applyTile(tile, blockEntity, level);
    }

    private static void applyTile(ModdedTileData tile, BlockEntity blockEntity, Level level) {
        try {
            tile.apply(blockEntity, (ServerLevel) level);
        } catch (Exception failure) {
            throw new IllegalArgumentException("The block entity data of " + tile.getMaterialKey() + " was rejected", failure);
        }
    }

    private static ModdedTileData tileOf(BlockEntityData data) {
        TileData tile = BlockEntities.tileOf(data);
        if (tile instanceof ModdedTileData moddedTile) {
            return moddedTile;
        }
        throw new IllegalArgumentException("Not block entity data of this platform: " + tile.getMaterialKey());
    }
}
