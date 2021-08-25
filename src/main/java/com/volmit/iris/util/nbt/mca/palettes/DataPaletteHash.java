/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.nbt.mca.palettes;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;

import java.util.function.Predicate;

public class DataPaletteHash implements DataPalette {
    private final RegistryID registryId;
    private final DataPaletteExpandable expander;
    private final int bits;

    public DataPaletteHash(int bits, DataPaletteExpandable expander) {
        this.bits = bits;
        this.expander = expander;
        this.registryId = new RegistryID(1 << bits);
    }

    public int getIndex(CompoundTag block) {
        int id = registryId.getId(block);
        if (id == -1) {
            id = registryId.c(block);
            if (id >= 1 << bits) {
                id = expander.onResize(bits + 1, block);
            }
        }

        return id;
    }

    public boolean contains(Predicate<CompoundTag> predicate) {
        for (int i = 0; i < size(); ++i) {
            if (predicate.test(registryId.fromId(i))) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag getByIndex(int index) {
        return registryId.fromId(index);
    }

    public int size() {
        return registryId.size();
    }

    public void replace(ListTag<CompoundTag> palette) {
        registryId.clear();

        for (int i = 0; i < palette.size(); ++i) {
            registryId.c(palette.get(i));
        }
    }

    public void writePalette(ListTag<CompoundTag> list) {
        for (int i = 0; i < size(); ++i) {
            list.add(registryId.fromId(i));
        }
    }
}
