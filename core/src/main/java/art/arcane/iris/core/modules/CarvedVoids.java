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

package art.arcane.iris.core.modules;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;
import com.volmit.resource.ResourceId;

import java.util.Objects;

/**
 * The voids Iris's mantle holds for chunks to take over, IrisApi's {@link com.volmit.mantle.Mantle#carved()}, over the
 * chunks one invocation may touch. Iris stores them as the cavern slice of the mantle ({@link MatterCavern}), which its
 * own readers (the cave floors, the structure anchors, the biome pass) read as it is; this layer translates at the edge:
 * a cavern's liquid byte is the void's fill, and a custom biome key is a pack biome id. A cavern of any other liquid
 * reads as {@link CarvedVoid#OPEN}.
 *
 * <p>The carver calls {@link #set} for thousands of blocks of one chunk, so the layer remembers the slices of the chunks
 * it touched last and the voids it writes are the shared constants. A view belongs to one invocation and one thread, as
 * IrisApi's mantle does.
 */
final class CarvedVoids implements MantleLayer<CarvedVoid> {
    static final byte LIQUID_OPEN = 0;
    static final byte LIQUID_FLUID = 1;
    static final byte LIQUID_LAVA = 2;
    static final byte LIQUID_AIR = 3;

    private static final MatterCavern STORED_OPEN = new MatterCavern(true, "", LIQUID_OPEN);
    private static final MatterCavern STORED_FLUID = new MatterCavern(true, "", LIQUID_FLUID);
    private static final MatterCavern STORED_LAVA = new MatterCavern(true, "", LIQUID_LAVA);
    private static final MatterCavern STORED_AIR = new MatterCavern(true, "", LIQUID_AIR);

    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;
    private static final int RECENT_CHUNKS = 8;
    private static final String READ_ONLY = "This view only reads the carved voids; mantle stages write them on the mantle itself";

    private final BlockBox bounds;
    private final IrisModuleMantle.Chunks chunks;
    private final PackResourceIds ids;
    private final boolean writable;
    private final ChunkSlices[] recent = new ChunkSlices[RECENT_CHUNKS];
    private final CarvedCells.Scratch gridScratch = new CarvedCells.Scratch();

    CarvedVoids(BlockBox bounds, IrisModuleMantle.Chunks chunks, PackResourceIds ids, boolean writable) {
        this.bounds = bounds;
        this.chunks = chunks;
        this.ids = ids;
        this.writable = writable;
    }

    @Override
    public CarvedVoid get(int x, int y, int z) {
        requireInside(x, y, z);
        MatterSlice<MatterCavern> slice = slicesAt(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT).existing(y >> SECTION_SHIFT);
        if (slice == null) {
            return null;
        }
        MatterCavern cavern = slice.get(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK);
        return cavern == null ? null : toApi(cavern);
    }

    @Override
    public void set(int x, int y, int z, CarvedVoid value) {
        requireWritable();
        Objects.requireNonNull(value, "value");
        requireInside(x, y, z);
        slicesAt(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT).creating(y >> SECTION_SHIFT)
                .set(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK, toStored(value));
    }

    @Override
    public void remove(int x, int y, int z) {
        requireWritable();
        requireInside(x, y, z);
        MatterSlice<MatterCavern> slice = slicesAt(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT).existing(y >> SECTION_SHIFT);
        if (slice != null) {
            slice.set(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK, null);
        }
    }

    @Override
    public void visit(BlockBox area, MantleVisitor<CarvedVoid> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        requireInside(area);
        collect(area).forEachInOrder((relativeX, relativeY, relativeZ, carvedVoid) ->
                visitor.accept(area.minX() + relativeX, area.minY() + relativeY, area.minZ() + relativeZ, carvedVoid));
    }

    @Override
    public void clear(BlockBox area) {
        requireWritable();
        requireInside(area);
        collect(area).forEachInOrder((relativeX, relativeY, relativeZ, carvedVoid) ->
                remove(area.minX() + relativeX, area.minY() + relativeY, area.minZ() + relativeZ));
    }

    private CarvedCells collect(BlockBox area) {
        CarvedCells collected = new CarvedCells(area, gridScratch);
        try {
            collectChunks(area, collected);
        } catch (RuntimeException failure) {
            collected.release();
            throw failure;
        }
        return collected;
    }

    private void collectChunks(BlockBox area, CarvedCells collected) {
        int firstSection = area.minY() >> SECTION_SHIFT;
        int lastSection = (area.maxY() - 1) >> SECTION_SHIFT;
        for (int chunkX = area.minX() >> CHUNK_SHIFT; chunkX <= (area.maxX() - 1) >> CHUNK_SHIFT; chunkX++) {
            for (int chunkZ = area.minZ() >> CHUNK_SHIFT; chunkZ <= (area.maxZ() - 1) >> CHUNK_SHIFT; chunkZ++) {
                ChunkSlices slices = slicesAt(chunkX, chunkZ);
                for (int section = firstSection; section <= lastSection; section++) {
                    collectSection(slices.existing(section), chunkX << CHUNK_SHIFT, section << SECTION_SHIFT, chunkZ << CHUNK_SHIFT, area, collected);
                }
            }
        }
    }

