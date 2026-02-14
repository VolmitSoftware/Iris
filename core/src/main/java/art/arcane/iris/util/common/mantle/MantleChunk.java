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

import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.documentation.ChunkRelativeBlockCoordinates;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.iris.Iris;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.util.matter.IrisMatter;
import art.arcane.iris.util.matter.Matter;
import art.arcane.iris.util.matter.MatterSlice;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a mantle chunk. Mantle chunks contain sections of matter (see matter api)
 * Mantle Chunks are fully atomic & thread safe
 */
public class MantleChunk extends art.arcane.volmlib.util.mantle.MantleChunk<Matter> {
    @ChunkCoordinates
    public MantleChunk(int sectionHeight, int x, int z) {
        super(sectionHeight, x, z);
    }

    public MantleChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException {
        super(version, sectionHeight, din);
    }

    @Override
    protected void onBeforeReadSection(int index) {
        Iris.addPanic("read.section", "Section[" + index + "]");
    }

    @Override
    protected void onReadSectionFailure(int index, long start, long end, CountingDataInputStream din, IOException e) {
        Iris.error("Failed to read chunk section, skipping it.");
        Iris.addPanic("read.byte.range", start + " " + end);
        Iris.addPanic("read.byte.current", din.count() + "");
        Iris.reportError(e);
        e.printStackTrace();
        Iris.panic();
        TectonicPlate.addError();
    }

    @Override
    protected Matter createSection() {
        return new IrisMatter(16, 16, 16);
    }

    @Override
    protected Matter readSection(CountingDataInputStream din) throws IOException {
        return Matter.readDin(din);
    }

    @Override
    protected void writeSection(Matter section, DataOutputStream dos) throws IOException {
        section.writeDos(dos);
    }

    @Override
    protected void trimSection(Matter section) {
        section.trimSlices();
    }

    @Override
    protected boolean isSectionEmpty(Matter section) {
        return section.getSliceMap().isEmpty();
    }

    @Override
    public MantleChunk use() {
        super.use();
        return this;
    }

    public void copyFrom(MantleChunk chunk) {
        super.copyFrom(chunk);
    }

    @Nullable
    @ChunkRelativeBlockCoordinates
    @SuppressWarnings("unchecked")
    public <T> T get(int x, int y, int z, Class<T> type) {
        return (T) getOrCreate(y >> 4)
                .slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        for (int i = 0; i < sectionCount(); i++) {
            int bs = (i << 4);
            Matter matter = get(i);

            if (matter != null) {
                MatterSlice<T> t = matter.getSlice(type);

                if (t != null) {
                    t.iterateSync((a, b, c, f) -> iterator.accept(a, b + bs, c, f));
                }
            }
        }
    }

    public void deleteSlices(Class<?> c) {
        if (IrisToolbelt.isRetainingMantleDataForSlice(c.getCanonicalName())) {
            return;
        }

        for (int i = 0; i < sectionCount(); i++) {
            Matter m = get(i);
            if (m != null && m.hasSlice(c)) {
                m.deleteSlice(c);
            }
        }
    }

    public void trimSlices() {
        trimSections();
    }
}
