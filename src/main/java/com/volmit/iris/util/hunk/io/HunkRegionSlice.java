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

package com.volmit.iris.util.hunk.io;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.function.Function2;
import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.oldnbt.ByteArrayTag;
import com.volmit.iris.util.oldnbt.CompoundTag;
import com.volmit.iris.util.oldnbt.Tag;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class HunkRegionSlice<T> {
    public static final Function2<Integer, CompoundTag, HunkRegionSlice<BlockData>> BLOCKDATA = (h, c) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new BlockDataHunkIOAdapter(), c, "blockdata");
    public static final Function2<Integer, CompoundTag, HunkRegionSlice<TileData<? extends TileState>>> TILE = (h, c) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new TileDataHunkIOAdapter(), c, "tile");
    public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<String>> STRING = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new StringHunkIOAdapter(), c, t);
    public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<Boolean>> BOOLEAN = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new BooleanHunkIOAdapter(), c, t);
    private final Function3<Integer, Integer, Integer, Hunk<T>> factory;
    private final HunkIOAdapter<T> adapter;
    private final CompoundTag compound;
    private final String key;
    private final KMap<Position2, Hunk<T>> loadedChunks;
    private final KMap<Position2, Long> lastUse;
    private final KSet<Position2> save;
    private final int height;

    public HunkRegionSlice(int height, Function3<Integer, Integer, Integer, Hunk<T>> factory, HunkIOAdapter<T> adapter, CompoundTag compound, String key) {
        this.height = height;
        this.loadedChunks = new KMap<>();
        this.factory = factory;
        this.adapter = adapter;
        this.compound = compound;
        this.save = new KSet<>();
        this.key = key;
        this.lastUse = new KMap<>();
    }

    public synchronized int cleanup(long t) {
        int v = 0;
        if (loadedChunks.size() != lastUse.size()) {
            Iris.warn("Incorrect chunk use counts in " + key + " region slice.");

            for (Position2 i : lastUse.k()) {
                if (!loadedChunks.containsKey(i)) {
                    Iris.warn("  Missing LoadChunkKey " + i);
                }
            }
        }

        for (Position2 i : lastUse.k()) {
            Long l = lastUse.get(i);
            if (l == null || M.ms() - l > t) {
                v++;
                MultiBurst.burst.lazy(() -> {
                    unload(i.getX(), i.getZ());
                });
            }
        }

        return v;
    }

    public synchronized void clear() {
        for (String i : new KList<>(compound.getValue().keySet())) {
            if (i.startsWith(key + ".")) {
                compound.getValue().remove(i);
            }
        }
    }

    public synchronized void save(MultiBurst burst) {

        try {
            for (Position2 i : save.copy()) {
                if (i == null) {
                    continue;
                }

                save(i.getX(), i.getZ());

                try {
                    save.remove(i);
                } catch (Throwable eer) {
                    Iris.reportError(eer);
                }
            }
        } catch (Throwable ee) {
            Iris.reportError(ee);
        }
    }

    public boolean contains(int x, int z) {
        return compound.getValue().containsKey(key(x, z));
    }

    public void delete(int x, int z) {
        compound.getValue().remove(key(x, z));
    }

    public Hunk<T> read(int x, int z) throws IOException {
        AtomicReference<IOException> e = new AtomicReference<>();
        Hunk<T> xt = null;

        Tag t = compound.getValue().get(key(x, z));

        if ((t instanceof ByteArrayTag)) {
            try {
                xt = adapter.read(factory, (ByteArrayTag) t);
            } catch (IOException xe) {
                Iris.reportError(xe);
                e.set(xe);
            }
        }

        if (xt != null) {
            return xt;
        }

        if (e.get() != null) {
            throw e.get();
        }

        return null;
    }

    public void write(Hunk<T> hunk, int x, int z) throws IOException {
        compound.getValue().put(key(x, z), hunk.writeByteArrayTag(adapter, key(x, z)));
    }

    public synchronized int unloadAll() {
        int v = 0;
        for (Position2 i : loadedChunks.k()) {
            unload(i.getX(), i.getZ());
            v++;
        }

        save.clear();
        loadedChunks.clear();
        lastUse.clear();
        return v;
    }

    public void save(Hunk<T> region, int x, int z) {
        try {
            write(region, x, z);
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    public boolean isLoaded(int x, int z) {
        return loadedChunks.containsKey(new Position2(x, z));
    }

    public void save(int x, int z) {
        if (isLoaded(x, z)) {
            save(get(x, z), x, z);
        }
    }

    public void unload(int x, int z) {
        Position2 key = new Position2(x, z);
        if (isLoaded(x, z)) {
            if (save.contains(key)) {
                save(x, z);
                save.remove(key);
            }

            lastUse.remove(key);
            loadedChunks.remove(key);
        }
    }

    public Hunk<T> load(int x, int z) {
        if (isLoaded(x, z)) {
            return loadedChunks.get(new Position2(x, z));
        }

        Hunk<T> v = null;

        if (contains(x, z)) {
            try {
                v = read(x, z);
            } catch (IOException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        }

        if (v == null) {
            v = factory.apply(16, height, 16);
        }

        loadedChunks.put(new Position2(x, z), v);

        return v;
    }

    public Hunk<T> get(int x, int z) {
        Position2 key = new Position2(x, z);

        Hunk<T> c = loadedChunks.get(key);

        if (c == null) {
            c = load(x, z);
        }

        lastUse.put(new Position2(x, z), M.ms());

        return c;
    }

    public Hunk<T> getR(int x, int z) {
        return get(x, z).readOnly();
    }

    public Hunk<T> getRW(int x, int z) {
        save.add(new Position2(x, z));
        return get(x, z);
    }

    private String key(int x, int z) {
        if (x < 0 || x >= 32 || z < 0 || z >= 32) {
            throw new IndexOutOfBoundsException("The chunk " + x + " " + z + " is out of bounds max is 31x31");
        }

        return key + "." + x + "." + z;
    }

    public int getLoadCount() {
        return loadedChunks.size();
    }
}
