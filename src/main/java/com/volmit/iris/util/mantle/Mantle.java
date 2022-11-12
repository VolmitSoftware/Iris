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

package com.volmit.iris.util.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.MatterSlice;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.HyperLock;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Getter;
import org.bukkit.Chunk;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mantle can store any type of data slice anywhere and manage regions & IO on it's own.
 * This class is fully thread safe read & writeNodeData
 */
public class Mantle {
    private final File dataFolder;
    private final int worldHeight;
    private final Map<Long, Long> lastUse;
    @Getter
    private final Map<Long, TectonicPlate> loadedRegions;
    private final HyperLock hyperLock;
    private final KSet<Long> unload;
    private final AtomicBoolean closed;
    private final MultiBurst ioBurst;
    private final AtomicBoolean io;

    /**
     * Create a new mantle
     *
     * @param dataFolder  the data folder
     * @param worldHeight the world's height (in blocks)
     */
    @BlockCoordinates
    public Mantle(File dataFolder, int worldHeight) {
        this.hyperLock = new HyperLock();
        this.closed = new AtomicBoolean(false);
        this.dataFolder = dataFolder;
        this.worldHeight = worldHeight;
        this.io = new AtomicBoolean(false);
        dataFolder.mkdirs();
        unload = new KSet<>();
        loadedRegions = new KMap<>();
        lastUse = new KMap<>();
        ioBurst = MultiBurst.burst;
        Iris.debug("Opened The Mantle " + C.DARK_AQUA + dataFolder.getAbsolutePath());
    }

    /**
     * Get the file for a region
     *
     * @param folder the folder
     * @param x      the x coord
     * @param z      the z coord
     * @return the file
     */
    public static File fileForRegion(File folder, int x, int z) {
        return fileForRegion(folder, key(x, z));
    }

    /**
     * Get the file for the given region
     *
     * @param folder the data folder
     * @param key    the region key
     * @return the file
     */
    public static File fileForRegion(File folder, Long key) {
        File f = new File(folder, "p." + key + ".ttp");
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        return f;
    }

    /**
     * Get the long value representing a chunk or region coordinate
     *
     * @param x the x
     * @param z the z
     * @return the value
     */
    public static Long key(int x, int z) {
        return Cache.key(x, z);
    }

    /**
     * Raise a flag if it is lowered currently, If the flag was raised, execute the runnable
     *
     * @param x    the chunk x
     * @param z    the chunk z
     * @param flag the flag to raise
     * @param r    the runnable to fire if the flag is now raised (and was previously lowered)
     */
    @ChunkCoordinates
    public void raiseFlag(int x, int z, MantleFlag flag, Runnable r) {
        if (!hasFlag(x, z, flag)) {
            flag(x, z, flag, true);
            r.run();
        }
    }

    /**
     * Obtain a cached writer which only contains cached chunks.
     * This avoids locking on regions when writing to lots of chunks
     *
     * @param x      the x chunk
     * @param z      the z chunk
     * @param radius the radius chunks
     * @return the writer
     */
    @ChunkCoordinates
    public MantleWriter write(EngineMantle engineMantle, int x, int z, int radius) {
        return new MantleWriter(engineMantle, this, x, z, radius);
    }

    /**
     * Lower a flag if it is raised. If the flag was lowered (meaning it was previously raised), execute the runnable
     *
     * @param x    the chunk x
     * @param z    the chunk z
     * @param flag the flag to lower
     * @param r    the runnable that is fired if the flag was raised but is now lowered
     */
    @ChunkCoordinates
    public void lowerFlag(int x, int z, MantleFlag flag, Runnable r) {
        if (hasFlag(x, z, flag)) {
            flag(x, z, flag, false);
            r.run();
        }
    }

