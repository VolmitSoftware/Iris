/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.nbt.mca.palette;

import com.volmit.iris.util.nbt.tag.ByteArrayTag;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.LongArrayTag;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@RequiredArgsConstructor
public class MCAWrappedPalettedContainer<T> implements MCAPaletteAccess {
    private final MCAPalettedContainer<T> container;
    private final Function<T, CompoundTag> reader;
    private final Function<CompoundTag, T> writer;

    public void setBlock(int x, int y, int z, CompoundTag data) {
        container.set(x, y, z, writer.apply(data));
    }

    public CompoundTag getBlock(int x, int y, int z) {
        return reader.apply(container.get(x, y, z));
    }

    public void writeToSection(CompoundTag tag) {
        container.write(tag, "Palette", "BlockStates");
    }

    public void readFromSection(CompoundTag tag) {
        //   container.read(tag.getCompoundTag("block_states").getListTag("palette"), tag.getCompoundTag("block_states").getLongArrayTag("data").getValue());
        CompoundTag blockStates = tag.getCompoundTag("block_states");
        if (blockStates == null) {
            throw new IllegalArgumentException("block_states tag is missing");
        }
        LongArrayTag longData = blockStates.getLongArrayTag("data");
        if (longData != null && longData.getValue() != null) {
            container.read(tag.getCompoundTag("block_states").getListTag("palette"), tag.getCompoundTag("block_states").getLongArrayTag("data").getValue());
        } else {
            ByteArrayTag byteData = blockStates.getByteArrayTag("data");
            if (byteData == null) {
                container.read(tag.getCompoundTag("block_states").getListTag("palette"));
            } else {
                throw new IllegalArgumentException("No palette data tag found or data value is null");
            }
        }
    }

}
