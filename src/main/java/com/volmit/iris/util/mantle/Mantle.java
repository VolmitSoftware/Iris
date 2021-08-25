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

package com.volmit.iris.util.mantle;

import com.google.common.collect.ImmutableList;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.basic.IrisPosition;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.Consumer4;
import com.volmit.iris.util.math.INode;
import com.volmit.iris.util.math.KochanekBartelsInterpolation;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.PathInterpolation;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.HyperLock;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The mantle can store any type of data slice anywhere and manage regions & IO on it's own.
 * This class is fully thread safe read & write
 */
public class Mantle {
    private final File dataFolder;
    private final int worldHeight;
    private final Map<Long, Long> lastUse;
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
        if (!hasTectonicPlate(x >> 5, z >> 5)) {
            return;
        }

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

        if (t instanceof IrisFeaturePositional) {
            get((x >> 4) >> 5, (z >> 4) >> 5)
                    .getOrCreate((x >> 4) & 31, (z >> 4) & 31).addFeature((IrisFeaturePositional) t);
        } else {
            Matter matter = get((x >> 4) >> 5, (z >> 4) >> 5)
                    .getOrCreate((x >> 4) & 31, (z >> 4) & 31)
                    .getOrCreate(y >> 4);
            matter.slice(matter.getClass(t))
                    .set(x & 15, y & 15, z & 15, t);
        }
    }

    /**
     * Gets the data tat the current block position This method will attempt to find a
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

    /**
     * Closes the Mantle. By closing the mantle, you can no longer read or write
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
            Iris.error("Failed to get Tectonic Plate " + x + " " + z + " Due to a thread intterruption");
            Iris.reportError(e);
            e.printStackTrace();
        } catch (ExecutionException e) {
            Iris.error("Failed to get Tectonic Plate " + x + " " + z + " Due to a thread execution exception");
            Iris.reportError(e);
            e.printStackTrace();
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
                    region = TectonicPlate.read(worldHeight, file);
                    loadedRegions.put(k, region);
                    Iris.debug("Loaded Tectonic Plate " + C.DARK_GREEN + x + " " + z + C.DARK_AQUA + " " + file.getName());
                } catch (Throwable e) {
                    Iris.error("Failed to read Tectonic Plate " + file.getAbsolutePath() + " creating a new chunk instead.");
                    Iris.reportError(e);
                    e.printStackTrace();
                    region = new TectonicPlate(worldHeight);
                    loadedRegions.put(k, region);
                    Iris.debug("Created new Tectonic Plate (Due to Load Failure) " + C.DARK_GREEN + x + " " + z);
                }

                return region;
            }

            region = new TectonicPlate(worldHeight);
            loadedRegions.put(k, region);
            Iris.debug("Created new Tectonic Plate " + C.DARK_GREEN + x + " " + z);
            return region;
        }));
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

    public void saveAll() {

    }

    /**
     * Set a sphere into the mantle
     *
     * @param cx     the center x
     * @param cy     the center y
     * @param cz     the center z
     * @param radius the radius of this sphere
     * @param fill   should it be filled? or just the outer shell?
     * @param data   the data to set
     * @param <T>    the type of data to apply to the mantle
     */
    public <T> void setSphere(int cx, int cy, int cz, double radius, boolean fill, T data) {
        setElipsoid(cx, cy, cz, radius, radius, radius, fill, data);
    }

    /**
     * Set an elipsoid into the mantle
     *
     * @param cx   the center x
     * @param cy   the center y
     * @param cz   the center z
     * @param rx   the x radius
     * @param ry   the y radius
     * @param rz   the z radius
     * @param fill should it be filled or just the outer shell?
     * @param data the data to set
     * @param <T>  the type of data to apply to the mantle
     */
    public <T> void setElipsoid(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, T data) {
        rx += 0.5;
        ry += 0.5;
        rz += 0.5;
        final double invRadiusX = 1 / rx;
        final double invRadiusY = 1 / ry;
        final double invRadiusZ = 1 / rz;
        final int ceilRadiusX = (int) Math.ceil(rx);
        final int ceilRadiusY = (int) Math.ceil(ry);
        final int ceilRadiusZ = (int) Math.ceil(rz);
        double nextXn = 0;

        forX:
        for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextYn = 0;
            forY:
            for (int y = 0; y <= ceilRadiusY; ++y) {
                final double yn = nextYn;
                nextYn = (y + 1) * invRadiusY;
                double nextZn = 0;
                for (int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    nextZn = (z + 1) * invRadiusZ;

                    double distanceSq = lengthSq(xn, yn, zn);
                    if (distanceSq > 1) {
                        if (z == 0) {
                            if (y == 0) {
                                break forX;
                            }
                            break forY;
                        }
                        break;
                    }

                    if (!fill) {
                        if (lengthSq(nextXn, yn, zn) <= 1 && lengthSq(xn, nextYn, zn) <= 1 && lengthSq(xn, yn, nextZn) <= 1) {
                            continue;
                        }
                    }

                    set(x + cx, y + cy, z + cz, data);
                    set(-x + cx, y + cy, z + cz, data);
                    set(x + cx, -y + cy, z + cz, data);
                    set(x + cx, y + cy, -z + cz, data);
                    set(-x + cx, y + cy, -z + cz, data);
                    set(-x + cx, -y + cy, z + cz, data);
                    set(x + cx, -y + cy, -z + cz, data);
                    set(-x + cx, y + cy, -z + cz, data);
                    set(-x + cx, -y + cy, -z + cz, data);
                }
            }
        }
    }

    /**
     * Set a cuboid of data in the mantle
     *
     * @param x1   the min x
     * @param y1   the min y
     * @param z1   the min z
     * @param x2   the max x
     * @param y2   the max y
     * @param z2   the max z
     * @param data the data to set
     * @param <T>  the type of data to apply to the mantle
     */
    public <T> void setCuboid(int x1, int y1, int z1, int x2, int y2, int z2, T data) {
        int j, k;

        for (int i = x1; i <= x2; i++) {
            for (j = x1; j <= x2; j++) {
                for (k = x1; k <= x2; k++) {
                    set(i, j, k, data);
                }
            }
        }
    }

    /**
     * Set a pyramid of data in the mantle
     *
     * @param cx     the center x
     * @param cy     the base y
     * @param cz     the center z
     * @param data   the data to set
     * @param size   the size of the pyramid (width of base & height)
     * @param filled should it be filled or hollow
     * @param <T>    the type of data to apply to the mantle
     */
    @SuppressWarnings("ConstantConditions")
    public <T> void setPyramid(int cx, int cy, int cz, T data, int size, boolean filled) {
        int height = size;

        for (int y = 0; y <= height; ++y) {
            size--;
            for (int x = 0; x <= size; ++x) {
                for (int z = 0; z <= size; ++z) {
                    if ((filled && z <= size && x <= size) || z == size || x == size) {
                        set(x + cx, y + cy, z + cz, data);
                        set(-x + cx, y + cy, z + cz, data);
                        set(x + cx, y + cy, -z + cz, data);
                        set(-x + cx, y + cy, -z + cz, data);
                    }
                }
            }
        }
    }

    /**
     * Set a 3d tube spline interpolated with Kochanek Bartels
     *
     * @param nodevectors the vector points
     * @param radius      the radius
     * @param filled      if it should be filled or hollow
     * @param data        the data to set
     */
    public <T> void setSpline(List<Vector> nodevectors, double radius, boolean filled, T data) {
        setSpline(nodevectors, 0, 0, 0, 10, radius, filled, data);
    }

    /**
     * Set a 3d tube spline interpolated with Kochanek Bartels
     *
     * @param nodevectors the spline points
     * @param tension     the tension 0
     * @param bias        the bias 0
     * @param continuity  the continuity 0
     * @param quality     the quality 10
     * @param radius      the radius
     * @param filled      filled or hollow
     * @param data        the data to set
     * @param <T>         the type of data to apply to the mantle
     */
    public <T> void setSpline(List<Vector> nodevectors, double tension, double bias, double continuity, double quality, double radius, boolean filled, T data) {
        Set<IrisPosition> vset = new KSet<>();
        List<INode> nodes = new ArrayList<>(nodevectors.size());
        PathInterpolation interpol = new KochanekBartelsInterpolation();

        for (Vector nodevector : nodevectors) {
            INode n = new INode(nodevector);
            n.setTension(tension);
            n.setBias(bias);
            n.setContinuity(continuity);
            nodes.add(n);
        }

        interpol.setNodes(nodes);
        double splinelength = interpol.arcLength(0, 1);
        for (double loop = 0; loop <= 1; loop += 1D / splinelength / quality) {
            Vector tipv = interpol.getPosition(loop);
            vset.add(new IrisPosition(tipv.toBlockVector()));
        }

        vset = getBallooned(vset, radius);
        if (!filled) {
            vset = getHollowed(vset);
        }

        set(vset, data);
    }

    /**
     * Set a 3d line
     *
     * @param a      the first point
     * @param b      the second point
     * @param radius the radius
     * @param filled hollow or filled?
     * @param data   the data
     * @param <T>    the type of data to apply to the mantle
     */
    public <T> void setLine(IrisPosition a, IrisPosition b, double radius, boolean filled, T data) {
        setLine(ImmutableList.of(a, b), radius, filled, data);
    }

    /**
     * Set lines for points
     *
     * @param vectors the points
     * @param radius  the radius
     * @param filled  hollow or filled?
     * @param data    the data to set
     * @param <T>     the type of data to apply to the mantle
     */
    public <T> void setLine(List<IrisPosition> vectors, double radius, boolean filled, T data) {
        Set<IrisPosition> vset = new KSet<>();

        for (int i = 0; vectors.size() != 0 && i < vectors.size() - 1; i++) {
            IrisPosition pos1 = vectors.get(i);
            IrisPosition pos2 = vectors.get(i + 1);
            int x1 = pos1.getX();
            int y1 = pos1.getY();
            int z1 = pos1.getZ();
            int x2 = pos2.getX();
            int y2 = pos2.getY();
            int z2 = pos2.getZ();
            int tipx = x1;
            int tipy = y1;
            int tipz = z1;
            int dx = Math.abs(x2 - x1);
            int dy = Math.abs(y2 - y1);
            int dz = Math.abs(z2 - z1);

            if (dx + dy + dz == 0) {
                vset.add(new IrisPosition(tipx, tipy, tipz));
                continue;
            }

            int dMax = Math.max(Math.max(dx, dy), dz);
            if (dMax == dx) {
                for (int domstep = 0; domstep <= dx; domstep++) {
                    tipx = x1 + domstep * (x2 - x1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dx) * (y2 - y1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dx) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(new IrisPosition(tipx, tipy, tipz));
                }
            } else if (dMax == dy) {
                for (int domstep = 0; domstep <= dy; domstep++) {
                    tipy = y1 + domstep * (y2 - y1 > 0 ? 1 : -1);
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dy) * (x2 - x1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dy) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(new IrisPosition(tipx, tipy, tipz));
                }
            } else /* if (dMax == dz) */ {
                for (int domstep = 0; domstep <= dz; domstep++) {
                    tipz = z1 + domstep * (z2 - z1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dz) * (y2 - y1 > 0 ? 1 : -1));
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dz) * (x2 - x1 > 0 ? 1 : -1));

                    vset.add(new IrisPosition(tipx, tipy, tipz));
                }
            }
        }

        vset = getBallooned(vset, radius);

        if (!filled) {
            vset = getHollowed(vset);
        }

        set(vset, data);
    }

    /**
     * Set a cylinder in the mantle
     *
     * @param cx     the center x
     * @param cy     the base y
     * @param cz     the center z
     * @param data   the data to set
     * @param radius the radius
     * @param height the height of the cyl
     * @param filled filled or not
     */
    public <T> void setCylinder(int cx, int cy, int cz, T data, double radius, int height, boolean filled) {
        setCylinder(cx, cy, cz, data, radius, radius, height, filled);
    }

    /**
     * Set a cylinder in the mantle
     *
     * @param cx      the center x
     * @param cy      the base y
     * @param cz      the center z
     * @param data    the data to set
     * @param radiusX the x radius
     * @param radiusZ the z radius
     * @param height  the height of this cyl
     * @param filled  filled or hollow?
     */
    public <T> void setCylinder(int cx, int cy, int cz, T data, double radiusX, double radiusZ, int height, boolean filled) {
        int affected = 0;
        radiusX += 0.5;
        radiusZ += 0.5;

        if (height == 0) {
            return;
        } else if (height < 0) {
            height = -height;
            cy = cy - height;
        }

        if (cy < 0) {
            cy = 0;
        } else if (cy + height - 1 > worldHeight) {
            height = worldHeight - cy + 1;
        }

        final double invRadiusX = 1 / radiusX;
        final double invRadiusZ = 1 / radiusZ;
        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);
        double nextXn = 0;

        forX:
        for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextZn = 0;
            for (int z = 0; z <= ceilRadiusZ; ++z) {
                final double zn = nextZn;
                nextZn = (z + 1) * invRadiusZ;
                double distanceSq = lengthSq(xn, zn);

                if (distanceSq > 1) {
                    if (z == 0) {
                        break forX;
                    }

                    break;
                }

                if (!filled) {
                    if (lengthSq(nextXn, zn) <= 1 && lengthSq(xn, nextZn) <= 1) {
                        continue;
                    }
                }

                for (int y = 0; y < height; ++y) {
                    set(cx + x, cy + y, cz + z, data);
                    set(cx + -x, cy + y, cz + z, data);
                    set(cx + x, cy + y, cz + -z, data);
                    set(cx + -x, cy + y, cz + -z, data);
                }
            }
        }
    }

    public <T> void set(IrisPosition pos, T data) {
        set(pos.getX(), pos.getY(), pos.getZ(), data);
    }

    public <T> void set(List<IrisPosition> positions, T data) {
        for (IrisPosition i : positions) {
            set(i, data);
        }
    }

    public <T> void set(Set<IrisPosition> positions, T data) {
        for (IrisPosition i : positions) {
            set(i, data);
        }
    }

    private static Set<IrisPosition> getBallooned(Set<IrisPosition> vset, double radius) {
        Set<IrisPosition> returnset = new HashSet<>();
        int ceilrad = (int) Math.ceil(radius);

        for (IrisPosition v : vset) {
            int tipx = v.getX();
            int tipy = v.getY();
            int tipz = v.getZ();

            for (int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for (int loopy = tipy - ceilrad; loopy <= tipy + ceilrad; loopy++) {
                    for (int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                        if (hypot(loopx - tipx, loopy - tipy, loopz - tipz) <= radius) {
                            returnset.add(new IrisPosition(loopx, loopy, loopz));
                        }
                    }
                }
            }
        }
        return returnset;
    }

    private static Set<IrisPosition> getHollowed(Set<IrisPosition> vset) {
        Set<IrisPosition> returnset = new KSet<>();
        for (IrisPosition v : vset) {
            double x = v.getX();
            double y = v.getY();
            double z = v.getZ();
            if (!(vset.contains(new IrisPosition(x + 1, y, z))
                    && vset.contains(new IrisPosition(x - 1, y, z))
                    && vset.contains(new IrisPosition(x, y + 1, z))
                    && vset.contains(new IrisPosition(x, y - 1, z))
                    && vset.contains(new IrisPosition(x, y, z + 1))
                    && vset.contains(new IrisPosition(x, y, z - 1)))) {
                returnset.add(v);
            }
        }
        return returnset;
    }

    private static double hypot(double... pars) {
        double sum = 0;
        for (double d : pars) {
            sum += Math.pow(d, 2);
        }
        return Math.sqrt(sum);
    }

    private static double lengthSq(double x, double y, double z) {
        return (x * x) + (y * y) + (z * z);
    }

    private static double lengthSq(double x, double z) {
        return (x * x) + (z * z);
    }

    public int getWorldHeight() {
        return worldHeight;
    }
}
