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

package com.volmit.iris.engine.parallax;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("ALL")
public class ParallaxWorld implements ParallaxAccess {
    private final KMap<Long, ParallaxRegion> loadedRegions;
    private final KList<Long> save;
    private final File folder;
    private final MultiBurst burst;
    private final int height;

    public ParallaxWorld(MultiBurst burst, int height, File folder) {
        this.height = height;
        this.burst = burst;
        this.folder = folder;
        save = new KList<>();
        loadedRegions = new KMap<>();
        folder.mkdirs();
    }

    public int getRegionCount() {
        return loadedRegions.size();
    }

    public int getChunkCount() {
        int m = 0;

        try {
            for (ParallaxRegion i : loadedRegions.values()) {
                m += i.getChunkCount();
            }
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return m;
    }

    public void close() {
        for (ParallaxRegion i : loadedRegions.v()) {
            unload(i.getX(), i.getZ());
        }

        save.clear();
        loadedRegions.clear();
    }

    public void save(ParallaxRegion region) {
        try {
            region.save();
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @RegionCoordinates
    public boolean isLoaded(int x, int z) {
        return loadedRegions.containsKey(key(x, z));
    }

    @RegionCoordinates
    public void save(int x, int z) {
        if (isLoaded(x, z)) {
            save(getR(x, z));
        }
    }

    @RegionCoordinates
    public int unload(int x, int z) {
        long key = key(x, z);
        int v = 0;
        if (isLoaded(x, z)) {
            if (save.contains(key)) {
                save(x, z);
                save.remove(key);
            }

            ParallaxRegion lr = loadedRegions.remove(key);

            if (lr != null) {
                v += lr.unload();
            }
        }

        return v;
    }

    @RegionCoordinates
    public ParallaxRegion load(int x, int z) {
        if (isLoaded(x, z)) {
            return loadedRegions.get(key(x, z));
        }

        ParallaxRegion v = new ParallaxRegion(burst, height, folder, x, z);
        loadedRegions.put(key(x, z), v);

        return v;
    }

    @RegionCoordinates
    public ParallaxRegion getR(int x, int z) {
        long key = key(x, z);

        ParallaxRegion region = loadedRegions.get(key);

        if (region == null) {
            region = load(x, z);
        }

        return region;
    }

    @RegionCoordinates
    public ParallaxRegion getRW(int x, int z) {
        save.addIfMissing(key(x, z));
        return getR(x, z);
    }

    @RegionCoordinates
    private long key(int x, int z) {
        return (((long) x) << 32) | (((long) z) & 0xffffffffL);
    }

    @ChunkCoordinates
    @Override
    public Hunk<BlockData> getBlocksR(int x, int z) {
        return getR(x >> 5, z >> 5).getBlockSlice().getR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<BlockData> getBlocksRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getBlockSlice().getRW(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<TileData<? extends TileState>> getTilesR(int x, int z) {
        return getR(x >> 5, z >> 5).getTileSlice().getR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<TileData<? extends TileState>> getTilesRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getTileSlice().getRW(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<String> getObjectsR(int x, int z) {
        return getR(x >> 5, z >> 5).getObjectSlice().getR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<String> getObjectsRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getObjectSlice().getRW(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<String> getEntitiesRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getEntitySlice().getRW(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<String> getEntitiesR(int x, int z) {
        return getRW(x >> 5, z >> 5).getEntitySlice().getR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<Boolean> getUpdatesR(int x, int z) {
        return getR(x >> 5, z >> 5).getUpdateSlice().getR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public Hunk<Boolean> getUpdatesRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getUpdateSlice().getRW(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public ParallaxChunkMeta getMetaR(int x, int z) {
        return getR(x >> 5, z >> 5).getMetaR(x & 31, z & 31);
    }

    @ChunkCoordinates
    @Override
    public ParallaxChunkMeta getMetaRW(int x, int z) {
        return getRW(x >> 5, z >> 5).getMetaRW(x & 31, z & 31);
    }

    public void cleanup() {
        cleanup(IrisSettings.get().getParallaxRegionEvictionMS(), IrisSettings.get().getParallax().getParallaxChunkEvictionMS());
    }

    @Override
    public synchronized void cleanup(long r, long c) {
        try {
            int rr = 0;
            for (ParallaxRegion i : loadedRegions.v()) {
                burst.lazy(() -> {
                    if (i.hasBeenIdleLongerThan(r)) {
                        unload(i.getX(), i.getZ());
                    } else {
                        i.cleanup(c);
                    }
                });
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Override
    public void saveAll() {
        burst.lazy(this::saveAllNOW);
    }

    @Override
    public void saveAllNOW() {
        Iris.debug("Saving " + C.GREEN + loadedRegions.size() + " Parallax Regions");
        for (ParallaxRegion i : loadedRegions.v()) {
            if (save.contains(key(i.getX(), i.getZ()))) {
                save(i.getX(), i.getZ());
            }
        }
    }
}
