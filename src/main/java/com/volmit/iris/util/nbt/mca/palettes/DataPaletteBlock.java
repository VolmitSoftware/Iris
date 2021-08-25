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

import com.volmit.iris.util.math.MathHelper;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.network.PacketDataSerializer;
import org.bukkit.Material;

import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Predicate;

@Getter
public class DataPaletteBlock<T> implements DataPaletteExpandable<T> {
    private static final int d = 4096;
    public static final int a = 9;
    public static final int b = 4;
    private final DataPalette<T> e;
    private final DataPaletteExpandable<T> f = (var0x, var1x) -> {
        return 0;
    };
    private final RegistryBlockID<T> g;
    private final Function<CompoundTag, T> h;
    private final Function<T, CompoundTag> i;
    private final T j;
    private static final RegistryBlockID<CompoundTag> registry = new RegistryBlockID<>();
    private static final CompoundTag air = NBTWorld.getCompound(Material.AIR.createBlockData());
    protected DataBits c;
    private DataPalette<T> k;
    private int bits;
    private final Semaphore m = new Semaphore(1);

    public void b() {
        this.m.release();
    }

    public DataPaletteBlock() {
        this(null, (RegistryBlockID<T>) registry, (i) -> (T) i, (i) -> (CompoundTag) i, (T) air);
    }

    public DataPaletteBlock(DataPalette<T> var0,
                            RegistryBlockID<T> var1,
                            Function<CompoundTag, T> var2,
                            Function<T, CompoundTag> var3,
                            T var4) {
        this.e = var0;
        this.g = var1;
        this.h = var2;
        this.i = var3;
        this.j = var4;
        this.b(4);
    }

    private static int b(int var0, int var1, int var2) {
        return var1 << 8 | var2 << 4 | var0;
    }

    private void b(int var0) {
        if (var0 != this.bits) {
            this.bits = var0;
            if (this.bits <= 4) {
                this.bits = 4;
                this.k = new DataPaletteLinear<T>(this.g, this.bits, this, this.h);
            } else if (this.bits < 9) {
                this.k = new DataPaletteHash<T>(this.g, this.bits, this, this.h, this.i);
            } else {
                this.k = this.e;
                this.bits = MathHelper.e(this.g.a());
            }

            this.k.a(this.j);
            this.c = new DataBits(this.bits, 4096);
        }
    }

    public int onResize(int var0, T var1) {
        DataBits var2 = this.c;
        DataPalette<T> var3 = this.k;
        this.b(var0);

        for (int var4 = 0; var4 < var2.b(); ++var4) {
            T var5 = var3.a(var2.a(var4));
            if (var5 != null) {
                this.setBlockIndex(var4, var5);
            }
        }

        return this.k.a(var1);
    }

    public T setBlock(int var0, int var1, int var2, T var3) {
        T var6;
        try {
            var6 = this.a(b(var0, var1, var2), var3);
        } finally {
            this.b();
        }

        return var6;
    }

    public T b(int var0, int var1, int var2, T var3) {
        return this.a(b(var0, var1, var2), var3);
    }

    private T a(int var0, T var1) {
        int var2 = this.k.a(var1);
        int var3 = this.c.a(var0, var2);
        T var4 = this.k.a(var3);
        return var4 == null ? this.j : var4;
    }

    public void c(int var0, int var1, int var2, T var3) {
        try {
            this.setBlockIndex(b(var0, var1, var2), var3);
        } finally {
            this.b();
        }
    }

    private void setBlockIndex(int var0, T var1) {
        int var2 = this.k.a(var1);
        this.c.b(var0, var2);
    }

    public T a(int var0, int var1, int var2) {
        return this.a(b(var0, var1, var2));
    }

    protected T a(int var0) {
        T var1 = this.k.a(this.c.a(var0));
        return var1 == null ? this.j : var1;
    }

    public void a(ListTag<CompoundTag> palettedata, long[] databits) {
        try {
            int var2 = Math.max(4, MathHelper.e(palettedata.size()));
            if (var2 != this.bits) {
                this.b(var2);
            }

            this.k.a(palettedata);
            int var3 = databits.length * 64 / 4096;
            if (this.k == this.e) {
                DataPalette<T> var4 = new DataPaletteHash<T>(this.g, var2, this.f, this.h, this.i);
                var4.a(palettedata);
                DataBits var5 = new DataBits(var2, 4096, databits);

                for (int var6 = 0; var6 < 4096; ++var6) {
                    this.c.b(var6, this.e.a(var4.a(var5.a(var6))));
                }
            } else if (var3 == this.bits) {
                System.arraycopy(databits, 0, this.c.a(), 0, databits.length);
            } else {
                DataBits var4 = new DataBits(var3, 4096, databits);

                for (int var5 = 0; var5 < 4096; ++var5) {
                    this.c.b(var5, var4.a(var5));
                }
            }
        } finally {
            this.b();
        }
    }

    public void a(CompoundTag var0, String var1, String var2) {
        try {
            DataPaletteHash<T> var3 = new DataPaletteHash(this.g, this.bits, this.f, this.h, this.i);
            T var4 = this.j;
            int var5 = var3.a(this.j);
            int[] var6 = new int[4096];

            for (int var7 = 0; var7 < 4096; ++var7) {
                T var8 = this.a(var7);
                if (var8 != var4) {
                    var4 = var8;
                    var5 = var3.a(var8);
                }

                var6[var7] = var5;
            }

            ListTag<CompoundTag> var7 = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
            var3.b(var7);
            var0.put(var1, var7);
            int var8 = Math.max(4, MathHelper.e(var7.size()));
            DataBits var9 = new DataBits(var8, 4096);

            for (int var10 = 0; var10 < var6.length; ++var10) {
                var9.b(var10, var6[var10]);
            }

            var0.putLongArray(var2, var9.a());
        } finally {
            this.b();
        }
    }

    public int c() {
        return 1 + this.k.a() + PacketDataSerializer.a(this.c.b()) + this.c.a().length * 8;
    }

    public boolean contains(Predicate<T> var0) {
        return this.k.a(var0);
    }

    public void a(DataPaletteBlock.a<T> var0) {
        Int2IntMap var1 = new Int2IntOpenHashMap();
        this.c.a((var1x) -> var1.put(var1x, var1.get(var1x) + 1));
        var1.int2IntEntrySet().forEach((var1x) -> var0.accept(this.k.a(var1x.getIntKey()), var1x.getIntValue()));
    }

    @FunctionalInterface
    public interface a<T> {
        void accept(T var1, int var2);
    }
}