/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeSectionVolume;
import art.arcane.volmlib.util.hunk.Hunk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

public final class SectionBuffer implements Hunk<NativeBlockState>, NativeSectionVolume, ColumnExtent, ColumnRunHunk<NativeBlockState> {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");
    private static final VarHandle SECTIONS = MethodHandles.arrayElementVarHandle(short[][].class);
    private static final VarHandle COLUMN_TOPS = MethodHandles.arrayElementVarHandle(int[].class);
    private static final int SECTION_CELLS = 4096;
    private static final int MAX_PALETTE_ID = 0xFFFF;
    private static final int INITIAL_PALETTE_CAPACITY = 64;
    private static final int COLUMN_SLICE_SHIFT = 5;

    private final int height;
    private final short[][] sections;
    private final int[] columnTops;
    private final Object paletteLock = new Object();
    private PaletteEntry[] slots;
    private NativeBlockState[] states;
    private int paletteSize;

    public SectionBuffer(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("Unsupported section buffer height " + height);
        }
        this.height = height;
        this.sections = new short[(height + 15) >> 4][];
        this.columnTops = new int[16 << COLUMN_SLICE_SHIFT];
        Arrays.fill(columnTops, -1);
        this.slots = new PaletteEntry[INITIAL_PALETTE_CAPACITY << 1];
        this.states = new NativeBlockState[INITIAL_PALETTE_CAPACITY];
        this.paletteSize = 1;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    public int paletteId(NativeBlockState state) {
        if (state == null) {
            return 0;
        }
        PaletteEntry[] table = slots;
        int mask = table.length - 1;
        int slot = System.identityHashCode(state) & mask;
        PaletteEntry entry;
        while ((entry = table[slot]) != null) {
            if (entry.state == state) {
                return entry.id;
            }
            slot = (slot + 1) & mask;
        }
        return insert(state);
    }

    @Override
    public void setRaw(int x, int y, int z, NativeBlockState t) {
        if (t == null) {
            return;
        }
        writableSection(y >> 4)[((y & 15) << 8) | (z << 4) | x] = (short) paletteId(t);
        raiseTop((x << COLUMN_SLICE_SHIFT) | z, y);
    }

    @Override
    public NativeBlockState getRaw(int x, int y, int z) {
        if (y < 0 || y >= height) {
            return AIR.get();
        }
        short[] section = sections[y >> 4];
        if (section != null) {
            int id = section[((y & 15) << 8) | (z << 4) | x] & 0xFFFF;
            if (id != 0) {
                return state(id);
            }
        }
        return AIR.get();
    }

    @Override
    public NativeBlockState getStoredRaw(int x, int y, int z) {
        if (y < 0 || y >= height) {
            return null;
        }
        short[] section = sections[y >> 4];
        if (section == null) {
            return null;
        }
        int id = section[((y & 15) << 8) | (z << 4) | x] & 0xFFFF;
        return id == 0 ? null : state(id);
    }

    @Override
    public void fillColumnRun(int x, int z, int yFromInclusive, int yToInclusive, NativeBlockState value) {
        if (value == null || yToInclusive < yFromInclusive) {
            return;
        }
        fillColumnRun(x, z, yFromInclusive, yToInclusive, paletteId(value));
    }

    public void fillColumnRun(int localX, int localZ, int yFromInclusive, int yToInclusive, int paletteId) {
        if (paletteId == 0 || yToInclusive < yFromInclusive) {
            return;
        }
        short value = (short) paletteId;
        int column = (localZ << 4) | localX;
        int y = yFromInclusive;
        while (y <= yToInclusive) {
            int sectionIndex = y >> 4;
            short[] section = writableSection(sectionIndex);
            int last = Math.min(yToInclusive, (sectionIndex << 4) | 15);
            int end = ((last & 15) << 8) | column;
            for (int index = ((y & 15) << 8) | column; index <= end; index += 256) {
                section[index] = value;
            }
            y = last + 1;
        }
        raiseTop((localX << COLUMN_SLICE_SHIFT) | localZ, yToInclusive);
    }

    @Override
    public int highestStoredY(int x, int z) {
        return (int) COLUMN_TOPS.getOpaque(columnTops, (x << COLUMN_SLICE_SHIFT) | z);
    }

    @Override
    public int sectionCount() {
        return sections.length;
    }

    @Override
    public short[] section(int sectionIndex) {
        return sections[sectionIndex];
    }

    @Override
    public NativeBlockState[] palette() {
        synchronized (paletteLock) {
            return Arrays.copyOf(states, paletteSize);
        }
    }

    private short[] writableSection(int sectionIndex) {
        short[] section = sections[sectionIndex];
        if (section != null) {
            return section;
        }
        short[] created = new short[SECTION_CELLS];
        short[] witness = (short[]) SECTIONS.compareAndExchange(sections, sectionIndex, (short[]) null, created);
        return witness == null ? created : witness;
    }

    private void raiseTop(int column, int y) {
        int top = (int) COLUMN_TOPS.getOpaque(columnTops, column);
        while (y > top) {
            int witness = (int) COLUMN_TOPS.compareAndExchange(columnTops, column, top, y);
            if (witness == top) {
                return;
            }
            top = witness;
        }
    }

    private NativeBlockState state(int id) {
        NativeBlockState[] current = states;
        if (id < current.length) {
            NativeBlockState state = current[id];
            if (state != null) {
                return state;
            }
        }
        synchronized (paletteLock) {
            return states[id];
        }
    }

    private int insert(NativeBlockState state) {
        synchronized (paletteLock) {
            PaletteEntry[] table = slots;
            int mask = table.length - 1;
            int slot = System.identityHashCode(state) & mask;
            PaletteEntry entry;
            while ((entry = table[slot]) != null) {
                if (entry.state == state) {
                    return entry.id;
                }
                slot = (slot + 1) & mask;
            }
            int id = paletteSize;
            if (id > MAX_PALETTE_ID) {
                throw new IllegalStateException("A chunk wrote more than " + MAX_PALETTE_ID + " distinct block state instances");
            }
            NativeBlockState[] current = states;
            if (id == current.length) {
                current = Arrays.copyOf(current, current.length << 1);
            }
            current[id] = state;
            states = current;
            paletteSize = id + 1;
            PaletteEntry created = new PaletteEntry(state, id);
            if (id << 1 >= table.length) {
                slots = rehash(table, created);
            } else {
                table[slot] = created;
            }
            return id;
        }
    }

    private static PaletteEntry[] rehash(PaletteEntry[] table, PaletteEntry created) {
        PaletteEntry[] grown = new PaletteEntry[table.length << 1];
        for (PaletteEntry entry : table) {
            if (entry != null) {
                place(grown, entry);
            }
        }
        place(grown, created);
        return grown;
    }

    private static void place(PaletteEntry[] table, PaletteEntry entry) {
        int mask = table.length - 1;
        int slot = System.identityHashCode(entry.state) & mask;
        while (table[slot] != null) {
            slot = (slot + 1) & mask;
        }
        table[slot] = entry;
    }

    private record PaletteEntry(NativeBlockState state, int id) {
    }
}
