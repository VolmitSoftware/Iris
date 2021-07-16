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

package com.volmit.iris.engine.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.data.mca.Chunk;
import com.volmit.iris.engine.data.mca.MCAFile;
import com.volmit.iris.engine.data.mca.MCAUtil;
import com.volmit.iris.engine.data.mca.Section;
import com.volmit.iris.engine.data.nbt.tag.CompoundTag;
import com.volmit.iris.engine.data.nbt.tag.StringTag;
import com.volmit.iris.engine.parallel.BurstExecutor;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.B;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@SuppressWarnings("EmptyMethod")
public class DirectWorldWriter {
    private final File worldFolder;
    private final Map<Long, MCAFile> writeBuffer;
    private static final Map<String, CompoundTag> blockDataCache = new KMap<>();
    private static final Map<Biome, Integer> biomeIds = computeBiomeIDs();

    public DirectWorldWriter(File worldFolder) {
        this.worldFolder = worldFolder;
        writeBuffer = new KMap<>();
        new File(worldFolder, "iris/mca-region").mkdirs();
    }

    public void flush() {
        BurstExecutor ex2 = MultiBurst.burst.burst(writeBuffer.size());

        for (Long i : new KList<>(writeBuffer.keySet())) {
            ex2.queue(() -> {
                int x = Cache.keyX(i);
                int z = Cache.keyZ(i);
                try {
                    File f = getMCAFile(x, z);

                    if (!f.exists()) {
                        f.getParentFile().mkdirs();
                        f.createNewFile();
                    }

                    MCAUtil.write(writeBuffer.get(i), f, true);
                    writeBuffer.remove(i);
                } catch (Throwable e) {Iris.reportError(e);
                    e.printStackTrace();
                }
            });
        }

        ex2.complete();
    }

    public void optimizeChunk(int x, int z) {
        getChunk(x, z).cleanupPalettesAndBlockStates();
    }

    public File getMCAFile(int x, int z) {
        return new File(worldFolder, "iris/mca-region/r." + x + "." + z + ".mca");
    }

    public static BlockData getBlockData(CompoundTag tag) {
        if (tag == null) {
            return B.getAir();
        }

        StringBuilder p = new StringBuilder(tag.getString("Name"));

        if (tag.containsKey("Properties")) {
            CompoundTag props = tag.getCompoundTag("Properties");
            p.append('[');

            for (String i : props.keySet()) {
                p.append(i).append('=').append(props.getString(i)).append(',');
            }

            p.deleteCharAt(p.length() - 1).append(']');
        }

        BlockData b = B.getOrNull(p.toString());

        if (b == null) {
            return B.getAir();
        }

        return b;
    }

    public static CompoundTag getCompound(BlockData blockData) {
        String data = blockData.getAsString(true);

        if (blockDataCache.containsKey(data)) {
            return blockDataCache.get(data).clone();
        }

        CompoundTag s = new CompoundTag();
        NamespacedKey key = blockData.getMaterial().getKey();
        s.putString("Name", key.getNamespace() + ":" + key.getKey());


        if (data.contains("[")) {
            String raw = data.split("\\Q[\\E")[1].replaceAll("\\Q]\\E", "");
            CompoundTag props = new CompoundTag();
            if (raw.contains(",")) {
                for (String i : raw.split("\\Q,\\E")) {
                    String[] m = i.split("\\Q=\\E");
                    String k = m[0];
                    String v = m[1];
                    props.put(k, new StringTag(v));
                }
            } else {
                String[] m = raw.split("\\Q=\\E");
                String k = m[0];
                String v = m[1];
                props.put(k, new StringTag(v));
            }
            s.put("Properties", props);
        }

        blockDataCache.put(data, s.clone());
        return s;
    }

    public BlockData getBlockData(int x, int y, int z) {
        try {
            CompoundTag tag = getChunkSection(x >> 4, y >> 4, z >> 4).getBlockStateAt(x & 15, y & 15, z & 15);

            if (tag == null) {
                return B.get("AIR");
            }

            return getBlockData(tag);
        } catch (Throwable e) {Iris.reportError(e);

        }
        return B.get("AIR");
    }

    public void setBlockData(int x, int y, int z, BlockData data) {
        getChunkSection(x >> 4, y >> 4, z >> 4).setBlockStateAt(x & 15, y & 15, z & 15, getCompound(data), false);
    }

    public void setBiome(int x, int y, int z, Biome biome) {
        getChunk(x >> 4, z >> 4).setBiomeAt(x & 15, y, z & 15, biomeIds.get(biome));
    }

    public Section getChunkSection(int x, int y, int z) {
        Chunk c = getChunk(x, z);
        Section s = c.getSection(y);

        if (s == null) {
            s = Section.newSection();
            c.setSection(y, s);
        }

        return s;
    }

    public void deleteChunk(int x, int z) {

    }

    public Chunk getChunk(int x, int z) {
        MCAFile mca = getMCA(x >> 5, z >> 5);
        Chunk c = mca.getChunk(x & 31, z & 31);

        if (c == null) {
            c = Chunk.newChunk();
            mca.setChunk(x & 31, z & 31, c);
        }

        return c;
    }

    public MCAFile getMCA(int x, int z) {
        long key = Cache.key(x, z);
        MCAFile mca = writeBuffer.get(key);

        if (mca != null) {
            return mca;
        }

        File f = getMCAFile(x, z);
        try {
            mca = f.exists() ? MCAUtil.read(f) : new MCAFile(x, z);
        } catch (IOException e) {
            e.printStackTrace();
            mca = new MCAFile(x, z);
        }

        writeBuffer.put(key, mca);
        return mca;
    }

    public int size() {
        return writeBuffer.size();
    }

    private static Map<Biome, Integer> computeBiomeIDs() {
        Map<Biome, Integer> biomeIds = new KMap<>();

        for (Biome biome : Biome.values()) {
            if (!biome.name().equals("CUSTOM")) {
                biomeIds.put(biome, INMS.get().getBiomeId(biome));
            }
        }

        return biomeIds;
    }

    public void verify(int mcaox, int mcaoz) {
        MCAFile file = getMCA(mcaox, mcaoz);

        for(int i = 0; i < 32; i++)
        {
            for(int j = 0; j < 32; j++)
            {
                Chunk c = file.getChunk(i, j);

                if(c == null)
                {
                    Iris.warn("Chunk " + ((mcaox << 5) + i) + ", " + ((mcaoz << 5) + j) + " is null in MCA File " + mcaox + ", " + mcaoz);
                }
            }
        }
    }
}
