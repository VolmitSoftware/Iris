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

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.volmit.iris.util.math.MathHelper;

import java.util.Arrays;
import java.util.Iterator;

public class RegistryID<K> implements Registry<K> {
    public static final int a = -1;
    private static final Object b = null;
    private static final float c = 0.8F;
    private K[] d;
    private int[] e;
    private K[] f;
    private int g;
    private int h;

    public RegistryID(int var0) {
        var0 = (int) ((float) var0 / 0.8F);
        this.d = (K[]) new Object[var0];
        this.e = new int[var0];
        this.f = (K[]) new Object[var0];
    }

    public int getId(K var0) {
        return this.c(this.b(var0, this.d(var0)));
    }

    public K fromId(int var0) {
        return var0 >= 0 && var0 < this.f.length ? this.f[var0] : null;
    }

    private int c(int var0) {
        return var0 == -1 ? -1 : this.e[var0];
    }

    public boolean b(K var0) {
        return this.getId(var0) != -1;
    }

    public boolean b(int var0) {
        return this.fromId(var0) != null;
    }

    public int c(K var0) {
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
        K[] var1 = this.d;
        int[] var2 = this.e;
        this.d = (K[]) new Object[var0];
        this.e = new int[var0];
        this.f = (K[]) new Object[var0];
        this.g = 0;
        this.h = 0;

        for (int var3 = 0; var3 < var1.length; ++var3) {
            if (var1[var3] != null) {
                this.a(var1[var3], var2[var3]);
            }
        }

    }

    public void a(K var0, int var1) {
        int var2 = Math.max(var1, this.h + 1);
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
        ++this.h;
        if (var1 == this.g) {
            ++this.g;
        }

    }

    private int d(K var0) {
        return (MathHelper.g(System.identityHashCode(var0)) & 2147483647) % this.d.length;
    }

    private int b(K var0, int var1) {
        int var2;
        for (var2 = var1; var2 < this.d.length; ++var2) {
            if (this.d[var2] == var0) {
                return var2;
            }

            if (this.d[var2] == b) {
                return -1;
            }
        }

        for (var2 = 0; var2 < var1; ++var2) {
            if (this.d[var2] == var0) {
                return var2;
            }

            if (this.d[var2] == b) {
                return -1;
            }
        }

        return -1;
    }

    private int e(int var0) {
        int var1;
        for (var1 = var0; var1 < this.d.length; ++var1) {
            if (this.d[var1] == b) {
                return var1;
            }
        }

        for (var1 = 0; var1 < var0; ++var1) {
            if (this.d[var1] == b) {
                return var1;
            }
        }

        throw new RuntimeException("Overflowed :(");
    }

    public Iterator<K> iterator() {
        return Iterators.filter(Iterators.forArray(this.f), Predicates.notNull());
    }

    public void a() {
        Arrays.fill(this.d, (Object) null);
        Arrays.fill(this.f, (Object) null);
        this.g = 0;
        this.h = 0;
    }

    public int b() {
        return this.h;
    }
}