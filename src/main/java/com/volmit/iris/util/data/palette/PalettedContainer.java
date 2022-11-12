/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.data.palette;

import com.volmit.iris.Iris;
import com.volmit.iris.util.math.M;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.List;

public class PalettedContainer<T> implements PaletteResize<T> {
    public static final int GLOBAL_PALETTE_BITS = 9;
    public static final int MIN_PALETTE_SIZE = 4;
    private static final int SIZE = 4096;
    private final PaletteResize<T> dummyPaletteResize = (var0, var1) -> 0;
    protected BitStorage storage;
    private Palette<T> palette;
    private int bits;

    public PalettedContainer() {
        setBits(4);
    }

    private static int getIndex(int var0, int var1, int var2) {
        return var1 << 8 | var2 << 4 | var0;
    }

    private void setBits(int var0) {
        if (var0 == this.bits) {
            return;
        }
        this.bits = var0;
        if (this.bits <= 4) {
            this.bits = 4;
            this.palette = new LinearPalette<>(this.bits, this);
        } else {
            this.palette = new HashMapPalette<>(this.bits, this);
        }

        this.palette.idFor(null);
        this.storage = new BitStorage(this.bits, 4096);
    }

    public int onResize(int var0, T var1) {
        BitStorage var2 = this.storage;
        Palette<T> var3 = this.palette;
        setBits(var0);
        for (int var4 = 0; var4 < var2.getSize(); var4++) {
            T var5 = var3.valueFor(var2.get(var4));
            if (var5 != null) {
                set(var4, var5);
            }
        }

        return this.palette.idFor(var1);
    }

    public T getAndSet(int var0, int var1, int var2, T var3) {
        return getAndSet(getIndex(var0, var1, var2), var3);
    }

    public T getAndSetUnchecked(int var0, int var1, int var2, T var3) {
        return getAndSet(getIndex(var0, var1, var2), var3);
    }

    private T getAndSet(int var0, T var1) {
        int var2 = this.palette.idFor(var1);
        int var3 = this.storage.getAndSet(var0, var2);
        return this.palette.valueFor(var3);
    }

    public void set(int var0, int var1, int var2, T var3) {
        set(getIndex(var0, var1, var2), var3);
    }

    private void set(int var0, T var1) {
        int var2 = this.palette.idFor(var1);

        if (M.r(0.003)) {
            Iris.info("ID for " + var1 + " is " + var2 + " Palette: " + palette.getSize());
        }

        this.storage.set(var0, var2);
    }

    public T get(int var0, int var1, int var2) {
        return get(getIndex(var0, var1, var2));
    }

    protected T get(int var0) {
        return this.palette.valueFor(this.storage.get(var0));
    }

    public void read(List<T> palette, long[] data) {
        int var2 = Math.max(4, Mth.ceillog2(palette.size()));
        if (var2 != this.bits) {
            setBits(var2);
        }

        this.palette.read(palette);
        int var3 = data.length * 64 / 4096;
        if (var3 == this.bits) {
            System.arraycopy(data, 0, this.storage.getRaw(), 0, data.length);
        } else {
            BitStorage var4 = new BitStorage(var3, 4096, data);
            for (int var5 = 0; var5 < 4096; var5++) {
                this.storage.set(var5, var4.get(var5));
            }
        }
    }

    public long[] write(List<T> toList) {
        HashMapPalette<T> var3 = new HashMapPalette<>(this.bits, this.dummyPaletteResize);
        T var4 = null;
        int var5 = 0;
        int[] var6 = new int[4096];
        for (int i = 0; i < 4096; i++) {
            T t = get(i);
            if (t != var4) {
                var4 = t;
                var5 = var3.idFor(t);
            }
            var6[i] = var5;
        }

        var3.write(toList);
        int var8 = Math.max(4, Mth.ceillog2(toList.size()));
        BitStorage var9 = new BitStorage(var8, 4096);
        for (int var10 = 0; var10 < var6.length; var10++) {
            var9.set(var10, var6[var10]);
        }
        return var9.getRaw();
    }

    public void count(CountConsumer<T> var0) {
        Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
        this.storage.getAll(var1 -> int2IntOpenHashMap.put(var1, int2IntOpenHashMap.get(var1) + 1));
        int2IntOpenHashMap.int2IntEntrySet().forEach(var1 -> var0.accept(this.palette.valueFor(var1.getIntKey()), var1.getIntValue()));
    }
}