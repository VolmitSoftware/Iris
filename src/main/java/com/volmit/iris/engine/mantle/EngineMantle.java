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

package com.volmit.iris.engine.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.matter.slices.UpdateMatter;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.Chunk;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.function.Consumer;

// TODO: MOVE PLACER OUT OF MATTER INTO ITS OWN THING
public interface EngineMantle extends IObjectPlacer {
    BlockData AIR = B.get("AIR");

    Mantle getMantle();

    Engine getEngine();

    int getRadius();

    KList<MantleComponent> getComponents();

    void registerComponent(MantleComponent c);

    default int getHighest(int x, int z) {
        return getHighest(x, z, getData());
    }

    @ChunkCoordinates
    default KList<IrisPosition> findMarkers(int x, int z, MatterMarker marker) {
        KList<IrisPosition> p = new KList<>();
        getMantle().iterateChunk(x, z, MatterMarker.class, (xx, yy, zz, mm) -> {
            if (marker.equals(mm)) {
                p.add(new IrisPosition(xx + (x << 4), yy, zz + (z << 4)));
            }
        });

        return p;
    }

    default int getHighest(int x, int z, boolean ignoreFluid) {
        return getHighest(x, z, getData(), ignoreFluid);
    }

    @Override
    default int getHighest(int x, int z, IrisData data) {
        return getHighest(x, z, data, false);
    }

    @Override
    default int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getEngine().getDimension().getFluidHeight());
    }

    default int trueHeight(int x, int z) {
        return getComplex().getTrueHeightStream().get(x, z);
    }

    default boolean isCarved(int x, int h, int z) {
        return getMantle().get(x, h, z, MatterCavern.class) != null;
    }

    @Override
    default void set(int x, int y, int z, BlockData d) {
        getMantle().set(x, y, z, d == null ? AIR : d);
    }

    @Override
    default void setTile(int x, int y, int z, TileData<? extends TileState> d) {
        // TODO SET TILE
    }

    @Override
    default BlockData get(int x, int y, int z) {
        BlockData block = getMantle().get(x, y, z, BlockData.class);

        if (block == null) {
            return AIR;
        }

        return block;
    }

    @Override
    default boolean isPreventingDecay() {
        return getEngine().getDimension().isPreventLeafDecay();
    }

    @Override
    default boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    @Override
    default boolean isUnderwater(int x, int z) {
        return getHighest(x, z, true) <= getFluidHeight();
    }

    @Override
    default int getFluidHeight() {
        return getEngine().getDimension().getFluidHeight();
    }

    @Override
    default boolean isDebugSmartBore() {
        return getEngine().getDimension().isDebugSmartBore();
    }

    default void trim(long dur) {
        getMantle().trim(dur);
    }

    default IrisData getData() {
        return getEngine().getData();
    }

    default EngineTarget getTarget() {
        return getEngine().getTarget();
    }

    default IrisDimension getDimension() {
        return getEngine().getDimension();
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    default void close() {
        getMantle().close();
    }

    default void saveAllNow() {
        getMantle().saveAll();
    }

    default void save() {

    }

    default void trim() {
        getMantle().trim(60000);
    }

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default int getRealRadius() {
        return (int) Math.ceil(getRadius() / 2D);
    }


    @ChunkCoordinates
    default void generateMatter(int x, int z, boolean multicore) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        KList<Runnable> post = new KList<>();
        Consumer<Runnable> c = (i) -> {
            synchronized (post) {
                post.add(i);
            }
        };
        int s = getRealRadius();
        BurstExecutor burst = burst().burst(multicore);
        MantleWriter writer = getMantle().write(this, x, z, s * 2);
        for (int i = -s; i <= s; i++) {
            for (int j = -s; j <= s; j++) {
                int xx = i + x;
                int zz = j + z;
                burst.queue(() -> {
                    MantleChunk mc = getMantle().getChunk(xx, zz);

                    for (MantleComponent k : getComponents()) {
                        generateMantleComponent(writer, xx, zz, k, c, mc);
                    }
                });
            }
        }

        burst.complete();

        while (!post.isEmpty()) {
            KList<Runnable> px = post.copy();
            post.clear();
            burst().burst(multicore, px);
        }

        getMantle().flag(x, z, MantleFlag.REAL, true);
    }

    default void generateMantleComponent(MantleWriter writer, int x, int z, MantleComponent c, Consumer<Runnable> post, MantleChunk mc) {
        mc.raiseFlag(c.getFlag(), () -> c.generateLayer(writer, x, z, post));
    }

    @ChunkCoordinates
    default <T> void insertMatter(int x, int z, Class<T> t, Hunk<T> blocks, boolean multicore) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        getMantle().iterateChunk(x, z, t, blocks::set);
    }

    @BlockCoordinates
    default void updateBlock(int x, int y, int z) {
        getMantle().set(x, y, z, UpdateMatter.ON);
    }

    @BlockCoordinates
    default void dropCavernBlock(int x, int y, int z) {
        Matter matter = getMantle().getChunk(x & 15, z & 15).get(y & 15);

        if (matter != null) {
            matter.slice(MatterCavern.class).set(x & 15, y & 15, z & 15, null);
        }
    }

    @ChunkCoordinates
    default List<IrisFeaturePositional> getFeaturesInChunk(Chunk c) {
        return getFeaturesInChunk(c.getX(), c.getZ());
    }

    @ChunkCoordinates
    default List<IrisFeaturePositional> getFeaturesInChunk(int x, int z) {
        return getMantle().getChunk(x, z).getFeatures();
    }


    @ChunkCoordinates
    default KList<IrisFeaturePositional> forEachFeature(Chunk c) {
        return forEachFeature((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
    }

    @BlockCoordinates
    default KList<IrisFeaturePositional> forEachFeature(double x, double z) {
        KList<IrisFeaturePositional> pos = new KList<>();

        for (IrisFeaturePositional i : getEngine().getDimension().getSpecificFeatures()) {
            if (i.shouldFilter(x, z, getEngine().getComplex().getRng(), getData())) {
                pos.add(i);
            }
        }

        int s = getRealRadius();
        int i, j;
        int cx = (int) x >> 4;
        int cz = (int) z >> 4;

        for (i = -s; i <= s; i++) {
            for (j = -s; j <= s; j++) {
                try {
                    for (IrisFeaturePositional k : getFeaturesInChunk(i + cx, j + cz)) {
                        if (k.shouldFilter(x, z, getEngine().getComplex().getRng(), getData())) {
                            pos.add(k);
                        }
                    }
                } catch (Throwable e) {
                    Iris.error("FILTER ERROR" + " AT " + (cx + i) + " " + (j + cz));
                    e.printStackTrace();
                    Iris.reportError(e);
                }
            }
        }

        return pos;
    }

    default boolean queueRegenerate(int x, int z) {
        return false; // TODO:
    }

    default boolean dequeueRegenerate(int x, int z) {
        return false;// TODO:
    }

    default int getLoadedRegionCount() {
        return getMantle().getLoadedRegionCount();
    }
}