    private void collectSection(MatterSlice<MatterCavern> slice, int originX, int originY, int originZ, BlockBox area, CarvedCells collected) {
        if (slice == null) {
            return;
        }
        slice.iterateSync((localX, localY, localZ, cavern) -> {
            int x = originX + localX;
            int y = originY + localY;
            int z = originZ + localZ;
            if (cavern != null && area.contains(x, y, z)) {
                collected.add(x - area.minX(), y - area.minY(), z - area.minZ(), toApi(cavern));
            }
        });
    }

    private ChunkSlices slicesAt(int chunkX, int chunkZ) {
        int slot = (chunkX * 31 + chunkZ) & (RECENT_CHUNKS - 1);
        ChunkSlices known = recent[slot];
        if (known != null && known.chunkX == chunkX && known.chunkZ == chunkZ) {
            return known;
        }
        MantleChunk<Matter> chunk = chunks.chunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("Mantle chunk " + chunkX + ", " + chunkZ + " is inside " + bounds + " but was not prepared");
        }
        ChunkSlices created = new ChunkSlices(chunkX, chunkZ, chunk);
        recent[slot] = created;
        return created;
    }

    private CarvedVoid toApi(MatterCavern cavern) {
        CarvedVoid plain = switch (cavern.getLiquid()) {
            case LIQUID_FLUID -> CarvedVoid.FLUID;
            case LIQUID_LAVA -> CarvedVoid.LAVA;
            case LIQUID_AIR -> CarvedVoid.AIR;
            default -> CarvedVoid.OPEN;
        };
        String biomeKey = cavern.getCustomBiome();
        return biomeKey == null || biomeKey.isEmpty() ? plain : plain.withBiome(ids.of(biomeKey));
    }

    private MatterCavern toStored(CarvedVoid value) {
        if (value == CarvedVoid.OPEN) {
            return STORED_OPEN;
        }
        if (value == CarvedVoid.FLUID) {
            return STORED_FLUID;
        }
        if (value == CarvedVoid.LAVA) {
            return STORED_LAVA;
        }
        if (value == CarvedVoid.AIR) {
            return STORED_AIR;
        }
        return new MatterCavern(true, value.biome().map(this::biomeKey).orElse(""), liquidOf(value.fill()));
    }

    private static byte liquidOf(CarvedVoid.Fill fill) {
        return switch (fill) {
            case OPEN -> LIQUID_OPEN;
            case FLUID -> LIQUID_FLUID;
            case LAVA -> LIQUID_LAVA;
            case AIR -> LIQUID_AIR;
        };
    }

    /** The key Iris's biome loader knows the biome by: its path inside this pack, or the whole id for another namespace's. */
    private String biomeKey(ResourceId biome) {
        return biome.namespace().equals(ids.namespace()) ? biome.path() : biome.toString();
    }

    private void requireWritable() {
        if (!writable) {
            throw new UnsupportedOperationException(READ_ONLY);
        }
    }

    private void requireInside(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the mantle this invocation prepared, " + bounds);
        }
    }

    private void requireInside(BlockBox area) {
        Objects.requireNonNull(area, "area");
        if (!bounds.contains(area)) {
            throw new IndexOutOfBoundsException("Area " + area + " is not inside the mantle this invocation prepared, " + bounds);
        }
    }

    /** The cavern slices of one mantle chunk's sections, found once. */
    private static final class ChunkSlices {
        private final int chunkX;
        private final int chunkZ;
        private final MantleChunk<Matter> chunk;
        private final MatterSlice<?>[] bySection;

        ChunkSlices(int chunkX, int chunkZ, MantleChunk<Matter> chunk) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunk = chunk;
            this.bySection = new MatterSlice<?>[chunk.sectionCount()];
        }

        /** The section's cavern slice, or null when it has none; nothing is created. */
        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> existing(int section) {
            MatterSlice<MatterCavern> known = (MatterSlice<MatterCavern>) bySection[section];
            if (known != null) {
                return known;
            }
            if (!chunk.exists(section)) {
                return null;
            }
            Matter matter = chunk.get(section);
            if (matter == null || !matter.hasSlice(MatterCavern.class)) {
                return null;
            }
            MatterSlice<MatterCavern> found = matter.getSlice(MatterCavern.class);
            bySection[section] = found;
            return found;
        }

        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> creating(int section) {
            MatterSlice<MatterCavern> known = (MatterSlice<MatterCavern>) bySection[section];
            if (known != null) {
                return known;
            }
            MatterSlice<MatterCavern> created = chunk.getOrCreate(section).slice(MatterCavern.class);
            bySection[section] = created;
            return created;
        }
    }
}
