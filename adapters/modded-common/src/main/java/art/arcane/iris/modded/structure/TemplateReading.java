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

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.modded.ModdedTileData;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.TemplateBlock;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A vanilla structure template as a {@link com.volmit.tooling.StructureTemplate}: its size, and the blocks of its first palette. */
final class TemplateReading {
    private static final String FINAL_STATE_NBT_KEY = "final_state";
    private static final String PALETTES_FIELD = "palettes";
    private static final String ENTITIES_FIELD = "entityInfoList";

    private TemplateReading() {
    }

    /** The template; empty when it holds no palette. */
    static Optional<com.volmit.tooling.StructureTemplate> read(StructureTemplate template) {
        List<?> palettes = PrivateFields.readList(template, PALETTES_FIELD);
        if (palettes.isEmpty()) {
            return Optional.empty();
        }
        Vec3i size = template.getSize();
        List<TemplateBlock> blocks = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : ((StructureTemplate.Palette) palettes.getFirst()).blocks()) {
            blocks.add(blockOf(info));
        }
        return Optional.of(new com.volmit.tooling.StructureTemplate(
                new BlockPosition(size.getX(), size.getY(), size.getZ()),
                palettes.size(),
                PrivateFields.readList(template, ENTITIES_FIELD).size(),
                blocks));
    }

    private static TemplateBlock blockOf(StructureTemplate.StructureBlockInfo info) {
        BlockPosition position = new BlockPosition(info.pos().getX(), info.pos().getY(), info.pos().getZ());
        BlockState state = BlockState.parse(ModdedBlockState.serialize(info.state()));
        if (info.state().is(Blocks.JIGSAW)) {
            return new TemplateBlock(position, state, Optional.empty(), finalStateOf(info.nbt()));
        }
        if (info.state().is(Blocks.STRUCTURE_BLOCK) || info.state().is(Blocks.STRUCTURE_VOID)) {
            return TemplateBlock.of(position, state, Optional.empty());
        }
        return TemplateBlock.of(position, state, blockEntityOf(info));
    }

    private static Optional<BlockEntityData> blockEntityOf(StructureTemplate.StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        if (nbt == null || !info.state().hasBlockEntity()) {
            return Optional.empty();
        }
        try {
            String blockKey = BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString();
            return Optional.of(BlockEntities.of(ModdedTileData.capture(blockKey, NbtUtils.structureToSnbt(nbt))));
        } catch (java.io.IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris could not read the block entity of a template block at {}.", info.pos(), failure);
            return Optional.empty();
        }
    }

    private static Optional<String> finalStateOf(CompoundTag nbt) {
        if (nbt == null) {
            return Optional.empty();
        }
        return nbt.getString(FINAL_STATE_NBT_KEY).filter(finalState -> !finalState.isBlank());
    }
}
