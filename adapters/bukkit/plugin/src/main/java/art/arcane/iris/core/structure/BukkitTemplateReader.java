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
import art.arcane.iris.engine.object.prefab.LegacyTileData;
import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.IrisLogging;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplateBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads a Bukkit {@link Structure} as a {@link StructureTemplate}: its size, and the blocks of its first palette. */
final class BukkitTemplateReader {
    private static final String FINAL_STATE_NBT_KEY = "final_state";

    private BukkitTemplateReader() {
    }

    /** The template; empty when the structure holds no palette. */
    static Optional<StructureTemplate> read(Structure structure) {
        if (structure == null || structure.getPalettes().isEmpty()) {
            return Optional.empty();
        }
        BlockVector size = structure.getSize();
        Palette palette = structure.getPalettes().get(0);
        List<TemplateBlock> blocks = new ArrayList<>();
        for (org.bukkit.block.BlockState block : palette.getBlocks()) {
            blocks.add(blockOf(block));
        }
        return Optional.of(new StructureTemplate(
                new BlockPosition(size.getBlockX(), size.getBlockY(), size.getBlockZ()),
                structure.getPaletteCount(),
                structure.getEntityCount(),
                blocks));
    }

    private static TemplateBlock blockOf(org.bukkit.block.BlockState block) {
        Location location = block.getLocation();
        BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockData data = block.getBlockData();
        Material material = data.getMaterial();
        BlockState state = BlockState.parse(PlatformBlockStates.namespaced(BukkitBlockState.of(data).key()));
        if (material == Material.JIGSAW) {
            return new TemplateBlock(position, state, Optional.empty(), readJigsawFinalState(block));
        }
        if (material == Material.STRUCTURE_BLOCK || material == Material.STRUCTURE_VOID) {
            return TemplateBlock.of(position, state, Optional.empty());
        }
        return TemplateBlock.of(position, state, blockEntityOf(block));
    }

    private static Optional<BlockEntityData> blockEntityOf(org.bukkit.block.BlockState block) {
        try {
            LegacyTileData tile = LegacyTileData.fromBukkit(block);
            return tile == null ? Optional.empty() : Optional.of(BlockEntities.of(tile));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return Optional.empty();
        }
    }

    private static Optional<String> readJigsawFinalState(org.bukkit.block.BlockState block) {
        try {
            Object nbt = block.getClass().getMethod("getSnapshotNBT").invoke(block);
            if (nbt == null) {
                return Optional.empty();
            }
            Object res = nbt.getClass().getMethod("getString", String.class).invoke(nbt, FINAL_STATE_NBT_KEY);
            String finalState = null;
            if (res instanceof String s) {
                finalState = s;
            } else if (res instanceof Optional<?> o && o.isPresent()) {
                finalState = String.valueOf(o.get());
            }
            return finalState == null || finalState.isBlank() ? Optional.empty() : Optional.of(finalState);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return Optional.empty();
        }
    }
}
