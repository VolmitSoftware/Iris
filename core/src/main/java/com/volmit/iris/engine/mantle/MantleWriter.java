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

package com.volmit.iris.engine.mantle;

import com.google.common.collect.ImmutableList;
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IObjectPlacer;
import com.volmit.iris.engine.object.IrisGeneratorStyle;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.engine.object.TileData;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.Matter;
import lombok.Data;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class MantleWriter implements IObjectPlacer {
    private final EngineMantle engineMantle;
    private final Mantle mantle;
    private final KMap<Long, MantleChunk> cachedChunks;
    private final int radius;
    private final int x;
    private final int z;

    public MantleWriter(EngineMantle engineMantle, Mantle mantle, int x, int z, int radius) {
        this.engineMantle = engineMantle;
        this.mantle = mantle;
        this.cachedChunks = new KMap<>();
        this.radius = radius;
        this.x = x;
        this.z = z;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                cachedChunks.put(Cache.key(i + x, j + z), mantle.getChunk(i + x, j + z));
            }
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

    public <T> void setDataWarped(int x, int y, int z, T t, RNG rng, IrisData data, IrisGeneratorStyle style) {
        setData((int) Math.round(style.warp(rng, data, x, x, y, -z)),
                (int) Math.round(style.warp(rng, data, y, z, -x, y)),
                (int) Math.round(style.warp(rng, data, z, -y, z, x)), t);
    }

    public <T> void setData(int x, int y, int z, T t) {
        if (t == null) {
            return;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }

        if (cx >= this.x - radius && cx <= this.x + radius
                && cz >= this.z - radius && cz <= this.z + radius) {
            MantleChunk chunk = cachedChunks.get(Cache.key(cx, cz));

            if (chunk == null) {
                Iris.error("Mantle Writer Accessed " + cx + "," + cz + " and came up null (and yet within bounds!)");
                return;
            }

            Matter matter = chunk.getOrCreate(y >> 4);
            matter.slice(matter.getClass(t)).set(x & 15, y & 15, z & 15, t);
        }
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return engineMantle.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return engineMantle.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        setData(x, y, z, d);
    }

    @Override
    public BlockData get(int x, int y, int z) {
        return getEngineMantle().get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return getEngineMantle().isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return getEngineMantle().isCarved(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return getEngineMantle().isSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return getEngineMantle().isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return getEngineMantle().getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return getEngineMantle().isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile) {
        getEngineMantle().setTile(xx, yy, zz, tile);
    }

    @Override
    public Engine getEngine() {
        return getEngineMantle().getEngine();
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

    public <T> void setElipsoid(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, T data) {
        setElipsoidFunction(cx, cy, cz, rx, ry, rz, fill, (a, b, c) -> data);
    }

    public <T> void setElipsoidWarped(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, T data, RNG rng, IrisData idata, IrisGeneratorStyle style) {
        setElipsoidFunctionWarped(cx, cy, cz, rx, ry, rz, fill, (a, b, c) -> data, rng, idata, style);
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
    public <T> void setElipsoidFunction(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, Function3<Integer, Integer, Integer, T> data) {
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

                    setData(x + cx, y + cy, z + cz, data.apply(x + cx, y + cy, z + cz));
                    setData(-x + cx, y + cy, z + cz, data.apply(-x + cx, y + cy, z + cz));
                    setData(x + cx, -y + cy, z + cz, data.apply(x + cx, -y + cy, z + cz));
                    setData(x + cx, y + cy, -z + cz, data.apply(x + cx, y + cy, -z + cz));
                    setData(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz));
                    setData(-x + cx, -y + cy, z + cz, data.apply(-x + cx, -y + cy, z + cz));
                    setData(x + cx, -y + cy, -z + cz, data.apply(x + cx, -y + cy, -z + cz));
                    setData(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz));
                    setData(-x + cx, -y + cy, -z + cz, data.apply(-x + cx, -y + cy, -z + cz));
                }
            }
        }
    }

    public <T> void setElipsoidFunctionWarped(int cx, int cy, int cz, double rx, double ry, double rz, boolean fill, Function3<Integer, Integer, Integer, T> data, RNG rng, IrisData idata, IrisGeneratorStyle style) {
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

                    setDataWarped(x + cx, y + cy, z + cz, data.apply(x + cx, y + cy, z + cz), rng, idata, style);
                    setDataWarped(-x + cx, y + cy, z + cz, data.apply(-x + cx, y + cy, z + cz), rng, idata, style);
                    setDataWarped(x + cx, -y + cy, z + cz, data.apply(x + cx, -y + cy, z + cz), rng, idata, style);
                    setDataWarped(x + cx, y + cy, -z + cz, data.apply(x + cx, y + cy, -z + cz), rng, idata, style);
                    setDataWarped(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz), rng, idata, style);
                    setDataWarped(-x + cx, -y + cy, z + cz, data.apply(-x + cx, -y + cy, z + cz), rng, idata, style);
                    setDataWarped(x + cx, -y + cy, -z + cz, data.apply(x + cx, -y + cy, -z + cz), rng, idata, style);
                    setDataWarped(-x + cx, y + cy, -z + cz, data.apply(-x + cx, y + cy, -z + cz), rng, idata, style);
                    setDataWarped(-x + cx, -y + cy, -z + cz, data.apply(-x + cx, -y + cy, -z + cz), rng, idata, style);
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
                    setData(i, j, k, data);
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
                        setData(x + cx, y + cy, z + cz, data);
                        setData(-x + cx, y + cy, z + cz, data);
                        setData(x + cx, y + cy, -z + cz, data);
                        setData(-x + cx, y + cy, -z + cz, data);
                    }
                }
            }
        }
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

    public <T> void setLine(List<IrisPosition> vectors, double radius, boolean filled, T data) {
        setLineConsumer(vectors, radius, filled, (_x, _y, _z) -> data);
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
    public <T> void setLineConsumer(List<IrisPosition> vectors, double radius, boolean filled, Function3<Integer, Integer, Integer, T> data) {
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

        setConsumer(vset, data);
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
        } else if (cy + height - 1 > getMantle().getWorldHeight()) {
            height = getMantle().getWorldHeight() - cy + 1;
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
                    setData(cx + x, cy + y, cz + z, data);
                    setData(cx + -x, cy + y, cz + z, data);
                    setData(cx + x, cy + y, cz + -z, data);
                    setData(cx + -x, cy + y, cz + -z, data);
                }
            }
        }
    }

    public <T> void set(IrisPosition pos, T data) {
        try {
            setData(pos.getX(), pos.getY(), pos.getZ(), data);
        } catch (Throwable e) {
            Iris.error("No set? " + data.toString() + " for " + pos.toString());
        }
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

    public <T> void setConsumer(Set<IrisPosition> positions, Function3<Integer, Integer, Integer, T> data) {
        for (IrisPosition i : positions) {
            set(i, data.apply(i.getX(), i.getY(), i.getZ()));
        }
    }

    public boolean isWithin(Vector pos) {
        return isWithin(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
    }

    public boolean isWithin(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return false;
        }

        return cx >= this.x - radius && cx <= this.x + radius
                && cz >= this.z - radius && cz <= this.z + radius;
    }
}
