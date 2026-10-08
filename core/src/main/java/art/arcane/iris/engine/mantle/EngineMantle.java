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

package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterFluidBody;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface EngineMantle extends MatterGenerator {
    PlatformBlockState AIR = B.getState("AIR");

    Mantle<Matter> getMantle();

    Engine getEngine();

    int getRadius();

    int getRealRadius();

    @UnmodifiableView
    List<MantlePass> getComponents();

    void hotload();

    default int getHighest(int x, int z) {
        return getHighest(x, z, getData());
    }

    @ChunkCoordinates
    default KList<IrisPosition> findMarkers(int x, int z, MatterMarker marker) {
        KList<IrisPosition> p = new KList<>();
        if (getEngine().getPlatformHooks().shouldSkipMantleMarkerRead(getEngine(), x, z)) {
            return p;
        }

        getMantle().iterateChunk(x, z, MatterMarker.class, (xx, yy, zz, mm) -> {
            if (marker.equals(mm)) {
                p.add(new IrisPosition(xx + (x << 4), yy + getEngine().getMinHeight(), zz + (z << 4)));
            }
        });

        return p;
    }

    default int getHighest(int x, int z, boolean ignoreFluid) {
        return getHighest(x, z, getData(), ignoreFluid);
    }

    default int getHighest(int x, int z, IrisData data) {
        return getHighest(x, z, data, false);
    }

    default int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getEngine().getDimension().getFluidHeight());
    }

    default int trueHeight(int x, int z) {
        return getComplex().getRoundedHeighteightStream().get(x, z);
    }

    default boolean isCarved(int x, int h, int z) {
        return getMantle().get(x, h, z, MatterCavern.class) != null;
    }

    default PlatformBlockState get(int x, int y, int z) {
        PlatformBlockState block = getMantle().get(x, y, z, PlatformBlockState.class);
        if (block == null)
            return AIR;
        return block;
    }

    default boolean isPreventingDecay() {
        return getEngine().getDimension().isPreventLeafDecay();
    }

    default boolean isUnderwater(int x, int z) {
        return getHighest(x, z, true) <= getFluidHeight();
    }

    default int getFluidHeight() {
        return getEngine().getDimension().getFluidHeight();
    }

    default boolean isDebugSmartBore() {
        return getEngine().getDimension().isDebugSmartBore();
    }

    default void trim(long duration) {
        getMantle().trim(duration);
    }

    default void trim(long dur, int limit) {
        getMantle().trim(dur, limit);
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

    default void trim(int limit) {
        getMantle().trim(TimeUnit.SECONDS.toMillis(IrisSettings.get().getPerformance().getMantleKeepAlive()), limit);
    }
    default int unloadTectonicPlate(int tectonicLimit){
        return getMantle().unloadTectonicPlate(tectonicLimit);
    }

    default MultiBurst burst() {
        return getEngine().burst();
    }

    @ChunkCoordinates
    default void insertMatter(int x, int z, Hunk<PlatformBlockState> blocks, boolean multicore) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        UpperDimensionContext upperCtx = getEngine().getUpperContext();
        boolean protectUpper = upperCtx != null;

        MantleChunk<Matter> chunk = getMantle().getChunk(x, z).use();
        try {
            if (protectUpper) {
                int chunkBlockX = x << 4;
                int chunkBlockZ = z << 4;
                int gap = getEngine().getDimension().getUpperDimensionGap();
                int[] upperYs = new int[256];
                for (int i = 0; i < 256; i++) {
                    int lx = i >> 4;
                    int lz = i & 15;
                    int worldX = chunkBlockX + lx;
                    int worldZ = chunkBlockZ + lz;
                    int he = (int) Math.round(getEngine().getComplex().getHeightStream().get((double) worldX, (double) worldZ));
                    int rawUpper = upperCtx.getUpperSurfaceY(worldX, worldZ);
                    upperYs[i] = Math.max(rawUpper, he + gap);
                }
                chunk.iterate(PlatformBlockState.class, (lx, y, lz, value) -> {
                    int colIdx = (lx << 4) | (lz & 15);
                    if (y < upperYs[colIdx]) {
                        blocks.set(lx, y, lz, value);
                    }
                });
            } else {
                chunk.iterate(PlatformBlockState.class, (lx, y, lz, value) -> blocks.set(lx, y, lz, value));
            }
        } finally {
            chunk.release();
        }
    }

    @BlockCoordinates
    default void updateBlock(int x, int y, int z) {
        getMantle().set(x, y, z, UpdateMatter.ON);
    }

    default int getLoadedRegionCount() {
        return getMantle().getLoadedRegionCount();
    }

    default boolean isCovered(int x, int z) {
        int s = getRealRadius();

        for (int i = -s; i <= s; i++) {
            for (int j = -s; j <= s; j++) {
                int xx = i + x;
                int zz = j + z;
                if (!getMantle().hasFlag(xx, zz, MantleFlag.REAL)) {
                    return false;
                }
            }
        }

        return true;
    }

    default void cleanupChunk(int x, int z) {
        if (!isCovered(x, z)) return;
        doCleanupChunk(x, z);
    }

    default void forceCleanupChunk(int x, int z) {
        MantleChunk<Matter> chunk = getMantle().getChunk(x, z).use();
        try {
            chunk.raiseFlagUnchecked(MantleFlag.CLEANED, () -> {
                MantleSliceRetention.deleteUnlessRetained(chunk, PlatformBlockState.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, String.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, UpdateMatter.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, MatterCavern.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, MatterFluidBody.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, MatterMarker.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, TreeBlockMaterial.class);
                chunk.trimSlices();
            });
        } finally {
            chunk.release();
        }
    }

    private void doCleanupChunk(int x, int z) {
        MantleChunk<Matter> chunk = getMantle().getChunk(x, z).use();
        try {
            chunk.raiseFlagUnchecked(MantleFlag.CLEANED, () -> {
                MantleSliceRetention.deleteUnlessRetained(chunk, PlatformBlockState.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, UpdateMatter.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, MatterCavern.class);
                MantleSliceRetention.deleteUnlessRetained(chunk, MatterFluidBody.class);
                chunk.trimSlices();
            });
        } finally {
            chunk.release();
        }
    }

    default int getUnloadRegionCount() {
        return getMantle().getUnloadRegionCount();
    }

    default double getAdjustedIdleDuration() {
        return getMantle().getAdjustedIdleDuration();
    }
}
