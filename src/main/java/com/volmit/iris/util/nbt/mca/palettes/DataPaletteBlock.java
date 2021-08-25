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

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.math.MathHelper;
import com.volmit.iris.util.nbt.io.SNBTSerializer;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import com.volmit.iris.util.scheduling.J;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.world.level.chunk.ChunkSection;
import org.bukkit.Material;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Getter
public class DataPaletteBlock implements DataPaletteExpandable {
    private static final int d = 4096;
    public static final int HASH_BITS = 9;
    public static final int LINEAR_BITS = 4;
    private final DataPalette globalPalette;
    private final DataPaletteExpandable f = (var0x, var1x) -> 0;
    private final RegistryBlockID stolenRegistry;
    private final CompoundTag defAir;
    private static final AtomicCache<RegistryBlockID> reg = new AtomicCache<>();
    private static final AtomicCache<DataPaletteGlobal> global = new AtomicCache<>();
    private static final CompoundTag air = NBTWorld.getCompound(Material.AIR.createBlockData());
    protected DataBits dataBits;
    private DataPalette currentPalette;
    private int bits = 0;

    public DataPaletteBlock() {
        this(global(), registry(), air);
    }
    
    public DataPaletteBlock(DataPalette var0,
                            RegistryBlockID var1,
                            CompoundTag airType) {
        this.globalPalette = var0;
        this.stolenRegistry = var1;
        this.defAir = airType;
        this.changeBitsTo(4);
    }

    private static RegistryBlockID registry()
    {
        return ((DataPaletteGlobal) global()).getRegistry();
    }

    private static DataPalette global() {
        return global.aquire(() -> new DataPaletteGlobal(J.attemptResult(() -> INMS.get().computeBlockIDRegistry()), air));
    }


    private static int blockIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }

    private void changeBitsTo(int newbits) {
        if (newbits != bits) {
            bits = newbits;
            if (bits <= LINEAR_BITS) {
                bits = LINEAR_BITS;
                currentPalette = new DataPaletteLinear(bits, this);
            } else if (bits < HASH_BITS) {
                currentPalette = new DataPaletteHash(bits, this);
            } else {
                currentPalette = globalPalette;
                bits = MathHelper.e(stolenRegistry.size());
            }

            currentPalette.getIndex(defAir);
            dataBits = new DataBits(bits, 4096);
        }
    }

    public int onResize(int newBits, CompoundTag newData) {
        DataBits oldBits = dataBits;
        DataPalette oldPalette = currentPalette;
        changeBitsTo(newBits);

        for (int i = 0; i < oldBits.b(); ++i) {
            CompoundTag block = oldPalette.getByIndex(oldBits.getIndexFromPos(i));
            if (block != null) {
                setBlockIndex(i, block);
            }
        }

        return currentPalette.getIndex(newData);
    }

    @Deprecated
    public CompoundTag setBlockAndReturn(int x, int y, int z, CompoundTag block) {
        return setBlockIndexAndReturn(blockIndex(x, y, z), block);
    }

    @Deprecated
    private CompoundTag setBlockIndexAndReturn(int index, CompoundTag block) {
        int paletteIndex = currentPalette.getIndex(block);
        int res = dataBits.setBlockResulting(index, paletteIndex);
        CompoundTag testBlock = currentPalette.getByIndex(res);
        return testBlock == null ? defAir : testBlock;
    }

    public void setBlock(int x, int y, int z, CompoundTag block) {
        setBlockIndex(blockIndex(x, y, z), block);
    }

    private void setBlockIndex(int blockIndex, CompoundTag block) {
        int paletteIndex = currentPalette.getIndex(block);
        dataBits.setBlock(blockIndex, paletteIndex);
    }

    public CompoundTag getBlock(int x, int y, int z) {
        return getByIndex(blockIndex(x, y, z));
    }

    protected CompoundTag getByIndex(int index) {
        if(currentPalette == null)
        {
            return null;
        }

        CompoundTag data = currentPalette.getByIndex(dataBits.getIndexFromPos(index));
        return data == null ? defAir : data;
    }

    public void load(ListTag<CompoundTag> palettedata, long[] databits) {
        int readBits = Math.max(4, MathHelper.e(palettedata.size()));
        if (readBits != bits) {
            changeBitsTo(readBits);
        }

        currentPalette.replace(palettedata);
        int dblen = databits.length * 64 / 4096;
        if (currentPalette == globalPalette) {
            DataPalette hashPalette = new DataPaletteHash(readBits, f);
            hashPalette.replace(palettedata);
            DataBits var5 = new DataBits(readBits, 4096, databits);

            for (int i = 0; i < 4096; ++i) {
                dataBits.setBlock(i, globalPalette.getIndex(hashPalette.getByIndex(var5.getIndexFromPos(i))));
            }
        } else if (dblen == bits) {
            System.arraycopy(databits, 0, dataBits.getData(), 0, databits.length);
        } else {
            DataBits var4 = new DataBits(dblen, 4096, databits);

            for (int i = 0; i < 4096; ++i) {
                dataBits.setBlock(i, var4.getIndexFromPos(i));
            }
        }
    }

    public void save(CompoundTag to, String paletteName, String blockStatesName) {
        DataPaletteHash hashpal = new DataPaletteHash(bits, f);
        CompoundTag cursor = defAir;
        int palIndex = hashpal.getIndex(defAir);
        int[] paletteIndex = new int[4096];
        int i;
        for (i = 0; i < 4096; ++i) {
            CompoundTag entry = getByIndex(i);
            if (!entry.equals(cursor)) {
                cursor = entry;
                palIndex = hashpal.getIndex(entry);
            }

            paletteIndex[i] = palIndex;
        }

        ListTag<CompoundTag> npalette = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        hashpal.writePalette(npalette);
        to.put(paletteName, npalette);
        int bits = Math.max(4, MathHelper.e(npalette.size()));
        DataBits writeBits = new DataBits(bits, 4096);

        for (i = 0; i < paletteIndex.length; ++i) {
            writeBits.setBlock(i, paletteIndex[i]);
        }

        to.putLongArray(blockStatesName, writeBits.getData());
        to.putString("DEBUG_PALETTE_MODE", currentPalette.getClass().getSimpleName());
    }

    public boolean contains(Predicate<CompoundTag> var0) {
        return currentPalette.contains(var0);
    }

    public void a(PaletteConsumer<CompoundTag> var0) {
        Int2IntMap var1 = new Int2IntOpenHashMap();
        dataBits.a((var1x) -> var1.put(var1x, var1.get(var1x) + 1));
        var1.int2IntEntrySet().forEach((var1x) -> var0.accept(currentPalette.getByIndex(var1x.getIntKey()), var1x.getIntValue()));
    }

    @FunctionalInterface
    public interface PaletteConsumer<T> {
        void accept(T var1, int var2);
    }
}