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

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.math.MathHelper;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.network.PacketDataSerializer;
import org.bukkit.Material;

import java.util.function.Function;
import java.util.function.Predicate;

@Getter
public class DataPaletteBlock<T> implements DataPaletteExpandable<T> {
    private static final int d = 4096;
    public static final int HASH_BITS = 9;
    public static final int LINEAR_BITS = 4;
    private final DataPalette<T> globalPalette;
    private final DataPaletteExpandable<T> f = (var0x, var1x) -> 0;
    private final RegistryBlockID<T> stolenRegistry;
    private final Function<CompoundTag, T> h;
    private final Function<T, CompoundTag> i;
    private final T defAir;
    private static final AtomicCache<RegistryBlockID<CompoundTag>> reg = new AtomicCache<>();
    private static final AtomicCache<DataPaletteGlobal<CompoundTag>> global = new AtomicCache<>();
    private static final CompoundTag air = NBTWorld.getCompound(Material.AIR.createBlockData());
    protected DataBits dataBits;
    private DataPalette<T> currentPalette;
    private int bits = 0;

    public DataPaletteBlock() {
        this((DataPalette<T>) global.aquire(() ->
             new DataPaletteGlobal<>(reg.aquire(() -> {
                try {
                    return INMS.get().computeBlockIDRegistry();
                } catch (NoSuchFieldException ex) {
                    ex.printStackTrace();
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                }
                return null;
            }), air)
        ), (RegistryBlockID<T>) reg.aquire(() -> {
            try {
                return INMS.get().computeBlockIDRegistry();
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
            }
            return null;
        }), (i) -> (T) i, (i) -> (CompoundTag) i, (T) air);
    }

    public DataPaletteBlock(DataPalette<T> var0,
                            RegistryBlockID<T> var1,
                            Function<CompoundTag, T> var2,
                            Function<T, CompoundTag> var3,
                            T var4) {
        this.globalPalette = var0;
        this.stolenRegistry = var1;
        this.h = var2;
        this.i = var3;
        this.defAir = var4;
        this.changeBitsTo(4);
    }

    private static int blockIndex(int var0, int var1, int var2) {
        return var1 << 8 | var2 << 4 | var0;
    }

    private void changeBitsTo(int newbits) {
        if (newbits != this.bits) {
            this.bits = newbits;
            if (this.bits <= LINEAR_BITS) {
                this.bits = LINEAR_BITS;
                this.currentPalette = new DataPaletteLinear<T>(this.stolenRegistry, this.bits, this, this.h);
            } else if (this.bits < HASH_BITS) {
                this.currentPalette = new DataPaletteHash<T>(this.stolenRegistry, this.bits, this, this.h, this.i);
            } else {
                this.currentPalette = this.globalPalette;
                this.bits = MathHelper.e(this.stolenRegistry.size());
            }

            this.currentPalette.getIndex(this.defAir);
            this.dataBits = new DataBits(this.bits, 4096);
        }
    }

    public int onResize(int var0, T var1) {
        DataBits var2 = this.dataBits;
        DataPalette<T> var3 = this.currentPalette;
        this.changeBitsTo(var0);

        for (int var4 = 0; var4 < var2.b(); ++var4) {
            T var5 = var3.getByIndex(var2.getIndexFromPos(var4));
            if (var5 != null) {
                this.setBlockIndex(var4, var5);
            }
        }

        return this.currentPalette.getIndex(var1);
    }

    public T setBlock(int var0, int var1, int var2, T var3) {
        return this.a(blockIndex(var0, var1, var2), var3);
    }

    private T a(int var0, T var1) {
        int var2 = this.currentPalette.getIndex(var1);
        int var3 = this.dataBits.a(var0, var2);
        T var4 = this.currentPalette.getByIndex(var3);
        return var4 == null ? this.defAir : var4;
    }

    public void c(int var0, int var1, int var2, T var3) {
        this.setBlockIndex(blockIndex(var0, var1, var2), var3);
    }

    private void setBlockIndex(int var0, T var1) {
        int var2 = this.currentPalette.getIndex(var1);
        this.dataBits.b(var0, var2);
    }

    public T getBlock(int var0, int var1, int var2) {
        return this.getByIndex(blockIndex(var0, var1, var2));
    }

    protected T getByIndex(int var0) {
        if(this.currentPalette == null)
        {
            return null;
        }

        T data = this.currentPalette.getByIndex(this.dataBits.getIndexFromPos(var0));
        return data == null ? this.defAir : data;
    }

    public void load(ListTag<CompoundTag> palettedata, long[] databits) {
        int readBits = Math.max(4, MathHelper.e(palettedata.size()));
        if (readBits != this.bits) {
            this.changeBitsTo(readBits);
        }

        this.currentPalette.replace(palettedata);
        int dblen = databits.length * 64 / 4096;
        if (this.currentPalette == this.globalPalette) {
            DataPalette<T> hashPalette = new DataPaletteHash<T>(this.stolenRegistry, readBits, this.f, this.h, this.i);
            hashPalette.replace(palettedata);
            DataBits var5 = new DataBits(readBits, 4096, databits);

            for (int var6 = 0; var6 < 4096; ++var6) {
                this.dataBits.b(var6, this.globalPalette.getIndex(hashPalette.getByIndex(var5.getIndexFromPos(var6))));
            }
        } else if (dblen == this.bits) {
            System.arraycopy(databits, 0, this.dataBits.getData(), 0, databits.length);
        } else {
            DataBits var4 = new DataBits(dblen, 4096, databits);

            for (int var5 = 0; var5 < 4096; ++var5) {
                this.dataBits.b(var5, var4.getIndexFromPos(var5));
            }
        }
    }

    public void save(CompoundTag to, String paletteName, String blockStatesName) {
        DataPaletteHash<T> hashpal = new DataPaletteHash<T>(this.stolenRegistry, bits, this.f, this.h, this.i);
        T cursor = this.defAir;
        int palIndex = hashpal.getIndex(this.defAir);
        int[] var6 = new int[4096];

        for (int var7 = 0; var7 < 4096; ++var7) {
            T entry = this.getByIndex(var7);
            if (!entry.equals(cursor)) {
                cursor = entry;
                palIndex = hashpal.getIndex(entry);
            }

            var6[var7] = palIndex;
        }

        ListTag<CompoundTag> npalette = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        hashpal.writePalette(npalette);
        to.put(paletteName, npalette);
        int var8 = Math.max(4, MathHelper.e(npalette.size()));
        DataBits writeBits = new DataBits(var8, 4096);

        for (int var10 = 0; var10 < var6.length; ++var10) {
            writeBits.b(var10, var6[var10]);
        }

        to.putLongArray(blockStatesName, writeBits.getData());
    }

    public int c() {
        return 1 + this.currentPalette.a() + PacketDataSerializer.a(this.dataBits.b()) + this.dataBits.getData().length * 8;
    }

    public boolean contains(Predicate<T> var0) {
        return this.currentPalette.a(var0);
    }

    public void a(DataPaletteBlock.a<T> var0) {
        Int2IntMap var1 = new Int2IntOpenHashMap();
        this.dataBits.a((var1x) -> var1.put(var1x, var1.get(var1x) + 1));
        var1.int2IntEntrySet().forEach((var1x) -> var0.accept(this.currentPalette.getByIndex(var1x.getIntKey()), var1x.getIntValue()));
    }

    @FunctionalInterface
    public interface a<T> {
        void accept(T var1, int var2);
    }
}