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
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.mantle.components.MantleJigsawComponent;
import art.arcane.iris.engine.mantle.components.MantleObjectComponent;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.hunk.Hunk;
import art.arcane.iris.util.mantle.Mantle;
import art.arcane.iris.util.mantle.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterFluidBody;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.iris.util.matter.*;
import art.arcane.iris.util.matter.slices.UpdateMatter;
import art.arcane.iris.util.parallel.MultiBurst;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface EngineMantle extends MatterGenerator {
    BlockData AIR = B.get("AIR");

    Mantle getMantle();

    Engine getEngine();

    int getRadius();

    int getRealRadius();

    @UnmodifiableView
    List<Pair<List<MantleComponent>, Integer>> getComponents();

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

    default int getHighest(int x, int z, IrisData data) {
        return getHighest(x, z, data, false);
    }

    default int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return ignoreFluid ? trueHeight(x, z) : Math.max(trueHeight(x, z), getEngine().getDimension().getFluidHeight());
    }

    default int trueHeight(int x, int z) {
        return getComplex().getRoundedHeighteightStream().get(x, z);
    }

    @Deprecated(forRemoval = true)
    default boolean isCarved(int x, int h, int z) {
        return getMantle().get(x, h, z, MatterCavern.class) != null;
    }

    @Deprecated(forRemoval = true)
    default BlockData get(int x, int y, int z) {
        BlockData block = getMantle().get(x, y, z, BlockData.class);
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
    default <T> void insertMatter(int x, int z, Class<T> t, Hunk<T> blocks, boolean multicore) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        var chunk = getMantle().getChunk(x, z).use();
        try {
            chunk.iterate(t, blocks::set);
        } finally {
            chunk.release();
        }
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

    default boolean queueRegenerate(int x, int z) {
        return false; // TODO:
    }

    default boolean dequeueRegenerate(int x, int z) {
        return false;// TODO:
    }

    default int getLoadedRegionCount() {
        return getMantle().getLoadedRegionCount();
    }

    MantleJigsawComponent getJigsawComponent();

    MantleObjectComponent getObjectComponent();

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
        MantleChunk chunk = getMantle().getChunk(x, z).use();
        try {
            chunk.raiseFlagUnchecked(MantleFlag.CLEANED, () -> {
                chunk.deleteSlices(BlockData.class);
                chunk.deleteSlices(String.class);
                chunk.deleteSlices(MatterCavern.class);
                chunk.deleteSlices(MatterFluidBody.class);
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
