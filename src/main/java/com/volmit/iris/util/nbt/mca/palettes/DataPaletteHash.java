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
import net.minecraft.network.PacketDataSerializer;

import java.util.function.Function;
import java.util.function.Predicate;

public class DataPaletteHash implements DataPalette {
    private final RegistryBlockID registryBlock;
    private final RegistryID registryId;
    private final DataPaletteExpandable expander;
    private final int bits;

    public DataPaletteHash(RegistryBlockID var0, int bits, DataPaletteExpandable expander) {
        this.registryBlock = var0;
        this.bits = bits;
        this.expander = expander;
        this.registryId = new RegistryID(1 << bits);
    }

    public int getIndex(CompoundTag var0) {
        int var1 = this.registryId.getId(var0);
        if (var1 == -1) {
            var1 = this.registryId.c(var0);
            if (var1 >= 1 << this.bits) {
                var1 = this.expander.onResize(this.bits + 1, var0);
            }
        }

        return var1;
    }

    public boolean a(Predicate<CompoundTag> var0) {
        for (int var1 = 0; var1 < this.b(); ++var1) {
            if (var0.test(this.registryId.fromId(var1))) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag getByIndex(int var0) {
        return this.registryId.fromId(var0);
    }

    public void a(PacketDataSerializer var0) {
        this.registryId.a();
        int var1 = var0.j();

        for (int var2 = 0; var2 < var1; ++var2) {
            this.registryId.c(this.registryBlock.fromId(var0.j()));
        }

    }

    public void b(PacketDataSerializer var0) {
        int var1 = this.b();
        var0.d(var1);

        for (int var2 = 0; var2 < var1; ++var2) {
            var0.d(this.registryBlock.getId(this.registryId.fromId(var2)));
        }

    }

    public int a() {
        int var0 = PacketDataSerializer.a(this.b());

        for (int var1 = 0; var1 < this.b(); ++var1) {
            var0 += PacketDataSerializer.a(this.registryBlock.getId(this.registryId.fromId(var1)));
        }

        return var0;
    }

    public int b() {
        return this.registryId.b();
    }

    public void replace(ListTag<CompoundTag> var0) {
        this.registryId.a();

        for (int var1 = 0; var1 < var0.size(); ++var1) {
            this.registryId.c(var0.get(var1));
        }
    }

    @Override
    public ListTag<CompoundTag> getPalette() {
        return null;
    }

    public void writePalette(ListTag<CompoundTag> var0) {
        for (int var1 = 0; var1 < this.b(); ++var1) {
            var0.add((CompoundTag) this.registryId.fromId(var1));
        }

    }
}
