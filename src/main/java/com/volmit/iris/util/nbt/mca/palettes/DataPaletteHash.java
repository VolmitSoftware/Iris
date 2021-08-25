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

public class DataPaletteHash<T> implements DataPalette<T> {
    private final RegistryBlockID<T> a;
    private final RegistryID<T> b;
    private final DataPaletteExpandable<T> c;
    private final Function<CompoundTag, T> d;
    private final Function<T, CompoundTag> e;
    private final int f;

    public DataPaletteHash(RegistryBlockID<T> var0, int var1, DataPaletteExpandable<T> var2, Function<CompoundTag, T> var3, Function<T, CompoundTag> var4) {
        this.a = var0;
        this.f = var1;
        this.c = var2;
        this.d = var3;
        this.e = var4;
        this.b = new RegistryID<T>(1 << var1);
    }

    public int a(T var0) {
        int var1 = this.b.getId(var0);
        if (var1 == -1) {
            var1 = this.b.c(var0);
            if (var1 >= 1 << this.f) {
                var1 = this.c.onResize(this.f + 1, var0);
            }
        }

        return var1;
    }

    public boolean a(Predicate<T> var0) {
        for (int var1 = 0; var1 < this.b(); ++var1) {
            if (var0.test(this.b.fromId(var1))) {
                return true;
            }
        }

        return false;
    }

    public T a(int var0) {
        return this.b.fromId(var0);
    }

    public void a(PacketDataSerializer var0) {
        this.b.a();
        int var1 = var0.j();

        for (int var2 = 0; var2 < var1; ++var2) {
            this.b.c(this.a.fromId(var0.j()));
        }

    }

    public void b(PacketDataSerializer var0) {
        int var1 = this.b();
        var0.d(var1);

        for (int var2 = 0; var2 < var1; ++var2) {
            var0.d(this.a.getId(this.b.fromId(var2)));
        }

    }

    public int a() {
        int var0 = PacketDataSerializer.a(this.b());

        for (int var1 = 0; var1 < this.b(); ++var1) {
            var0 += PacketDataSerializer.a(this.a.getId(this.b.fromId(var1)));
        }

        return var0;
    }

    public int b() {
        return this.b.b();
    }

    public void a(ListTag<CompoundTag> var0) {
        this.b.a();

        for (int var1 = 0; var1 < var0.size(); ++var1) {
            this.b.c(this.d.apply(var0.get(var1)));
        }
    }

    @Override
    public ListTag<CompoundTag> getPalette() {
        return null;
    }

    public void b(ListTag<CompoundTag> var0) {
        for (int var1 = 0; var1 < this.b(); ++var1) {
            var0.add(this.e.apply(this.b.fromId(var1)));
        }

    }
}
