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

package com.volmit.iris.util.nbt.mca;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.StringTag;
import com.volmit.iris.util.parallel.HyperLock;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class NBTWorld {
    private static final BlockData AIR = B.get("AIR");
    private static final Map<BlockData, CompoundTag> blockDataCache = new KMap<>();
    private static final Function<BlockData, CompoundTag> BLOCK_DATA_COMPUTE = (blockData) -> {
        CompoundTag s = new CompoundTag();
        String data = blockData.getAsString(true);
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

        return s;
    };
    private static final Map<Biome, Integer> biomeIds = computeBiomeIDs();
    private final KMap<Long, MCAFile> loadedRegions;
    private final HyperLock hyperLock = new HyperLock();
    private final KMap<Long, Long> lastUse;
    private final File worldFolder;
    private final ExecutorService saveQueue;

    public NBTWorld(File worldFolder) {
        this.worldFolder = worldFolder;
        this.loadedRegions = new KMap<>();
        this.lastUse = new KMap<>();
        saveQueue = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("Iris MCA Writer");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
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

    public static CompoundTag getCompound(BlockData bd) {
        return blockDataCache.computeIfAbsent(bd, BLOCK_DATA_COMPUTE);
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

    public void close() {

        for (Long i : loadedRegions.k()) {
            queueSaveUnload(Cache.keyX(i), Cache.keyZ(i));
        }

        saveQueue.shutdown();
        try {
            while (!saveQueue.awaitTermination(3, TimeUnit.SECONDS)) {
                Iris.info("Still Waiting to save MCA Files...");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void flushNow() {
        for (Long i : loadedRegions.k()) {
            doSaveUnload(Cache.keyX(i), Cache.keyZ(i));
        }
    }

    public void queueSaveUnload(int x, int z) {
        saveQueue.submit(() -> doSaveUnload(x, z));
    }

    public void doSaveUnload(int x, int z) {
        MCAFile f = getMCAOrNull(x, z);
        if (f != null) {
            unloadRegion(x, z);
        }

        saveRegion(x, z, f);
    }

    public void save() {
        boolean saving = true;

        for (Long i : loadedRegions.k()) {
            int x = Cache.keyX(i);
            int z = Cache.keyZ(i);

            if (!lastUse.containsKey(i)) {
                lastUse.put(i, M.ms());
            }

            if (shouldUnload(x, z)) {
                queueSaveUnload(x, z);
            }
        }

        Iris.debug("Regions: " + C.GOLD + loadedRegions.size() + C.LIGHT_PURPLE);
    }

    public void queueSave() {

    }

    public synchronized void unloadRegion(int x, int z) {
        long key = Cache.key(x, z);
        loadedRegions.remove(key);
        lastUse.remove(key);
        Iris.debug("Unloaded Region " + C.GOLD + x + " " + z);
    }

    public void saveRegion(int x, int z) {
        long k = Cache.key(x, z);
        MCAFile mca = getMCAOrNull(x, z);
        try {
            MCAUtil.write(mca, getRegionFile(x, z), true);
            Iris.debug("Saved Region " + C.GOLD + x + " " + z);
        } catch (IOException e) {
            Iris.error("Failed to save region " + getRegionFile(x, z).getPath());
            e.printStackTrace();
        }
    }

    public void saveRegion(int x, int z, MCAFile mca) {
        try {
            MCAUtil.write(mca, getRegionFile(x, z), true);
            Iris.debug("Saved Region " + C.GOLD + x + " " + z);
        } catch (IOException e) {
            Iris.error("Failed to save region " + getRegionFile(x, z).getPath());
            e.printStackTrace();
        }
    }

    public boolean shouldUnload(int x, int z) {
        return getIdleDuration(x, z) > 60000;
    }

    public File getRegionFile(int x, int z) {
        return new File(worldFolder, "region/r." + x + "." + z + ".mca");
    }

    public BlockData getBlockData(int x, int y, int z) {
        try {
            CompoundTag tag = getChunkSection(x >> 4, y >> 4, z >> 4).getBlockStateAt(x & 15, y & 15, z & 15);

            if (tag == null) {
                return AIR;
            }

            return getBlockData(tag);
        } catch (Throwable e) {
            Iris.reportError(e);

        }
        return AIR;
    }

    public void setBlockData(int x, int y, int z, BlockData data) {
        getChunkSection(x >> 4, y >> 4, z >> 4).setBlockStateAt(x & 15, y & 15, z & 15, getCompound(data), false);
    }

    public int getBiomeId(Biome b) {
        return biomeIds.get(b);
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

    public Chunk getChunk(int x, int z) {
        return getChunk(getMCA(x >> 5, z >> 5), x, z);
    }

    public Chunk getChunk(MCAFile mca, int x, int z) {
        Chunk c = mca.getChunk(x & 31, z & 31);

        if (c == null) {
            c = Chunk.newChunk();
            mca.setChunk(x & 31, z & 31, c);
        }

        return c;
    }

    public Chunk getNewChunk(MCAFile mca, int x, int z) {
        Chunk c = Chunk.newChunk();
        mca.setChunk(x & 31, z & 31, c);

        return c;
    }

    public long getIdleDuration(int x, int z) {
        return hyperLock.withResult(x, z, () -> {
            Long l = lastUse.get(Cache.key(x, z));
            return l == null ? 0 : (M.ms() - l);
        });
    }

    public MCAFile getMCA(int x, int z) {
        long key = Cache.key(x, z);

        return hyperLock.withResult(x, z, () -> {
            lastUse.put(key, M.ms());

            MCAFile mcaf = loadedRegions.get(key);

            if (mcaf == null) {
                mcaf = new MCAFile(x, z);
                loadedRegions.put(key, mcaf);
            }

            return mcaf;
        });
    }

    public MCAFile getMCAOrNull(int x, int z) {
        long key = Cache.key(x, z);

        return hyperLock.withResult(x, z, () -> {
            if (loadedRegions.containsKey(key)) {
                lastUse.put(key, M.ms());
                return loadedRegions.get(key);
            }

            return null;
        });
    }

    public int size() {
        return loadedRegions.size();
    }

    public boolean isLoaded(int x, int z) {
        return loadedRegions.containsKey(Cache.key(x, z));
    }
}
