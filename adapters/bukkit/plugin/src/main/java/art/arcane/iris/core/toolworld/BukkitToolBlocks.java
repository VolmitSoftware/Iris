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

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.engine.object.prefab.TileData;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Optional;

/** Blocks, positions and block entities between the tool world api and Bukkit. */
final class BukkitToolBlocks {
    private BukkitToolBlocks() {
    }

    static BlockPosition positionOf(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    static BlockPosition positionOf(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    static Block blockAt(World world, BlockPosition at) {
        return world.getBlockAt(at.x(), at.y(), at.z());
    }

    /** The complete state of {@code data}, with every property. */
    static BlockState stateOf(BlockData data) {
        return BlockState.parse(data.getAsString());
    }

    /**
     * The platform's data for {@code state}.
     *
     * @throws IllegalArgumentException when the platform has no such block or property
     */
    static BlockData dataOf(BlockState state) {
        return Bukkit.createBlockData(state.key());
    }

    /** The block entity of {@code block}; empty for a block without one. */
    static Optional<BlockEntityData> blockEntityOf(Block block) {
        return Optional.ofNullable(TileData.getTileState(block, false)).map(BlockEntities::of);
    }

    /**
     * Writes {@code data} into the block entity of {@code block}, which must already be the block it was made for.
     *
     * @throws IllegalArgumentException when the block cannot take it, or the data is not in the format Iris stores
     */
    static void applyBlockEntity(Block block, BlockEntityData data) {
        TileData tile = BlockEntities.tileOf(data);
        if (!TileData.setTileState(block, tile)) {
            throw new IllegalArgumentException("The block " + block.getType() + " at " + block.getX() + ", " + block.getY()
                    + ", " + block.getZ() + " cannot take block entity data of " + tile.getMaterialKey());
        }
    }

    /** The api face for a Bukkit face; empty for the diagonals and for {@code SELF}. */
    static Optional<BlockFace> faceOf(org.bukkit.block.BlockFace face) {
        if (face == null) {
            return Optional.empty();
        }
        return switch (face) {
            case UP -> Optional.of(BlockFace.UP);
            case DOWN -> Optional.of(BlockFace.DOWN);
            case NORTH -> Optional.of(BlockFace.NORTH);
            case SOUTH -> Optional.of(BlockFace.SOUTH);
            case EAST -> Optional.of(BlockFace.EAST);
            case WEST -> Optional.of(BlockFace.WEST);
            default -> Optional.empty();
        };
    }
}
