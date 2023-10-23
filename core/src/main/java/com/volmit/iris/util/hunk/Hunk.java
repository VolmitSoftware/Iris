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

package com.volmit.iris.util.hunk;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.*;
import com.volmit.iris.util.hunk.storage.*;
import com.volmit.iris.util.hunk.view.*;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.interpolation.InterpolationMethod3D;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.math.BlockPosition;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.stream.interpolation.Interpolated;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("ALL")
public interface Hunk<T> {
    /**
     * Create a hunk view from a source hunk. This view reads and writes through to
     * the source hunk. Its is not a copy.
     *
     * @param <T> the type
     * @param src the source hunk
     * @return the hunk view
     */
    static <T> Hunk<T> view(Hunk<T> src) {
        return new HunkView<T>(src);
    }

    static <A, B> Hunk<B> convertedReadView(Hunk<A> src, Function<A, B> reader) {
        return new FunctionalHunkView<A, B>(src, reader, null);
    }

    static <A, B> Hunk<B> convertedWriteView(Hunk<A> src, Function<B, A> writer) {
        return new FunctionalHunkView<A, B>(src, null, writer);
    }

    static <A, B> Hunk<B> convertedReadWriteView(Hunk<A> src, Function<A, B> reader, Function<B, A> writer) {
        return new FunctionalHunkView<A, B>(src, reader, writer);
    }

    static Hunk<Biome> view(BiomeGrid biome, int minHeight, int maxHeight) {
        return new BiomeGridHunkView(biome, minHeight, maxHeight);
    }

    static <T> Hunk<T> fringe(Hunk<T> i, Hunk<T> o) {
        return new FringedHunkView<>(i, o);
    }

    static Hunk<BlockData> view(ChunkData src) {
        return new ChunkDataHunkView(src);
    }

    static Hunk<BlockData> viewBlocks(Chunk src) {
        return new ChunkHunkView(src);
    }

    static Hunk<Biome> viewBiomes(Chunk src) {
        return new ChunkBiomeHunkView(src);
    }

    static <T> Hunk<T> newHunk(int w, int h, int d) {
        return newArrayHunk(w, h, d);
    }

    @SafeVarargs
    static <T> Hunk<T> newCombinedHunk(Hunk<T>... hunks) {
        return newCombinedArrayHunk(hunks);
    }

    static <T> Hunk<T> newArrayHunk(int w, int h, int d) {
        return new ArrayHunk<>(w, h, d);
    }

    @SafeVarargs
    static <T> Hunk<T> newCombinedArrayHunk(Hunk<T>... hunks) {
        return combined(Hunk::newArrayHunk, hunks);
    }

    static <T> Hunk<T> newSynchronizedArrayHunk(int w, int h, int d) {
        return new SynchronizedArrayHunk<>(w, h, d);
    }

    @SafeVarargs
    static <T> Hunk<T> newCombinedSynchronizedArrayHunk(Hunk<T>... hunks) {
        return combined(Hunk::newSynchronizedArrayHunk, hunks);
    }

    static <T> Hunk<T> newMappedHunk(int w, int h, int d) {
        return new MappedHunk<>(w, h, d);
    }

    static <T> Hunk<T> newMappedHunkSynced(int w, int h, int d) {
        return new MappedHunk<T>(w, h, d).synchronize();
    }

    @SafeVarargs
    static <T> Hunk<T> newCombinedMappedHunk(Hunk<T>... hunks) {
        return combined(Hunk::newMappedHunk, hunks);
    }

    static <T> Hunk<T> newAtomicHunk(int w, int h, int d) {
        return new AtomicHunk<>(w, h, d);
    }

    @SafeVarargs
    static <T> Hunk<T> newCombinedAtomicHunk(Hunk<T>... hunks) {
        return combined(Hunk::newAtomicHunk, hunks);
    }

    static Hunk<Double> newAtomicDoubleHunk(int w, int h, int d) {
        return new AtomicDoubleHunk(w, h, d);
    }

    @SafeVarargs
    static Hunk<Double> newCombinedAtomicDoubleHunk(Hunk<Double>... hunks) {
        return combined(Hunk::newAtomicDoubleHunk, hunks);
    }

    static Hunk<Long> newAtomicLongHunk(int w, int h, int d) {
        return new AtomicLongHunk(w, h, d);
    }

    @SafeVarargs
    static Hunk<Long> newCombinedAtomicLongHunk(Hunk<Long>... hunks) {
        return combined(Hunk::newAtomicLongHunk, hunks);
    }

    static Hunk<Integer> newAtomicIntegerHunk(int w, int h, int d) {
        return new AtomicIntegerHunk(w, h, d);
    }

    @SafeVarargs
    static Hunk<Integer> newCombinedAtomicIntegerHunk(Hunk<Integer>... hunks) {
        return combined(Hunk::newAtomicIntegerHunk, hunks);
    }

    /**
     * Creates a new bounding hunk from the given hunks
     *
     * @param <T>     the type
     * @param factory the factory that creates a hunk
     * @param hunks   the hunks
     * @return the new bounding hunk
     */
    @SafeVarargs
    static <T> Hunk<T> combined(Function3<Integer, Integer, Integer, Hunk<T>> factory, Hunk<T>... hunks) {
        int w = 0;
        int h = 0;
        int d = 0;

        for (Hunk<T> i : hunks) {
            w = Math.max(w, i.getWidth());
            h = Math.max(h, i.getHeight());
            d = Math.max(d, i.getDepth());
        }

        Hunk<T> b = factory.apply(w, h, d);

        for (Hunk<T> i : hunks) {
            b.insert(i);
        }

        return b;
    }

