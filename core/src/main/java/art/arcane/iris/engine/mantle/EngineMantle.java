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
import art.arcane.iris.core.modules.GenerationLayers;
import art.arcane.iris.core.modules.ModuleLayerValues;
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
                removeGenerationLayersUnlessRetained(chunk);
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
                removeGenerationLayersUnlessRetained(chunk);
                chunk.trimSlices();
            });
        } finally {
            chunk.release();
        }
    }

    /** The pack's module mantle layers that cleanup removes; none unless the engine's pack registers some. */
    default GenerationLayers getGenerationLayers() {
        return GenerationLayers.NONE;
    }

    private void removeGenerationLayersUnlessRetained(MantleChunk<Matter> chunk) {
        if (MantleSliceRetention.isRetained(ModuleLayerValues.class)) {
            return;
        }
        getGenerationLayers().removeFrom(chunk);
    }

    default int getUnloadRegionCount() {
        return getMantle().getUnloadRegionCount();
    }

    default double getAdjustedIdleDuration() {
        return getMantle().getAdjustedIdleDuration();
    }
}
