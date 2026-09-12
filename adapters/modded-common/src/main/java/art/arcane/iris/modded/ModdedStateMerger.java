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

package art.arcane.iris.modded;

import art.arcane.iris.generation.block.BlockDataMergeSupport;
import art.arcane.iris.spi.PlatformBlockState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

public final class ModdedStateMerger implements BlockDataMergeSupport.StateMerger {
    @Override
    public PlatformBlockState merge(PlatformBlockState base, PlatformBlockState update) {
        if (!(base instanceof ModdedBlockState fabricBase) || !(update instanceof ModdedBlockState fabricUpdate)) {
            return update;
        }
        ModdedBlockState metadataSource = fabricUpdate.isCustom() ? fabricUpdate : fabricBase;

        try {
            return metadataSource.withHandle(mergeStates(fabricBase.handle(), fabricUpdate.handle(), fabricUpdate.parsedProperties()), null);
        } catch (IllegalArgumentException e) {
            ModdedBlockResolution.Parsed normalizedBase = ModdedBlockResolution.resolveGet(ModdedBlockState.serialize(fabricBase.handle()));
            ModdedBlockResolution.Parsed normalizedUpdate = ModdedBlockResolution.resolveGet(ModdedBlockState.serialize(fabricUpdate.handle()));

            if (normalizedBase != null && normalizedUpdate != null) {
                try {
                    return metadataSource.withHandle(mergeStates(normalizedBase.state(), normalizedUpdate.state(), normalizedUpdate.properties()), null);
                } catch (IllegalArgumentException ignored) {
                    return metadataSource.withHandle(normalizedUpdate.state(), normalizedUpdate.properties());
                }
            }

            if (normalizedUpdate != null) {
                return metadataSource.withHandle(normalizedUpdate.state(), normalizedUpdate.properties());
            }

            return update;
        }
    }

    private static BlockState mergeStates(BlockState base, BlockState update, Map<Property<?>, Comparable<?>> parsedProperties) {
        if (parsedProperties == null) {
            throw new IllegalArgumentException("Block data not created via string parsing");
        }
        if (base.getBlock() != update.getBlock()) {
            throw new IllegalArgumentException("States have different types (got " + update.getBlock() + ", expected " + base.getBlock() + ")");
        }
        BlockState merged = base;
        for (Map.Entry<Property<?>, Comparable<?>> entry : parsedProperties.entrySet()) {
            merged = apply(merged, entry.getKey(), entry.getValue());
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property<T>) property, (T) value);
    }
}