    static <A, B> void computeDual2D(int parallelism, Hunk<A> a, Hunk<B> b, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> v) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight() || a.getDepth() != b.getDepth()) {
            throw new RuntimeException("Hunk sizes must match!");
        }

        if (a.get2DDimension(parallelism) == 1) {
            v.accept(0, 0, 0, a, b);
            return;
        }

        BurstExecutor e = MultiBurst.burst.burst(parallelism);
        KList<Runnable> rq = new KList<Runnable>(parallelism);
        getDualSections2D(parallelism, a, b, (xx, yy, zz, ha, hr, r) -> e.queue(() ->
        {
            v.accept(xx, yy, zz, ha, hr);

            synchronized (rq) {
                rq.add(r);
            }
        }), (x, y, z, hax, hbx) ->
        {
            a.insert(x, y, z, hax);
            b.insert(x, y, z, hbx);
        });
        e.complete();
        rq.forEach(Runnable::run);
        return;
    }

    static <A, B> void getDualSections2D(int sections, Hunk<A> a, Hunk<B> b, Consumer6<Integer, Integer, Integer, Hunk<A>, Hunk<B>, Runnable> v, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> inserterAB) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight() || a.getDepth() != b.getDepth()) {
            throw new RuntimeException("Hunk sizes must match!");
        }

        int dim = a.get2DDimension(sections);

        if (sections <= 1) {
            getDualSection(0, 0, 0, a.getWidth(), a.getHeight(), a.getDepth(), a, b, (ha, hr, r) -> v.accept(0, 0, 0, ha, hr, r), inserterAB);
            return;
        }

        int w = a.getWidth() / dim;
        int wr = a.getWidth() - (w * dim);
        int d = a.getDepth() / dim;
        int dr = a.getDepth() - (d * dim);
        int i, j;

        for (i = 0; i < a.getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < a.getDepth(); j += d) {
                int jj = j;
                getDualSection(i, 0, j, i + w + (i == 0 ? wr : 0), a.getHeight(), j + d + (j == 0 ? dr : 0), a, b, (ha, hr, r) -> v.accept(ii, 0, jj, ha, hr, r), inserterAB);
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }
    }

    static <A, B> void getDualSection(int x, int y, int z, int x1, int y1, int z1, Hunk<A> a, Hunk<B> b, Consumer3<Hunk<A>, Hunk<B>, Runnable> v, Consumer5<Integer, Integer, Integer, Hunk<A>, Hunk<B>> inserter) {
        Hunk<A> copya = a.crop(x, y, z, x1, y1, z1);
        Hunk<B> copyb = b.crop(x, y, z, x1, y1, z1);
        v.accept(copya, copyb, () -> inserter.accept(x, y, z, copya, copyb));
    }

    /**
     * Create a hunk that is optimized for specific uses
     *
     * @param w          width
     * @param h          height
     * @param d          depth
     * @param type       the class type
     * @param packed     if the hunk is generally more than 50% full (non-null nodes)
     * @param concurrent if this hunk must be thread safe
     * @param <T>        the type
     * @return the hunk
     */
    static <T> Hunk<T> newHunk(int w, int h, int d, Class<T> type, boolean packed, boolean concurrent) {
        if (type.equals(Double.class)) {
            return concurrent ?
                    packed ? (Hunk<T>) newAtomicDoubleHunk(w, h, d) : newMappedHunk(w, h, d)
                    : packed ? newArrayHunk(w, h, d) : newMappedHunkSynced(w, h, d);
        }

        if (type.equals(Integer.class)) {
            return concurrent ?
                    packed ? (Hunk<T>) newAtomicIntegerHunk(w, h, d) : newMappedHunk(w, h, d)
                    : packed ? newArrayHunk(w, h, d) : newMappedHunkSynced(w, h, d);
        }

        if (type.equals(Long.class)) {
            return concurrent ?
                    packed ? (Hunk<T>) newAtomicLongHunk(w, h, d) : newMappedHunk(w, h, d)
                    : packed ? newArrayHunk(w, h, d) : newMappedHunkSynced(w, h, d);
        }

        return concurrent ?
                packed ? newAtomicHunk(w, h, d) : newMappedHunk(w, h, d)
                : packed ? newArrayHunk(w, h, d) : newMappedHunkSynced(w, h, d);
    }

    static IrisPosition rotatedBounding(int w, int h, int d, double x, double y, double z) {
        int[] iii = {0, 0, 0};
        int[] aaa = {w, h, d};
        int[] aai = {w, h, 0};
        int[] iaa = {0, h, d};
        int[] aia = {w, 0, d};
        int[] iai = {0, h, 0};
        int[] iia = {0, 0, d};
        int[] aii = {w, 0, 0};
        rotate(x, y, z, iii);
        rotate(x, y, z, aaa);
        rotate(x, y, z, aai);
        rotate(x, y, z, iaa);
        rotate(x, y, z, aia);
        rotate(x, y, z, iai);
        rotate(x, y, z, iia);
        rotate(x, y, z, aii);
        int maxX = max(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int minX = min(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int maxY = max(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int minY = min(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int maxZ = max(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        int minZ = min(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        return new IrisPosition(maxX - minX, maxY - minY, maxZ - minZ);
    }

    static int max(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return Math.max(Math.max(Math.max(a5, a6), Math.max(a7, a8)), Math.max(Math.max(a1, a2), Math.max(a3, a4)));
    }

    static int min(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return Math.min(Math.min(Math.min(a5, a6), Math.min(a7, a8)), Math.min(Math.min(a1, a2), Math.min(a3, a4)));
    }

    static void rotate(double x, double y, double z, int[] c) {
        if (x % 360 != 0) {
            rotateAroundX(Math.toRadians(x), c);
        }

        if (y % 360 != 0) {
            rotateAroundY(Math.toRadians(y), c);
        }

        if (z % 360 != 0) {
            rotateAroundZ(Math.toRadians(z), c);
        }
    }

    static void rotateAroundX(double a, int[] c) {
        rotateAroundX(Math.cos(a), Math.sin(a), c);
    }

    static void rotateAroundX(double cos, double sin, int[] c) {
        int y = (int) Math.floor(cos * (double) (c[1] + 0.5) - sin * (double) (c[2] + 0.5));
        int z = (int) Math.floor(sin * (double) (c[1] + 0.5) + cos * (double) (c[2] + 0.5));
        c[1] = y;
        c[2] = z;
    }

    static void rotateAroundY(double a, int[] c) {
        rotateAroundY(Math.cos(a), Math.sin(a), c);
    }

    static void rotateAroundY(double cos, double sin, int[] c) {
        int x = (int) Math.floor(cos * (double) (c[0] + 0.5) + sin * (double) (c[2] + 0.5));
        int z = (int) Math.floor(-sin * (double) (c[0] + 0.5) + cos * (double) (c[2] + 0.5));
        c[0] = x;
        c[2] = z;
    }

    static void rotateAroundZ(double a, int[] c) {
        rotateAroundZ(Math.cos(a), Math.sin(a), c);
    }

    static void rotateAroundZ(double cos, double sin, int[] c) {
        int x = (int) Math.floor(cos * (double) (c[0] + 0.5) - sin * (double) (c[1] + 0.5));
        int y = (int) Math.floor(sin * (double) (c[0] + 0.5) + cos * (double) (c[1] + 0.5));
        c[0] = x;
        c[1] = y;
    }

    default boolean isMapped() {
        return false;
    }

    default int getEntryCount() {
        return getWidth() * getHeight() * getDepth();
    }

    default Hunk<T> listen(Consumer4<Integer, Integer, Integer, T> l) {
        return new ListeningHunk<>(this, l);
    }

    default Hunk<T> synchronize() {
        return new SynchronizedHunkView<>(this);
    }

    default Hunk<T> trackWrite(AtomicBoolean b) {
        return new WriteTrackHunk<T>(this, b);
    }

    default Hunk<T> readOnly() {
        return new ReadOnlyHunk<>(this);
    }

    default int getNonNullEntries() {
        AtomicInteger count = new AtomicInteger();
        iterate((x, y, z, v) -> count.getAndAdd(1));

        return count.get();
    }

    default boolean isAtomic() {
        return false;
    }

    default Hunk<T> invertY() {
        return new InvertedHunkView<T>(this);
    }

    default int getMaximumDimension() {
        return Math.max(getWidth(), Math.max(getHeight(), getDepth()));
    }

    default int getIdeal2DParallelism() {
        return getMax2DParallelism() / 4;
    }

    default int getIdeal3DParallelism() {
        return getMax3DParallelism() / 8;
    }

    default int getMinimumDimension() {
        return Math.min(getWidth(), Math.min(getHeight(), getDepth()));
    }

    default int getMax2DParallelism() {
        return (int) Math.pow(getMinimumDimension() / 2f, 2);
    }

    default int getMax3DParallelism() {
        return (int) Math.pow(getMinimumDimension() / 2f, 3);
    }

    default int filterDimension(int dim) {
        if (dim <= 1) {
            return 1;
        }

        dim = dim % 2 != 0 ? dim + 1 : dim;

        if (dim > getMinimumDimension() / 2) {
            if (dim <= 2) {
                return 1;
            }

            dim -= 2;
        }

        return dim;
    }

    default int get2DDimension(int sections) {
        if (sections <= 1) {
            return 1;
        }

        return filterDimension((int) Math.ceil(Math.sqrt(sections)));
    }

    default int get3DDimension(int sections) {
        if (sections <= 1) {
            return 1;
        }

        return filterDimension((int) Math.ceil(Math.cbrt(sections)));
    }

    /**
     * Iterate surfaces on 2d. Raytraces with a front and a back which stretches
     * through surfaces. Essentially what is returned is the following (in
     * order)<br>
     * <br>
     * <p>
     * The predicate is used to determine if the given block type is solid or not.
     *
     * <br>
     * <br>
     * ================================================ <br>
     * AX, AZ: Hunk Relative X and Z
     *
     * <br>
     * <br>
     * HX, HZ: Hunk Positional X and Z (in its parent hunk)
     *
     * <br>
     * <br>
     * TOP: The top of this surface (top+1 is air above a surface)
     *
     * <br>
     * <br>
     * BOTTOM: The bottom of this surface (bottom is the lowest SOLID surface before
     * either air or bedrock going down further)
     *
     * <br>
     * <br>
     * LAST_BOTTOM: The previous bottom. If your surface is the top surface, this
     * will be -1 as there is no bottom-of-surface above you. However if you are not
     * the top surface, this value is equal to the next solid layer above TOP, such
     * that ((LAST_BOTTOM - 1) - (TOP + 1)) is how many air blocks are between your
     * surface and the surface above you
     *
     * <br>
     * <br>
     * HUNK: The hunk to set data to. <br>
     * ================================================ <br>
     * <br>
     * If we assume your chunk coordinates are x and z, then <br>
     * <br>
     * bX = (x * 16)<br>
     * bZ = (z * 16)<br>
     * <br>
     * (ax, az, hx, hz, top, bottom, lastBottom, hunk) {<br>
     * actualBlockX = ax+hx;<br>
     * actualBlockZ = az+hz;<br>
     * <br>
     * hunkX = ax;<br>
     * hunkZ = az;<br>
     * <br>
     * hunk.set(hunkX, ?, hunkZ, noise(actualBlockX, ?, actualBlockZ));<br>
     * }<br>
     *
     * @param p the predicate
     * @param c the consumer
     * @return this
     */
    default Hunk<T> iterateSurfaces2D(Predicate<T> p, Consumer8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Hunk<T>> c) {
        return iterateSurfaces2D(getIdeal2DParallelism(), p, c);
    }

    /**
     * Iterate surfaces on 2d. Raytraces with a front and a back which stretches
     * through surfaces. Essentially what is returned is the following (in
     * order)<br>
     * <br>
     * <p>
     * The predicate is used to determine if the given block type is solid or not.
     *
     * <br>
     * <br>
     * ================================================ <br>
     * AX, AZ: Hunk Relative X and Z
     *
     * <br>
     * <br>
     * HX, HZ: Hunk Positional X and Z (in its parent hunk)
     *
     * <br>
     * <br>
     * TOP: The top of this surface (top+1 is air above a surface)
     *
     * <br>
     * <br>
     * BOTTOM: The bottom of this surface (bottom is the lowest SOLID surface before
     * either air or bedrock going down further)
     *
     * <br>
     * <br>
     * LAST_BOTTOM: The previous bottom. If your surface is the top surface, this
     * will be -1 as there is no bottom-of-surface above you. However if you are not
     * the top surface, this value is equal to the next solid layer above TOP, such
     * that ((LAST_BOTTOM - 1) - (TOP + 1)) is how many air blocks are between your
     * surface and the surface above you
     *
     * <br>
     * <br>
     * HUNK: The hunk to set data to. <br>
     * ================================================ <br>
     * <br>
     * If we assume your chunk coordinates are x and z, then <br>
     * <br>
     * bX = (x * 16)<br>
     * bZ = (z * 16)<br>
     * <br>
     * (ax, az, hx, hz, top, bottom, lastBottom, hunk) {<br>
     * actualBlockX = ax+hx;<br>
     * actualBlockZ = az+hz;<br>
     * <br>
     * hunkX = ax;<br>
     * hunkZ = az;<br>
     * <br>
     * hunk.set(hunkX, ?, hunkZ, noise(actualBlockX, ?, actualBlockZ));<br>
     * }<br>
     *
     * @param parallelism the ideal threads to use on this
     * @param p           the predicate
     * @param c           the consumer
     * @return this
     */
    default Hunk<T> iterateSurfaces2D(int parallelism, Predicate<T> p, Consumer8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Hunk<T>> c) {
        iterate2DTop(parallelism, (ax, az, hox, hoz, h) ->
        {
            int last = -1;
            int in = getHeight() - 1;
            boolean hitting = false;
            for (int i = getHeight() - 1; i >= 0; i--) {
                boolean solid = p.test(h.get(ax, i, az));

                if (!hitting && solid) {
                    in = i;
                    hitting = true;
                } else if (hitting && !solid) {
                    hitting = false;
                    c.accept(ax, az, hox, hoz, in, i - 1, last, h);
                    last = i - 1;
                }
            }

            if (hitting) {
                c.accept(ax, az, hox, hoz, in, 0, last, h);
            }
        });

        return this;
    }

    /**
     * Iterate on the xz top of this hunk. When using this consumer, given
     * <p>
     * consumer: (ax, az, hx, hz, hunk)
     * <p>
     * hunk.set(ax, ?, az, NOISE.get(ax+hx, az+hz));
     *
     * @param c the consumer hunkX, hunkZ, hunkOffsetX, hunkOffsetZ.
     * @return this
     */
    default Hunk<T> iterate2DTop(Consumer5<Integer, Integer, Integer, Integer, Hunk<T>> c) {
        return iterate2DTop(getIdeal2DParallelism(), c);
    }

    default Hunk<T> drift(int x, int y, int z) {
        return new DriftHunkView<>(this, x, y, z);
    }

    /**
     * Iterate on the xz top of this hunk. When using this consumer, given
     * <p>
     * consumer: (ax, az, hx, hz, hunk)
     * <p>
     * hunk.set(ax, ?, az, NOISE.get(ax+hx, az+hz));
     *
     * @param parallelism the target parallelism value or 0 to disable
     * @param c           the consumer hunkX, hunkZ, hunkOffsetX, hunkOffsetZ.
     * @return this
     */
    default Hunk<T> iterate2DTop(int parallelism, Consumer5<Integer, Integer, Integer, Integer, Hunk<T>> c) {
        compute2D(parallelism, (x, y, z, h) ->
        {
            for (int i = 0; i < h.getWidth(); i++) {
                for (int k = 0; k < h.getDepth(); k++) {
                    c.accept(i, k, x, z, h);
                }
            }
        });

        return this;
    }

    default Hunk<T> iterate(Predicate<T> p, Consumer3<Integer, Integer, Integer> c) {
        return iterate(getIdeal3DParallelism(), p, c);
    }

    default Hunk<T> iterate(int parallelism, Predicate<T> p, Consumer3<Integer, Integer, Integer> c) {
        iterate(parallelism, (x, y, z, t) ->
        {
            if (p.test(t)) {
                c.accept(x, y, z);
            }
        });

        return this;
    }

    default Hunk<T> iterate(Predicate<T> p, Consumer4<Integer, Integer, Integer, T> c) {
        return iterate(getIdeal3DParallelism(), p, c);
    }

    default Hunk<T> iterate(int parallelism, Predicate<T> p, Consumer4<Integer, Integer, Integer, T> c) {
        iterate(parallelism, (x, y, z, t) ->
        {
            if (p.test(t)) {
                c.accept(x, y, z, t);
            }
        });

        return this;
    }

    default Hunk<T> iterate(Consumer3<Integer, Integer, Integer> c) {
        return iterate(getIdeal3DParallelism(), c);
    }

    default Hunk<T> iterateSync(Consumer3<Integer, Integer, Integer> c) {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    c.accept(i, j, k);
                }
            }
        }

        return this;
    }

    default Hunk<T> iterateSync(Consumer4<Integer, Integer, Integer, T> c) {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    c.accept(i, j, k, get(i, j, k));
                }
            }
        }

        return this;
    }

    default Hunk<T> updateSync(Function4<Integer, Integer, Integer, T, T> c) {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    set(i, j, k, c.apply(i, j, k, get(i, j, k)));
                }
            }
        }

        return this;
    }

    default Hunk<T> iterateSyncIO(Consumer4IO<Integer, Integer, Integer, T> c) throws IOException {
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                for (int k = 0; k < getDepth(); k++) {
                    c.accept(i, j, k, get(i, j, k));
                }
            }
        }

        return this;
    }

    default Hunk<T> iterate(int parallelism, Consumer3<Integer, Integer, Integer> c) {
        compute3D(parallelism, (x, y, z, h) ->
        {
            for (int i = 0; i < h.getWidth(); i++) {
                for (int j = 0; j < h.getHeight(); j++) {
                    for (int k = 0; k < h.getDepth(); k++) {
                        c.accept(i + x, j + y, k + z);
                    }
                }
            }
        });

        return this;
    }

    default Hunk<T> iterate(Consumer4<Integer, Integer, Integer, T> c) {
        return iterate(getIdeal3DParallelism(), c);
    }

    default Hunk<T> iterate(int parallelism, Consumer4<Integer, Integer, Integer, T> c) {
        compute3D(parallelism, (x, y, z, h) ->
        {
            for (int i = 0; i < h.getWidth(); i++) {
                for (int j = 0; j < h.getHeight(); j++) {
                    for (int k = 0; k < h.getDepth(); k++) {
                        c.accept(i + x, j + y, k + z, h.get(i, j, k));
                    }
                }
            }
        });

        return this;
    }

    default Hunk<T> compute2D(Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        return compute2D(getIdeal2DParallelism(), v);
    }

    default Hunk<T> compute2D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        if (get2DDimension(parallelism) == 1) {
            v.accept(0, 0, 0, this);
            return this;
        }

        BurstExecutor e = MultiBurst.burst.burst(parallelism);

        if (isAtomic()) {
            getSectionsAtomic2D(parallelism, (xx, yy, zz, h) -> e.queue(() ->
            {
                v.accept(xx, yy, zz, h);
            }));

            e.complete();
        } else {
            KList<Runnable> rq = new KList<Runnable>(parallelism);

            getSections2D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
            {
                v.accept(xx, yy, zz, h);

                synchronized (rq) {
                    rq.add(r);
                }
            }), this::insert);

            e.complete();
            rq.forEach(Runnable::run);
        }

        return this;
    }

    default Hunk<T> compute2DYRange(int parallelism, int ymin, int ymax, Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        if (get2DDimension(parallelism) == 1) {
            v.accept(0, 0, 0, this);
            return this;
        }

        BurstExecutor e = MultiBurst.burst.burst(parallelism);
        KList<Runnable> rq = new KList<Runnable>(parallelism);
        getSections2DYLimit(parallelism, ymin, ymax, (xx, yy, zz, h, r) -> e.queue(() ->
        {
            v.accept(xx, yy, zz, h);

            synchronized (rq) {
                rq.add(r);
            }
        }), this::insert);
        e.complete();
        rq.forEach(Runnable::run);
        return this;
    }

    default Hunk<T> compute3D(Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        return compute3D(getIdeal3DParallelism(), v);
    }

    default Hunk<T> compute3D(int parallelism, Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        if (get3DDimension(parallelism) == 1) {
            v.accept(0, 0, 0, this);
            return this;
        }

        BurstExecutor e = MultiBurst.burst.burst(parallelism);
        KList<Runnable> rq = new KList<Runnable>(parallelism);
        getSections3D(parallelism, (xx, yy, zz, h, r) -> e.queue(() ->
        {
            v.accept(xx, yy, zz, h);
            synchronized (rq) {
                rq.add(r);
            }
        }), this::insert);
        e.complete();
        rq.forEach(Runnable::run);
        return this;
    }

    default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v) {
        return getSections2D(sections, v, this::insert);
    }

    default Hunk<T> getSectionsAtomic2D(int sections, Consumer4<Integer, Integer, Integer, Hunk<T>> v) {
        int dim = get2DDimension(sections);

        if (sections <= 1) {
            getAtomicSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh) -> v.accept(0, 0, 0, hh));
            return this;
        }

        int w = getWidth() / dim;
        int wr = getWidth() - (w * dim);
        int d = getDepth() / dim;
        int dr = getDepth() - (d * dim);
        int i, j;

        for (i = 0; i < getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < getDepth(); j += d) {
                int jj = j;
                getAtomicSection(i, 0, j, i + w + (i == 0 ? wr : 0), getHeight(), j + d + (j == 0 ? dr : 0), (h) -> v.accept(ii, 0, jj, h));
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }

        return this;
    }

    default Hunk<T> getSections2D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter) {
        int dim = get2DDimension(sections);

        if (sections <= 1) {
            getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
            return this;
        }

        int w = getWidth() / dim;
        int wr = getWidth() - (w * dim);
        int d = getDepth() / dim;
        int dr = getDepth() - (d * dim);
        int i, j;

        for (i = 0; i < getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < getDepth(); j += d) {
                int jj = j;
                getSection(i, 0, j, i + w + (i == 0 ? wr : 0), getHeight(), j + d + (j == 0 ? dr : 0), (h, r) -> v.accept(ii, 0, jj, h, r), inserter);
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }

        return this;
    }

    default Hunk<T> getSections2DYLimit(int sections, int ymin, int ymax, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter) {
        int dim = get2DDimension(sections);

        if (sections <= 1) {
            getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
            return this;
        }

        int w = getWidth() / dim;
        int wr = getWidth() - (w * dim);
        int d = getDepth() / dim;
        int dr = getDepth() - (d * dim);
        int i, j;

        for (i = 0; i < getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < getDepth(); j += d) {
                int jj = j;
                getSection(i, ymin, j, i + w + (i == 0 ? wr : 0), ymax, j + d + (j == 0 ? dr : 0), (h, r) -> v.accept(ii, ymin, jj, h, r), inserter);
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }

        return this;
    }

    default Hunk<T> getSections3D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v) {
        return getSections3D(sections, v, (xx, yy, zz, c) -> insert(xx, yy, zz, c));
    }

    default Hunk<T> getSections3D(int sections, Consumer5<Integer, Integer, Integer, Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter) {
        int dim = get3DDimension(sections);

        if (sections <= 1) {
            getSection(0, 0, 0, getWidth(), getHeight(), getDepth(), (hh, r) -> v.accept(0, 0, 0, hh, r), inserter);
            return this;
        }

        int w = getWidth() / dim;
        int h = getHeight() / dim;
        int d = getDepth() / dim;
        int wr = getWidth() - (w * dim);
        int hr = getHeight() - (h * dim);
        int dr = getDepth() - (d * dim);
        int i, j, k;

        for (i = 0; i < getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < getHeight(); j += d) {
                int jj = j;

                for (k = 0; k < getDepth(); k += d) {
                    int kk = k;
                    getSection(ii, jj, kk, i + w + (i == 0 ? wr : 0), j + h + (j == 0 ? hr : 0), k + d + (k == 0 ? dr : 0), (hh, r) -> v.accept(ii, jj, kk, hh, r), inserter);
                    i = i == 0 ? i + wr : i;
                    j = j == 0 ? j + hr : j;
                    k = k == 0 ? k + dr : k;
                }
            }
        }

        return this;
    }

    default Hunk<T> getSection(int x, int y, int z, int x1, int y1, int z1, Consumer2<Hunk<T>, Runnable> v) {
        return getSection(x, y, z, x1, y1, z1, v, (xx, yy, zz, c) -> insert(xx, yy, zz, c));
    }

    default Hunk<T> getSection(int x, int y, int z, int x1, int y1, int z1, Consumer2<Hunk<T>, Runnable> v, Consumer4<Integer, Integer, Integer, Hunk<T>> inserter) {
        Hunk<T> copy = crop(x, y, z, x1, y1, z1);
        v.accept(copy, () -> inserter.accept(x, y, z, copy));
        return this;
    }

    default Hunk<T> getAtomicSection(int x, int y, int z, int x1, int y1, int z1, Consumer<Hunk<T>> v) {
        Hunk<T> copy = croppedView(x, y, z, x1, y1, z1);
        v.accept(copy);
        return this;
    }

    /**
     * Create a new hunk from a section of this hunk.
     *
     * @param x1 The min x (inclusive)
     * @param y1 The min y (inclusive)
     * @param z1 The min z (inclusive)
     * @param x2 The max x (exclusive)
     * @param y2 The max y (exclusive)
     * @param z2 The max z (exclusive)
     * @return the new hunk (x2-x1, y2-y1, z2-z1)
     */
    default ArrayHunk<T> crop(int x1, int y1, int z1, int x2, int y2, int z2) {
        ArrayHunk<T> h = new ArrayHunk<T>(x2 - x1, y2 - y1, z2 - z1);

        for (int i = x1; i < x2; i++) {
            for (int j = y1; j < y2; j++) {
                for (int k = z1; k < z2; k++) {
                    h.setRaw(i - x1, j - y1, k - z1, getRaw(i, j, k));
                }
            }
        }

        return h;
    }

    /**
     * Create a new view of this same hunk from a section of this hunk.
     * Modifications are routed to this hunk!
     *
     * @param x1 The min x (inclusive)
     * @param y1 The min y (inclusive)
     * @param z1 The min z (inclusive)
     * @param x2 The max x (exclusive)
     * @param y2 The max y (exclusive)
     * @param z2 The max z (exclusive)
     * @return the cropped view of this hunk (x2-x1, y2-y1, z2-z1)
     */
    default Hunk<T> croppedView(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new HunkView<T>(this, x2 - x1, y2 - y1, z2 - z1, x1, y1, z1);
    }

    /**
     * @return The X length
     */
    int getWidth();

    /**
     * @return The Z length
     */
    int getDepth();

    /**
     * @return The Y length
     */
    int getHeight();

    /**
     * Set a region
     *
     * @param x1 inclusive 1st x
     * @param y1 inclusive 1st y
     * @param z1 inclusive 1st z
     * @param x2 inclusive 2nd x
     * @param y2 inclusive 2nd y
     * @param z2 inclusive 2nd z
     * @param t  the value to set
     */
    default void set(int x1, int y1, int z1, int x2, int y2, int z2, T t) {
        for (int i = x1; i <= x2; i++) {
            for (int j = y1; j <= y2; j++) {
                for (int k = z1; k <= z2; k++) {
                    setRaw(i, j, k, t);
                }
            }
        }
    }

    /**
     * Get the value to the closest valid position
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the value closest to the border of the hunk
     */
    default T getClosest(int x, int y, int z) {
        return getRaw(x >= getWidth() ? getWidth() - 1 : x < 0 ? 0 : x, y >= getHeight() ? getHeight() - 1 : y < 0 ? 0 : y, z >= getDepth() ? getDepth() - 1 : z < 0 ? 0 : z);
    }

    default BlockPosition getCenter() {
        return new BlockPosition(getCenterX(), getCenterY(), getCenterZ());
    }

    default int getCenterX() {
        return (int) Math.floor(getWidth() / 2);
    }

    default int getCenterY() {
        return (int) Math.floor(getHeight() / 2);
    }

    default int getCenterZ() {
        return (int) Math.floor(getDepth() / 2);
    }

    default void fill(T t) {
        set(0, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1, t);
    }

    /**
     * Get a 1 node thick hunk representing the face of this hunk
     *
     * @param f the face
     * @return the hunk view of this hunk
     */
    default Hunk<T> viewFace(HunkFace f) {
        switch (f) {
            case BOTTOM:
                return croppedView(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
            case EAST:
                return croppedView(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            case NORTH:
                return croppedView(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
            case SOUTH:
                return croppedView(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
            case TOP:
                return croppedView(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            case WEST:
                return croppedView(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            default:
                break;
        }

        return null;
    }

    /**
     * Crop (copy) a 1 node thick hunk representing the face of this hunk
     *
     * @param f the face
     * @return the hunk copy (face) of this hunk
     */
    default Hunk<T> cropFace(HunkFace f) {
        switch (f) {
            case BOTTOM:
                return crop(0, 0, 0, getWidth() - 1, 0, getDepth() - 1);
            case EAST:
                return crop(getWidth() - 1, 0, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            case NORTH:
                return crop(0, 0, 0, getWidth() - 1, getHeight() - 1, 0);
            case SOUTH:
                return crop(0, 0, 0, 0, getHeight() - 1, getDepth() - 1);
            case TOP:
                return crop(0, getHeight() - 1, 0, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            case WEST:
                return crop(0, 0, getDepth() - 1, getWidth() - 1, getHeight() - 1, getDepth() - 1);
            default:
                break;
        }

        return null;
    }

    /**
     * Set a value at the given position
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @param t the value
     */
    default void set(int x, int y, int z, T t) {
        if (!contains(x, y, z)) {
            Iris.warn("OUT OF BOUNDS " + x + " " + y + " " + z + " in bounds " + getWidth() + " " + getHeight() + " " + getDepth());
            return;
        }

        setRaw(x, y, z, t);
    }

    default void setIfExists(int x, int y, int z, T t) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth()) {
            return;
        }

        setRaw(x, y, z, t);
    }

    default T getIfExists(int x, int y, int z, T t) {
        if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight() || z < 0 || z >= getDepth()) {
            return t;
        }

        return getOr(x, y, z, t);
    }

    default T getIfExists(int x, int y, int z) {
        return getIfExists(x, y, z, null);
    }

    /**
     * Set a value at the given position without checking coordinate bounds
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @param t the value
     */
    void setRaw(int x, int y, int z, T t);

    /**
     * Get a value at the given position without checking coordinate bounds
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the value or null
     */
    T getRaw(int x, int y, int z);

    /**
     * Get a value at the given position
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the value or null
     */
    default T get(int x, int y, int z) {
        return getRaw(x, y, z);
    }

    default T getOr(int x, int y, int z, T t) {
        T v = getRaw(x, y, z);

        if (v == null) {
            return t;
        }

        return v;
    }

    /**
     * Insert a hunk into this one with an offset the inserted hunk
     *
     * @param offX the offset from zero for x
     * @param offY the offset from zero for y
     * @param offZ the offset from zero for z
     * @param hunk the hunk to insert
     */
    default void insert(int offX, int offY, int offZ, Hunk<T> hunk) {
        insert(offX, offY, offZ, hunk, false);
    }

    default void insertSoftly(int offX, int offY, int offZ, Hunk<T> hunk, Predicate<T> shouldOverwrite) {
        insertSoftly(offX, offY, offZ, hunk, false, shouldOverwrite);
    }

    /**
     * Insert a hunk into this one
     *
     * @param hunk the hunk to insert
     */
    default void insert(Hunk<T> hunk) {
        insert(0, 0, 0, hunk, false);
    }

    /**
     * Returns the source of this hunk view. This could return another hunk view,
     * not an actual source, however it does return it's underlying data source. If
     * this hunk is a data source and not a view, it will return null.
     *
     * @return the source or null if this is already the source
     */
    default Hunk<T> getSource() {
        return null;
    }

    /**
     * Insert a hunk into this one
     *
     * @param hunk     the hunk to insert
     * @param inverted invert the inserted hunk or not
     */
    default void insert(Hunk<T> hunk, boolean inverted) {
        insert(0, 0, 0, hunk, inverted);
    }

    /**
     * Insert a hunk into this one with an offset and possibly inverting the y of
     * the inserted hunk
     *
     * @param offX    the offset from zero for x
     * @param offY    the offset from zero for y
     * @param offZ    the offset from zero for z
     * @param hunk    the hunk to insert
     * @param invertY should the inserted hunk be inverted
     */
    default void insert(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY) {
        for (int i = offX; i < offX + hunk.getWidth(); i++) {
            for (int j = offY; j < offY + hunk.getHeight(); j++) {
                for (int k = offZ; k < offZ + hunk.getDepth(); k++) {
                    setRaw(i, j, k, hunk.getRaw(i - offX, j - offY, k - offZ));
                }
            }
        }
    }

    /**
     * Insert a hunk into this one with an offset and possibly inverting the y of. Will never insert a node if its
     * already used
     * the inserted hunk
     *
     * @param offX    the offset from zero for x
     * @param offY    the offset from zero for y
     * @param offZ    the offset from zero for z
     * @param hunk    the hunk to insert
     * @param invertY should the inserted hunk be inverted
     */
    default void insertSoftly(int offX, int offY, int offZ, Hunk<T> hunk, boolean invertY, Predicate<T> shouldOverwrite) {
        for (int i = offX; i < offX + hunk.getWidth(); i++) {
            for (int j = offY; j < offY + hunk.getHeight(); j++) {
                for (int k = offZ; k < offZ + hunk.getDepth(); k++) {
                    if (shouldOverwrite.test(getRaw(i, j, k))) {
                        setRaw(i, j, k, hunk.getRaw(i - offX, j - offY, k - offZ));
                    }
                }
            }
        }
    }

    /**
     * Acts like fill, however if used by a mapped hunk, will simply clear it
     *
     * @param b the data to use for fill
     */
    default void empty(T b) {
        fill(b);
    }

    /**
     * Take a hunk and scale it up using interpolation
     *
     * @param scale        the scale
     * @param d            the interpolation method
     * @param interpolated the interpolated value converter
     * @return the new hunk
     */
    default Hunk<T> interpolate3D(double scale, InterpolationMethod3D d, Interpolated<T> interpolated) {
        Hunk<T> t = Hunk.newArrayHunk((int) (getWidth() * scale), (int) (getHeight() * scale), (int) (getDepth() * scale));
        NoiseProvider3 n3 = (x, y, z) -> interpolated.toDouble(
                t.get((int) (x / scale),
                        (int) (y / scale),
                        (int) (z / scale)));

        for (int i = 0; i < t.getWidth(); i++) {
            for (int j = 0; j < t.getHeight(); j++) {
                for (int k = 0; k < t.getDepth(); k++) {
                    t.set(i, j, k, interpolated.fromDouble(IrisInterpolation.getNoise3D(d, i, j, k, scale, n3)));
                }
            }
        }

        return t;
    }

    /**
     * Take a hunk and scale it up using interpolation
     * 2D, (using only x and z) assumes the height is 1
     *
     * @param scale        the scale
     * @param d            the interpolation method
     * @param interpolated the interpolated value converter
     * @return the new hunk
     */
    default Hunk<T> interpolate2D(double scale, InterpolationMethod d, Interpolated<T> interpolated) {
        Hunk<T> t = Hunk.newArrayHunk((int) (getWidth() * scale), 1, (int) (getDepth() * scale));
        NoiseProvider n2 = (x, z) -> interpolated.toDouble(
                t.get((int) (x / scale),
                        0,
                        (int) (z / scale)));

        for (int i = 0; i < t.getWidth(); i++) {
            for (int j = 0; j < t.getDepth(); j++) {
                t.set(i, 0, j, interpolated.fromDouble(IrisInterpolation.getNoise(d, i, j, scale, n2)));
            }
        }

        return t;
    }

    default Hunk<T> rotate(double x, double y, double z, Supplier3R<Integer, Integer, Integer, Hunk<T>> builder) {
        int w = getWidth();
        int h = getHeight();
        int d = getDepth();
        int i, j, k;
        int[] c = {w / 2, h / 2, d / 2};
        int[] b = {0, 0, 0};
        int[] iii = {0, 0, 0};
        int[] aaa = {w, h, d};
        int[] aai = {w, h, 0};
        int[] iaa = {0, h, d};
        int[] aia = {w, 0, d};
        int[] iai = {0, h, 0};
        int[] iia = {0, 0, d};
        int[] aii = {w, 0, 0};
        rotate(x, y, z, iii);
        rotate(x, y, z, aaa);
        rotate(x, y, z, aai);
        rotate(x, y, z, iaa);
        rotate(x, y, z, aia);
        rotate(x, y, z, iai);
        rotate(x, y, z, iia);
        rotate(x, y, z, aii);
        int maxX = max(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int minX = min(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int maxY = max(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int minY = min(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int maxZ = max(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        int minZ = min(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        Hunk<T> r = builder.get(maxX - minX, maxY - minY, maxZ - minZ);
        int[] cr = {(maxX - minX) / 2, (maxY - minY) / 2, (maxZ - minZ) / 2};

        for (i = 0; i < w; i++) {
            for (j = 0; j < h; j++) {
                for (k = 0; k < d; k++) {
                    b[0] = i - c[0];
                    b[1] = j - c[1];
                    b[2] = k - c[2];
                    rotate(x, y, z, b);

                    try {
                        r.set(b[0] + cr[0], b[1] + cr[1], b[2] + cr[2], get(i, j, k));
                    } catch (Throwable e) {

                    }
                }
            }
        }

        return r;
    }

    default boolean isEmpty() {
        return false;
    }

    default boolean contains(int x, int y, int z) {
        return x < getWidth() && x >= 0 && y < getHeight() && y >= 0 && z < getDepth() && z >= 0;
    }

    default int volume() {
        return getWidth() * getDepth() * getHeight();
    }
}
