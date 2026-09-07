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
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.mantle.components.MantleObjectComponent;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveStorage;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.context.ChunkContext;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

public interface EngineMantle extends MatterGenerator {
    PlatformBlockState AIR = B.getState("AIR");

    Mantle<Matter> getMantle();

    Engine getEngine();

    int getRadius();

    int getRealRadius();

    @UnmodifiableView
    List<MantlePass> getComponents();

    @UnmodifiableView
    Map<MantleFlag, MantleComponent> getRegisteredComponents();

    boolean registerComponent(MantleComponent c);

    @UnmodifiableView
    KList<MantleFlag> getComponentFlags();

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
        OptionalInt resolved = getComplex().resolvedTerrainHeight(x, z, ignoreFluid);
        if (resolved.isPresent()) {
            return resolved.getAsInt();
        }
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getFluidHeight(x, z));
    }

    default int trueHeight(int x, int z) {
        return getComplex().getRoundedHeighteightStream().get(x, z);
    }

    default boolean isCarved(int x, int h, int z) {
        Optional<TerrainBoundarySignature> resolved = getComplex().resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            PreObjectMatterCell cell = getMantle().get(x, h, z, PreObjectMatterCell.class);
            if (cell != null && cell.blockCaptured()) {
                PlatformBlockState block = getMantle().get(x, h, z, PlatformBlockState.class);
                if (block != null) {
                    return (block.isAir() || block.isFluid())
                            && resolved.get().geometry().hasSolidAbove(h + getEngine().getMinHeight());
                }
            }
            if (cell != null && cell.cavernCaptured()) {
                return getMantle().get(x, h, z, MatterCavern.class) != null;
            }
            return resolved.get().geometry().isEnclosedOpenAt(h + getEngine().getMinHeight());
        }
        HydrologyCaveCell hydrology = HydrologyCaveStorage.getIfPresent(getMantle(), x, h, z);
        if (hydrology != null) {
            return hydrology.carves();
        }
        return getMantle().get(x, h, z, MatterCavern.class) != null;
    }

    default PlatformBlockState get(int x, int y, int z) {
        PlatformBlockState block = getMantle().get(x, y, z, PlatformBlockState.class);
        Optional<TerrainBoundarySignature> resolved = getComplex().resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            PreObjectMatterCell cell = getMantle().get(x, y, z, PreObjectMatterCell.class);
            if (cell == null || !cell.blockCaptured()) {
                String stateKey = resolved.get().geometry().voxelAt(y + getEngine().getMinHeight()).stateKey();
                PlatformBlockState natural = IrisPlatforms.get().registries().blockOrNull(stateKey);
                if (natural == null) {
                    throw new IllegalStateException("Saved terrain state is unavailable: " + stateKey);
                }
                return natural;
            }
        }
        return block == null ? AIR : block;
    }

    default boolean isPreventingDecay() {
        return getEngine().getDimension().isPreventLeafDecay();
    }

    default boolean isUnderwater(int x, int z) {
        return getHighest(x, z, true) < getFluidHeight(x, z);
    }

    default int getFluidHeight() {
        return getEngine().getDimension().getFluidHeight();
    }

    default int getFluidHeight(int x, int z) {
        Optional<TerrainBoundarySignature> resolved = getComplex().resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            return resolved.get().fluidHeight().orElse(-1);
        }
        return (int) Math.round(getComplex().getRiverWaterSurfaceStream().get(x, z));
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
    default void insertMatter(
            int x,
            int z,
            Hunk<PlatformBlockState> blocks,
            boolean multicore,
            ChunkContext context
    ) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        UpperDimensionContext upperCtx = getEngine().getUpperContext();
        boolean protectUpper = upperCtx != null;
        DimensionStackContext stackContext = getEngine().getDimensionStackContext();

        MantleChunk<Matter> chunk = getMantle().getChunk(x, z).use();
        try {
            if (stackContext != null) {
                DimensionStackLayout[] layouts = new DimensionStackLayout[256];
                for (int i = 0; i < layouts.length; i++) {
                    int localX = i >> 4;
                    int localZ = i & 15;
                    layouts[i] = context.getDimensionStackLayout(localX, localZ);
                }
                chunk.iterate(PlatformBlockState.class, (localX, y, localZ, value) -> {
                    DimensionStackLayout layout = layouts[(localX << 4) | (localZ & 15)];
                    if (!layout.isHostFeatureProtectedY(y)) {
                        blocks.set(localX, y, localZ, value);
                    }
                });
            } else if (protectUpper) {
                int chunkBlockX = x << 4;
                int chunkBlockZ = z << 4;
                int[] upperYs = new int[256];
                for (int i = 0; i < 256; i++) {
                    int lx = i >> 4;
                    int lz = i & 15;
                    int worldX = chunkBlockX + lx;
                    int worldZ = chunkBlockZ + lz;
                    upperYs[i] = upperCtx.getEffectiveSurfaceY(worldX, worldZ);
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

    MantleObjectComponent getObjectComponent();

    default boolean isCovered(int x, int z) {
        int s = Math.max(getRadius(), getRealRadius());

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

    default boolean cleanupChunk(int x, int z) {
        return cleanupTargetIfCovered(x, z, false);
    }

    default boolean forceCleanupChunk(int x, int z) {
        return cleanupTargetIfCovered(x, z, true);
    }

    default void cleanupChunksCoveredBy(
            int newRealX,
            int newRealZ,
            boolean force,
            ChunkCleanupCallback callback
    ) {
        Objects.requireNonNull(callback, "callback");
        int radius = Math.max(getRadius(), getRealRadius());
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            int candidateX = newRealX + offsetX;
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int candidateZ = newRealZ + offsetZ;
                if (getMantle().hasFlag(candidateX, candidateZ, MantleFlag.CLEANED)) {
                    continue;
                }
                if (cleanupTargetIfCovered(candidateX, candidateZ, force)) {
                    callback.onChunkCleaned(candidateX, candidateZ);
                }
            }
        }
    }

    private boolean cleanupTargetIfCovered(int x, int z, boolean force) {
        if (getMantle().hasFlag(x, z, MantleFlag.CLEANED) || !isCovered(x, z)) {
            return false;
        }
        return cleanupCoveredChunk(x, z, force);
    }

    default boolean cleanupCoveredChunk(int x, int z, boolean force) {
        MantleChunk<Matter> chunk = getMantle().getChunk(x, z).use();
        try {
            synchronized (chunk) {
                if (chunk.isFlagged(MantleFlag.CLEANED)) {
                    return false;
                }
                chunk.raiseFlagUnchecked(MantleFlag.CLEANED, () -> cleanupSlices(chunk, force));
                return true;
            }
        } finally {
            chunk.release();
        }
    }

    private void cleanupSlices(MantleChunk<Matter> chunk, boolean force) {
        MantleSliceRetention.deleteUnlessRetained(chunk, PlatformBlockState.class);
        if (force) {
            MantleSliceRetention.deleteUnlessRetained(chunk, String.class);
        }
        MantleSliceRetention.deleteUnlessRetained(chunk, UpdateMatter.class);
        MantleSliceRetention.deleteUnlessRetained(chunk, MatterCavern.class);
        MantleSliceRetention.deleteUnlessRetained(chunk, MatterFluidBody.class);
        if (force) {
            MantleSliceRetention.deleteUnlessRetained(chunk, MatterMarker.class);
            MantleSliceRetention.deleteUnlessRetained(chunk, TreeBlockMaterial.class);
        }
        chunk.deleteSlices(PreObjectMatterCell.class);
        chunk.trimSlices();
    }

    default int getUnloadRegionCount() {
        return getMantle().getUnloadRegionCount();
    }

    default double getAdjustedIdleDuration() {
        return getMantle().getAdjustedIdleDuration();
    }

    @FunctionalInterface
    interface ChunkCleanupCallback {
        ChunkCleanupCallback NONE = (x, z) -> {
        };

        void onChunkCleaned(int x, int z);
    }
}
