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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

@RequiredArgsConstructor
public class DataPaletteGlobal implements DataPalette {
    @Getter
    private final RegistryBlockID registry;
    private final CompoundTag air;

    public int getIndex(CompoundTag var0) {
        int var1 = this.registry.getId(var0);
        return var1 == -1 ? 0 : var1;
    }

    public boolean a(Predicate<CompoundTag> var0) {
        return true;
    }

    public CompoundTag getByIndex(int var0) {
        CompoundTag var1 = this.registry.fromId(var0);
        return var1 == null ? this.air : var1;
    }

    public static int aa(int i) {
        for(int j = 1; j < 5; ++j) {
            if ((i & -1 << j * 7) == 0) {
                return j;
            }
        }

        return 5;
    }

    public int a() {
        return aa(0);
    }

    public int b() {
        return this.registry.size();
    }

    @Override
    public void replace(ListTag<CompoundTag> t) {

    }

    @Override
    public ListTag<CompoundTag> getPalette() {
        return null;
    }
}
