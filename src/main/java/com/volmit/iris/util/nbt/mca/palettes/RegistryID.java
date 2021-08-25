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

import com.google.common.collect.Iterators;
import com.volmit.iris.Iris;
import com.volmit.iris.util.math.MathHelper;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import net.minecraft.world.level.chunk.DataPaletteBlock;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class RegistryID implements Registry {
    public static final int a = -1;
    private static final CompoundTag b = null;
    private static final float c = 0.8F;
    private CompoundTag[] d;
    private int[] e;
    private CompoundTag[] f;
    private int g;
    private int size;

    public RegistryID(int var0) {
        var0 = (int) ((float) var0 / 0.8F);
        this.d = new CompoundTag[var0];
        this.f = new CompoundTag[var0];
        this.e = new int[var0];
    }

    public int getId(CompoundTag block) {
        return this.c(this.b(block, this.d(block)));
    }

    public CompoundTag fromId(int id) {
        return id >= 0 && id < this.f.length ? this.f[id] : null;
    }

    private int c(int var0) {
        return var0 == -1 ? -1 : this.e[var0];
    }

    public boolean b(CompoundTag var0) {
        return this.getId(var0) != -1;
    }

    public boolean b(int var0) {
        return this.fromId(var0) != null;
    }

    public int c(CompoundTag var0) {
        int var1 = this.c();
        this.a(var0, var1);
        return var1;
    }

    
    private int c() {
        while (this.g < this.f.length && this.f[this.g] != null) {
            ++this.g;
        }

        return this.g;
    }

    private void d(int var0) {
        CompoundTag[] var1 = this.d;
        int[] var2 = this.e;
        this.d = new CompoundTag[var0];
        this.e = new int[var0];
        this.f = new CompoundTag[var0];
        this.g = 0;
        this.size = 0;

        for (int var3 = 0; var3 < var1.length; ++var3) {
            if (var1[var3] != null) {
                this.a(var1[var3], var2[var3]);
            }
        }
    }

    public void a(CompoundTag var0, int var1) {
        int var2 = Math.max(var1, this.size + 1);
        int var3;
        if ((float) var2 >= (float) this.d.length * 0.8F) {
            for (var3 = this.d.length << 1; var3 < var1; var3 <<= 1) {
            }

            this.d(var3);
        }

        var3 = this.e(this.d(var0));
        this.d[var3] = var0;
        this.e[var3] = var1;
        this.f[var1] = var0;
        ++this.size;
        if (var1 == this.g) {
            ++this.g;
        }
    }

    private int d(CompoundTag block) {
        return (MathHelper.g(System.identityHashCode(block)) & 2147483647) % this.d.length;
    }

    private int b(CompoundTag block, int var1) {
        int var2;
        for (var2 = var1; var2 < this.d.length; ++var2) {
            if (this.d[var2] == null) {
                Iris.error("-1 because null!");
                return -1;
            }

            if (this.d[var2].equals(block)) {
                return var2;
            }
        }

        for (var2 = 0; var2 < var1; ++var2) {
            if (this.d[var2] == null) {
                Iris.error("-1 because null!");
                return -1;
            }

            if (this.d[var2].equals(block)) {
                return var2;
            }
        }

        return -1;
    }

    private int e(int var0) {
        int var1;
        for (var1 = var0; var1 < this.d.length; ++var1) {
            if (this.d[var1] == null) {
                return var1;
            }
        }

        for (var1 = 0; var1 < var0; ++var1) {
            if (this.d[var1] == null) {
                return var1;
            }
        }

        throw new RuntimeException("Overflowed :(");
    }

    public Iterator<CompoundTag> iterator() {
        return Iterators.filter(Iterators.forArray(this.f), Objects::nonNull);
    }

    public void clear() {
        Arrays.fill(this.d, null);
        Arrays.fill(this.f, null);
        this.g = 0;
        this.size = 0;
    }

    public int size() {
        return this.size;
    }
}