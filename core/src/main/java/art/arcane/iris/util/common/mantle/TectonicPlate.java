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

import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.iris.Iris;
import art.arcane.iris.engine.EnginePanic;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Tectonic Plates are essentially representations of regions in minecraft.
 * Tectonic Plates are fully atomic & thread safe
 */
public class TectonicPlate extends art.arcane.volmlib.util.mantle.TectonicPlate<MantleChunk> {
    public static final int MISSING = art.arcane.volmlib.util.mantle.TectonicPlate.MISSING;
    public static final int CURRENT = art.arcane.volmlib.util.mantle.TectonicPlate.CURRENT;

    public TectonicPlate(int worldHeight, int x, int z) {
        super(worldHeight, x, z);
    }

    public TectonicPlate(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException {
        super(worldHeight, din, versioned);
    }

    @Override
    protected void beforeReadChunk(int index) {
        Iris.addPanic("read-chunk", "Chunk[" + index + "]");
    }

    @Override
    protected void afterReadChunk(int index) {
        EnginePanic.saveLast();
    }

    @Override
    protected void onReadChunkFailure(int index, long start, long end, CountingDataInputStream din, Throwable e) {
        Iris.error("Failed to read chunk, creating a new chunk instead.");
        Iris.addPanic("read.byte.range", start + " " + end);
        Iris.addPanic("read.byte.current", din.count() + "");
        Iris.reportError(e);
        e.printStackTrace();
        Iris.panic();
    }

    @Override
    protected MantleChunk readChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        return new MantleChunk(version, sectionHeight, din);
    }

    @Override
    protected MantleChunk createChunk(int sectionHeight, int x, int z) {
        return new MantleChunk(sectionHeight, x, z);
    }

    @Override
    protected boolean isChunkInUse(MantleChunk chunk) {
        return chunk.inUse();
    }

    @Override
    protected void closeChunk(MantleChunk chunk) {
        chunk.close();
    }

    @Override
    protected void writeChunk(MantleChunk chunk, DataOutputStream dos) throws IOException {
        chunk.write(dos);
    }

    public static void addError() {
        art.arcane.volmlib.util.mantle.TectonicPlate.addError();
    }

    public static boolean hasError() {
        return art.arcane.volmlib.util.mantle.TectonicPlate.hasError();
    }
}
