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

public class DataPaletteLinear<T> implements DataPalette<T> {
    private final RegistryBlockID<T> a;
    private final T[] b;
    private final DataPaletteExpandable<T> c;
    private final Function<CompoundTag, T> d;
    private final int e;
    private int f;

    public DataPaletteLinear(RegistryBlockID<T> var0, int var1, DataPaletteExpandable<T> var2, Function<CompoundTag, T> var3) {
        this.a = var0;
        this.b = (T[]) new Object[1 << var1];
        this.e = var1;
        this.c = var2;
        this.d = var3;
    }

    public int getIndex(T var0) {
        int var1;
        for (var1 = 0; var1 < this.f; ++var1) {
            if (this.b[var1].equals(var0)) {
                return var1;
            }
        }

        var1 = this.f;
        if (var1 < this.b.length) {
            this.b[var1] = var0;
            ++this.f;
            return var1;
        } else {
            return this.c.onResize(this.e + 1, var0);
        }
    }

    public boolean a(Predicate<T> var0) {
        for (int var1 = 0; var1 < this.f; ++var1) {
            if (var0.test(this.b[var1])) {
                return true;
            }
        }

        return false;
    }

    public T getByIndex(int var0) {
        return var0 >= 0 && var0 < this.f ? this.b[var0] : null;
    }

    public void a(PacketDataSerializer var0) {
        this.f = var0.j();

        for (int var1 = 0; var1 < this.f; ++var1) {
            this.b[var1] = this.a.fromId(var0.j());
        }

    }

    public void b(PacketDataSerializer var0) {
        var0.d(this.f);

        for (int var1 = 0; var1 < this.f; ++var1) {
            var0.d(this.a.getId(this.b[var1]));
        }

    }

    public int a() {
        int var0 = PacketDataSerializer.a(this.b());

        for (int var1 = 0; var1 < this.b(); ++var1) {
            var0 += PacketDataSerializer.a(this.a.getId(this.b[var1]));
        }

        return var0;
    }

    public int b() {
        return this.f;
    }

    public void replace(ListTag<CompoundTag> var0) {
        for (int var1 = 0; var1 < var0.size(); ++var1) {
            this.b[var1] = this.d.apply(var0.get(var1));
        }

        this.f = var0.size();
    }

    @Override
    public ListTag<CompoundTag> getPalette() {
        ListTag<CompoundTag> c = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        for(T i : b)
        {
            c.add((CompoundTag) i);
        }

        return c;
    }
}