    @ChunkCoordinates
    public MantleChunk getChunk(int x, int z) {
        return get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31);
    }

    /**
     * Flag or unflag a chunk
     *
     * @param x       the chunk x
     * @param z       the chunk z
     * @param flag    the flag
     * @param flagged should it be set to flagged or not
     */
    @ChunkCoordinates
    public void flag(int x, int z, MantleFlag flag, boolean flagged) {
        get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).flag(flag, flagged);
    }

    public void deleteChunk(int x, int z) {
        get(x >> 5, z >> 5).delete(x & 31, z & 31);
    }

    /**
     * Check very quickly if a tectonic plate exists via cached or the file system
     *
     * @param x the x region coordinate
     * @param z the z region coordinate
     * @return true if it exists
     */
    @RegionCoordinates
    public boolean hasTectonicPlate(int x, int z) {
        Long k = key(x, z);
        return loadedRegions.containsKey(k) || fileForRegion(dataFolder, k).exists();
    }

    /**
     * Iterate data in a chunk
     *
     * @param x        the chunk x
     * @param z        the chunk z
     * @param type     the type of data to iterate
     * @param iterator the iterator (x,y,z,data) -> do stuff
     * @param <T>      the type of data to iterate
     */
    @ChunkCoordinates
    public <T> void iterateChunk(int x, int z, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        /*if(!hasTectonicPlate(x >> 5, z >> 5)) {
            return;
        }*/

        get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).iterate(type, iterator);
    }

    /**
     * Does this chunk have a flag on it?
     *
     * @param x    the x
     * @param z    the z
     * @param flag the flag to test
     * @return true if it's flagged
     */
    @ChunkCoordinates
    public boolean hasFlag(int x, int z, MantleFlag flag) {
        if (!hasTectonicPlate(x >> 5, z >> 5)) {
            return false;
        }

        return get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).isFlagged(flag);
    }

    /**
     * Set data T at the given block position. This method will attempt to find a
     * Tectonic Plate either by loading it or creating a new one. This method uses
     * the hyper lock packaged with each Mantle. The hyperlock allows locking of multiple
     * threads at a single region while still allowing other threads to continue
     * reading & writing other regions. Hyperlocks are slow sync, but in multicore
     * environments, they drastically speed up loading & saving large counts of plates
     *
     * @param x   the block's x coordinate
     * @param y   the block's y coordinate
     * @param z   the block's z coordinate
     * @param t   the data to set at the block
     * @param <T> the type of data (generic method)
     */
    @BlockCoordinates
    public <T> void set(int x, int y, int z, T t) {
        if (closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if (y < 0 || y >= worldHeight) {
            return;
        }

        Matter matter = get((x >> 4) >> 5, (z >> 4) >> 5)
                .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
                .getOrCreate(y >> 4);
        matter.slice(matter.getClass(t))
                .set(x & 15, y & 15, z & 15, t);
    }

    @BlockCoordinates
    public <T> void remove(int x, int y, int z, Class<T> t) {
        if (closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if (y < 0 || y >= worldHeight) {
            return;
        }

        Matter matter = get((x >> 4) >> 5, (z >> 4) >> 5)
                .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
                .getOrCreate(y >> 4);
        matter.slice(t)
                .set(x & 15, y & 15, z & 15, null);
    }

    /**
     * Gets the data at the current block position This method will attempt to find a
     * Tectonic Plate either by loading it or creating a new one. This method uses
     * the hyper lock packaged with each Mantle. The hyperlock allows locking of multiple
     * threads at a single region while still allowing other threads to continue
     * reading & writing other regions. Hyperlocks are slow sync, but in multicore
     * environments, they drastically speed up loading & saving large counts of plates
     *
     * @param x   the block's x coordinate
     * @param y   the block's y coordinate
     * @param z   the block's z coordinate
     * @param t   the class representing the type of data being requested
     * @param <T> the type assumed from the provided class
     * @return the returned result (or null) if it doesnt exist
     */
    @SuppressWarnings("unchecked")
    @BlockCoordinates
    public <T> T get(int x, int y, int z, Class<T> t) {
        if (closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        if (!hasTectonicPlate((x >> 4) >> 5, (z >> 4) >> 5)) {
            return null;
        }

        if (y < 0 || y >= worldHeight) {
            return null;
        }

        return (T) get((x >> 4) >> 5, (z >> 4) >> 5)
                .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
                .getOrCreate(y >> 4).slice(t)
                .get(x & 15, y & 15, z & 15);
    }

    /**
     * Is this mantle closed
     *
     * @return true if it is
     */
    public boolean isClosed() {
        return closed.get();
    }

    public void set(int x, int y, int z, Matter matter) {
        for (MatterSlice<?> i : matter.getSliceMap().values()) {
            i.iterate((mx, my, mz, v) -> set(mx + x, my + y, mz + z, v));
        }
    }

    /**
     * Closes the Mantle. By closing the mantle, you can no longer read or writeNodeData
     * any data to the mantle or it's Tectonic Plates. Closing will also flush any
     * loaded regions to the disk in parallel.
     */
    public synchronized void close() {
        Iris.debug("Closing The Mantle " + C.DARK_AQUA + dataFolder.getAbsolutePath());
        if (closed.get()) {
            return;
        }

        closed.set(true);
        BurstExecutor b = ioBurst.burst(loadedRegions.size());
        for (Long i : loadedRegions.keySet()) {
            b.queue(() -> {
                try {
                    loadedRegions.get(i).write(fileForRegion(dataFolder, i));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        try {
            b.complete();
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        loadedRegions.clear();
        Iris.debug("The Mantle has Closed " + C.DARK_AQUA + dataFolder.getAbsolutePath());
    }

    /**
     * Save & unload regions that have not been used for more than the
     * specified amount of milliseconds
     *
     * @param idleDuration the duration
     */
    public synchronized void trim(long idleDuration) {
        if (closed.get()) {
            throw new RuntimeException("The Mantle is closed");
        }

        io.set(true);
        Iris.debug("Trimming Tectonic Plates older than " + Form.duration((double) idleDuration, 0));
        unload.clear();

        for (Long i : lastUse.keySet()) {
            hyperLock.withLong(i, () -> {
                if (M.ms() - lastUse.get(i) >= idleDuration) {
                    unload.add(i);
                }
            });
        }

        for (Long i : unload) {
            hyperLock.withLong(i, () -> {
                TectonicPlate m = loadedRegions.remove(i);
                lastUse.remove(i);

                try {
                    m.write(fileForRegion(dataFolder, i));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Iris.debug("Unloaded Tectonic Plate " + C.DARK_GREEN + Cache.keyX(i) + " " + Cache.keyZ(i));
            });
        }
        io.set(false);
    }

    /**
     * This retreives a future of the Tectonic Plate at the given coordinates.
     * All methods accessing tectonic plates should go through this method
     *
     * @param x the region x
     * @param z the region z
     * @return the future of a tectonic plate.
     */
    @RegionCoordinates
    private TectonicPlate get(int x, int z) {
        if (io.get()) {
            try {
                return getSafe(x, z).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        TectonicPlate p = loadedRegions.get(key(x, z));

        if (p != null) {
            return p;
        }

        try {
            return getSafe(x, z).get();
        } catch (InterruptedException e) {
            Iris.warn("Failed to get Tectonic Plate " + x + " " + z + " Due to a thread intterruption (hotload?)");
            Iris.reportError(e);
        } catch (ExecutionException e) {
            Iris.warn("Failed to get Tectonic Plate " + x + " " + z + " Due to a thread execution exception (engine close?)");
            Iris.reportError(e);
        }

        Iris.warn("Retrying to get " + x + " " + z + " Mantle Region");
        return get(x, z);
    }

    /**
     * This retreives a future of the Tectonic Plate at the given coordinates.
     * All methods accessing tectonic plates should go through this method
     *
     * @param x the region x
     * @param z the region z
     * @return the future of a tectonic plate.
     */
    @RegionCoordinates
    private Future<TectonicPlate> getSafe(int x, int z) {
        Long k = key(x, z);
        TectonicPlate p = loadedRegions.get(k);

        if (p != null) {
            lastUse.put(k, M.ms());
            return CompletableFuture.completedFuture(p);
        }

        return ioBurst.completeValue(() -> hyperLock.withResult(x, z, () -> {
            lastUse.put(k, M.ms());
            TectonicPlate region = loadedRegions.get(k);

            if (region != null) {
                return region;
            }

            File file = fileForRegion(dataFolder, x, z);

            if (file.exists()) {
                try {
                    Iris.addPanic("reading.tectonic-plate", file.getAbsolutePath());
                    region = TectonicPlate.read(worldHeight, file);

                    if (region.getX() != x || region.getZ() != z) {
                        Iris.warn("Loaded Tectonic Plate " + x + "," + z + " but read it as " + region.getX() + "," + region.getZ() + "... Assuming " + x + "," + z);
                    }

                    loadedRegions.put(k, region);
                    Iris.debug("Loaded Tectonic Plate " + C.DARK_GREEN + x + " " + z + C.DARK_AQUA + " " + file.getName());
                } catch (Throwable e) {
                    Iris.error("Failed to read Tectonic Plate " + file.getAbsolutePath() + " creating a new chunk instead.");
                    Iris.reportError(e);
                    if (!(e instanceof EOFException)) {
                        e.printStackTrace();
                    }
                    Iris.panic();
                    region = new TectonicPlate(worldHeight, x, z);
                    loadedRegions.put(k, region);
                    Iris.debug("Created new Tectonic Plate (Due to Load Failure) " + C.DARK_GREEN + x + " " + z);
                }

                return region;
            }

            region = new TectonicPlate(worldHeight, x, z);
            loadedRegions.put(k, region);
            Iris.debug("Created new Tectonic Plate " + C.DARK_GREEN + x + " " + z);
            return region;
        }));
    }

    public void saveAll() {

    }

    public int getWorldHeight() {
        return worldHeight;
    }

    public MantleChunk getChunk(Chunk e) {
        return getChunk(e.getX(), e.getZ());
    }

    public void deleteChunkSlice(int x, int z, Class<?> c) {
        if (!IrisToolbelt.toolbeltConfiguration.isEmpty() && IrisToolbelt.toolbeltConfiguration.getOrDefault("retain.mantle." + c.getCanonicalName(), false)) {
            return;
        }

        getChunk(x, z).deleteSlices(c);
    }

    public int getLoadedRegionCount() {
        return loadedRegions.size();
    }

    public <T> void set(int x, int y, int z, MatterSlice<T> slice) {
        if (slice.isEmpty()) {
            return;
        }

        slice.iterateSync((xx, yy, zz, t) -> set(x + xx, y + yy, z + zz, t));
    }

    public boolean isLoaded(Chunk c) {
        return loadedRegions.containsKey(key(c.getX() >> 5, c.getZ() >> 5));
    }

    public boolean shouldReduce(Engine engine) {
        return !engine.isStudio() || IrisSettings.get().getPerformance().isTrimMantleInStudio();
    }
}
