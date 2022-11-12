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

package com.volmit.iris.util.nbt.mca.palette;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.function.Function;
import java.util.function.Predicate;

public class MCAPalettedContainer<T> implements MCAPaletteResize<T> {
    public static final int GLOBAL_PALETTE_BITS = 9;
    public static final int MIN_PALETTE_SIZE = 4;
    private static final int SIZE = 4096;
    private final MCAPalette<T> globalPalette;

    private final MCAPaletteResize<T> dummyPaletteResize = (var0, var1) -> 0;

    private final MCAIdMapper<T> registry;

    private final Function<CompoundTag, T> reader;

    private final Function<T, CompoundTag> writer;

    private final T defaultValue;

    protected MCABitStorage storage;

    private MCAPalette<T> palette;

    private int bits;

    public MCAPalettedContainer(MCAPalette<T> var0, MCAIdMapper<T> var1, Function<CompoundTag, T> var2, Function<T, CompoundTag> var3, T var4) {
        this.globalPalette = var0;
        this.registry = var1;
        this.reader = var2;
        this.writer = var3;
        this.defaultValue = var4;
        setBits(4);
    }

    private static int getIndex(int var0, int var1, int var2) {
        return var1 << 8 | var2 << 4 | var0;
    }

    private void setBits(int var0) {
        if (var0 == this.bits)
            return;
        this.bits = var0;
        if (this.bits <= 4) {
            this.bits = 4;
            this.palette = new MCALinearPalette<>(this.registry, this.bits, this, this.reader);
        } else if (this.bits < 9) {
            this.palette = new MCAHashMapPalette<>(this.registry, this.bits, this, this.reader, this.writer);
        } else {
            this.palette = this.globalPalette;
            this.bits = MCAMth.ceillog2(this.registry.size());
        }
        this.palette.idFor(this.defaultValue);
        this.storage = new MCABitStorage(this.bits, 4096);
    }

    public int onResize(int var0, T var1) {
        MCABitStorage var2 = this.storage;
        MCAPalette<T> var3 = this.palette;
        setBits(var0);
        for (int var4 = 0; var4 < var2.getSize(); var4++) {
            T var5 = var3.valueFor(var2.get(var4));
            if (var5 != null)
                set(var4, var5);
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
        T var4 = this.palette.valueFor(var3);
        return (var4 == null) ? this.defaultValue : var4;
    }

    public void set(int var0, int var1, int var2, T var3) {
        set(getIndex(var0, var1, var2), var3);
    }

    private void set(int var0, T var1) {
        int var2 = this.palette.idFor(var1);
        this.storage.set(var0, var2);
    }

    public T get(int var0, int var1, int var2) {
        return get(getIndex(var0, var1, var2));
    }

    protected T get(int var0) {
        T var1 = this.palette.valueFor(this.storage.get(var0));
        return (var1 == null) ? this.defaultValue : var1;
    }

    public void read(ListTag var0, long[] var1) {
        int var2 = Math.max(4, MCAMth.ceillog2(var0.size()));
        if (var2 != this.bits)
            setBits(var2);
        this.palette.read(var0);
        int var3 = var1.length * 64 / 4096;
        if (this.palette == this.globalPalette) {
            MCAPalette<T> var4 = new MCAHashMapPalette<>(this.registry, var2, this.dummyPaletteResize, this.reader, this.writer);
            var4.read(var0);
            MCABitStorage var5 = new MCABitStorage(var2, 4096, var1);
            for (int var6 = 0; var6 < 4096; var6++)
                this.storage.set(var6, this.globalPalette.idFor(var4.valueFor(var5.get(var6))));
        } else if (var3 == this.bits) {
            System.arraycopy(var1, 0, this.storage.getRaw(), 0, var1.length);
        } else {
            MCABitStorage var4 = new MCABitStorage(var3, 4096, var1);
            for (int var5 = 0; var5 < 4096; var5++)
                this.storage.set(var5, var4.get(var5));
        }
    }

    public void write(CompoundTag var0, String var1, String var2) {
        MCAHashMapPalette<T> var3 = new MCAHashMapPalette<>(this.registry, this.bits, this.dummyPaletteResize, this.reader, this.writer);
        T var4 = this.defaultValue;
        int var5 = var3.idFor(this.defaultValue);
        int[] var6 = new int[4096];
        for (int i = 0; i < 4096; i++) {
            T t = get(i);
            if (t != var4) {
                var4 = t;
                var5 = var3.idFor(t);
            }
            var6[i] = var5;
        }
        ListTag<CompoundTag> paletteList = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        var3.write(paletteList);
        var0.put(var1, paletteList);
        int var8 = Math.max(4, MCAMth.ceillog2(paletteList.size()));
        MCABitStorage var9 = new MCABitStorage(var8, 4096);
        for (int var10 = 0; var10 < var6.length; var10++) {
            var9.set(var10, var6[var10]);
        }
        var0.putLongArray(var2, var9.getRaw());
    }

    public boolean maybeHas(Predicate<T> var0) {
        return this.palette.maybeHas(var0);
    }

    public void count(MCACountConsumer<T> var0) {
        Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
        this.storage.getAll(var1 -> int2IntOpenHashMap.put(var1, int2IntOpenHashMap.get(var1) + 1));
        int2IntOpenHashMap.int2IntEntrySet().forEach(var1 -> var0.accept(this.palette.valueFor(var1.getIntKey()), var1.getIntValue()));
    }
}