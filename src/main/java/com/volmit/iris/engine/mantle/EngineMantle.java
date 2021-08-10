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
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Chunk;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

// TODO: MOVE PLACER OUT OF MATTER INTO ITS OWN THING
public interface EngineMantle extends IObjectPlacer {
    BlockData AIR = B.get("AIR");

    Mantle getMantle();

    Engine getEngine();

    CompletableFuture<Integer> getRadius();

    KList<MantleComponent> getComponents();

    void registerComponent(MantleComponent c);

    default int getHighest(int x, int z) {
        return getHighest(x, z, getData());
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
        try {
            return (int) Math.ceil(getRadius().get() / 2D);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return 0;
    }


    @ChunkCoordinates
    default void generateMatter(int x, int z) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        List<Runnable> post = Collections.synchronizedList(new KList<>());
        Consumer<Runnable> c = post::add;
        int s = getRealRadius();
        BurstExecutor burst = burst().burst();

        for (int i = -s; i <= s; i++) {
            int xx = i + x;
            for (int j = -s; j <= s; j++) {
                int zz = j + z;
                burst.queue(() -> {
                    getComponents().forEach((f) -> generateMantleComponent(xx, zz, f, c));
                });
            }
        }

        burst.complete();
        burst().burst(post);
    }

    default void generateMantleComponent(int x, int z, MantleComponent c, Consumer<Runnable> post) {
        getMantle().raiseFlag(x, z, c.getFlag(), () -> c.generateLayer(x, z, post));
    }

    @ChunkCoordinates
    default <T> void insertMatter(int x, int z, Class<T> t, Hunk<T> blocks) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        getMantle().iterateChunk(x, z, t, blocks::set);
    }

    @BlockCoordinates
    default void updateBlock(int x, int y, int z) {
        getMantle().flag(x >> 4, z >> 4, MantleFlag.UPDATE, true);
        getMantle().set(x, y, z, true);
    }

    @ChunkCoordinates
    default KList<IrisFeaturePositional> getFeaturesInChunk(Chunk c) {
        return getFeaturesInChunk(c.getX(), c.getZ());
    }

    @ChunkCoordinates
    default KList<IrisFeaturePositional> getFeaturesInChunk(int x, int z) {
        KList<IrisFeaturePositional> pos = new KList<>();
        getMantle().iterateChunk(x, z, IrisFeaturePositional.class, (a, b, c, f) -> pos.add(f), MantleFlag.FEATURE);
        return pos;
    }

    @BlockCoordinates
    default KList<IrisFeaturePositional> forEachFeature(double x, double z) {
        KList<IrisFeaturePositional> pos = new KList<>();

        if (!getEngine().getDimension().hasFeatures(getEngine())) {
            return pos;
        }

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
                    for (IrisFeaturePositional k : getFeaturesInChunk(i + cx, j + cx)) {
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
}
