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

package art.arcane.iris.util.mantle;

import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.iris.util.mantle.io.IOWorker;
import art.arcane.iris.util.matter.Matter;
import art.arcane.iris.util.matter.MatterSlice;
import art.arcane.iris.util.parallel.HyperLock;
import art.arcane.iris.util.parallel.MultiBurst;

import java.io.File;
import java.io.IOException;

/**
 * Iris adapter over shared mantle runtime.
 */
public class Mantle extends art.arcane.volmlib.util.mantle.Mantle<TectonicPlate, MantleChunk> {
    private static final int LOCK_SIZE = Short.MAX_VALUE;

    @BlockCoordinates
    public Mantle(File dataFolder, int worldHeight) {
        super(dataFolder, worldHeight, LOCK_SIZE, new HyperLock(), MultiBurst.ioBurst, new IOWorkerRegionIO(dataFolder, worldHeight));
    }

    @ChunkCoordinates
    public MantleWriter write(EngineMantle engineMantle, int x, int z, int radius, boolean multicore) {
        return new MantleWriter(engineMantle, this, x, z, radius, multicore);
    }

    @Override
    protected TectonicPlate createRegion(int x, int z) {
        return new TectonicPlate(getWorldHeight(), x, z);
    }

    @Override
    protected <T> void setChunkValue(MantleChunk chunk, int x, int y, int z, T value) {
        Matter matter = chunk.getOrCreate(y >> 4);
        matter.slice(matter.getClass(value)).set(x & 15, y & 15, z & 15, value);
    }

    @Override
    protected <T> void removeChunkValue(MantleChunk chunk, int x, int y, int z, Class<T> type) {
        Matter matter = chunk.getOrCreate(y >> 4);
        matter.slice(type).set(x & 15, y & 15, z & 15, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T getChunkValue(MantleChunk chunk, int x, int y, int z, Class<T> type) {
        return (T) chunk.getOrCreate(y >> 4)
                .slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    @Override
    protected <T> void iterateChunkValues(MantleChunk chunk, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        chunk.iterate(type, iterator);
    }

    @Override
    protected void deleteChunkSlice(MantleChunk chunk, Class<?> type) {
        chunk.deleteSlices(type);
    }

    @Override
    protected boolean shouldRetainSlice(Class<?> sliceType) {
        return IrisToolbelt.isRetainingMantleDataForSlice(sliceType.getCanonicalName());
    }

    @Override
    protected String formatDuration(double millis) {
        return Form.duration(millis, 0);
    }

    @Override
    protected void onDebug(String message) {
        Iris.debug(message);
    }

    @Override
    protected void onWarn(String message) {
        Iris.warn(message);
    }

    @Override
    protected void onError(Throwable throwable) {
        Iris.reportError(throwable);
    }

    @Override
    protected void deleteTemporaryFiles() {
        IO.delete(new File(getDataFolder(), ".tmp"));
    }

    public void set(int x, int y, int z, Matter matter) {
        for (MatterSlice<?> slice : matter.getSliceMap().values()) {
            slice.iterate((mx, my, mz, v) -> set(mx + x, my + y, mz + z, v));
        }
    }

    public <T> void set(int x, int y, int z, MatterSlice<T> slice) {
        if (slice.isEmpty()) {
            return;
        }

        slice.iterateSync((xx, yy, zz, t) -> set(x + xx, y + yy, z + zz, t));
    }

    public boolean shouldReduce(Engine engine) {
        return !engine.isStudio() || IrisSettings.get().getPerformance().isTrimMantleInStudio();
    }

    private static final class IOWorkerRegionIO implements RegionIO<TectonicPlate> {
        private final IOWorker worker;

        private IOWorkerRegionIO(File root, int worldHeight) {
            this.worker = new IOWorker(root, worldHeight);
        }

        @Override
        public TectonicPlate read(String name) throws IOException {
            return worker.read(name);
        }

        @Override
        public void write(String name, TectonicPlate region) throws IOException {
            worker.write(name, region);
        }

        @Override
        public void close() throws IOException {
            worker.close();
        }
    }
}
