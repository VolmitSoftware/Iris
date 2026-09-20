/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded.command;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.modded.ModdedBlockResolution;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.jetbrains.annotations.Nullable;


final class ModdedObjectPlacer implements IObjectPlacer {
    private static final int DEFAULT_FLUID_HEIGHT = 63;

    private final NativeEditWorld level;
    private final Engine engine;
    private final NativeEditWorld.EditSession undo;
    private int writes = 0;
    private int nonAirWrites = 0;
    private int skippedTiles = 0;
    private int restoredTiles = 0;

    ModdedObjectPlacer(NativeWorld level, @Nullable Engine engine) {
        this.level = new NativeEditWorld(level);
        this.undo = this.level.editSession(new NativeEditWorld.EditOptions(level.minHeight() + 1, level.maxHeight(), true));
        this.engine = engine;
    }

    NativeEditWorld.EditSession undoSnapshot() {
        return undo;
    }

    int writes() {
        return writes;
    }

    int nonAirWrites() {
        return nonAirWrites;
    }

    int skippedTiles() {
        return skippedTiles;
    }

    int restoredTiles() {
        return restoredTiles;
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return level.highest(x, z, false);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return level.highest(x, z, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, NativeBlockState s) {
        NativeEditWorld.WriteResult result = undo.set(x, y, z, s);
        if (result == NativeEditWorld.WriteResult.SKIPPED) {
            return;
        }
        writes++;
        if (result == NativeEditWorld.WriteResult.BLOCK) {
            nonAirWrites++;
        }
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        return level.block(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return false;
    }

    /**
     * Mantle Y is relative to the world minimum height while this placer works in absolute world Y, so shift
     * before the lookup. Only answers from an already loaded mantle chunk: a hand placed object can sit
     * anywhere, and loading a mantle chunk to answer a carve probe would generate terrain as a side effect.
     */
    @Override
    public boolean isCarved(int x, int y, int z) {
        if (engine == null) {
            return false;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (mantle.isClosed() || !mantle.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        return engine.getMantle().isCarved(x, y - engine.getWorld().minHeight(), z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return level.solid(x, y, z);
    }

    /**
     * Engine height stream against the dimension fluid height, both engine relative, so no shift here. Needs a
     * ready complex; the placer also runs from commands against levels that never bound an engine.
     */
    @Override
    public boolean isUnderwater(int x, int z) {
        return engine != null && engine.getComplex() != null && engine.getMantle().isUnderwater(x, z);
    }

    /**
     * IrisDimension fluid height is engine relative while this placer works in absolute world Y, so shift it
     * up by the engine minimum before handing it to object placement.
     */
    @Override
    public int getFluidHeight() {
        return engine == null
                ? DEFAULT_FLUID_HEIGHT
                : engine.getMinHeight() + engine.getDimension().getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return false;
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        if (!(tile instanceof ModdedTileData moddedTile)) {
            skippedTiles++;
            return;
        }
        if (level.restoreTile(new NativeBlockPoint(xx, yy, zz), moddedTile.nativeData(),
                error -> ModdedIrisLog.error("Iris tile restore failed at {} {} {}", xx, yy, zz, error))) {
            restoredTiles++;
        } else {
            skippedTiles++;
        }
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
    }

    @Override
    public <T> T getData(int xx, int yy, int zz, Class<T> t) {
        return null;
    }

    @Override
    public Engine getEngine() {
        return engine;
    }
}
