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

package art.arcane.iris.modules.carve.field;

import art.arcane.iris.modules.carve.Cave;
import com.volmit.config.Range;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;

/**
 * One cave of a pack, ready to carve chunks of one world: its shapes with their noise built for the world's seed, and
 * the heights it may carve at. Heights count from the dimension's bottom. Safe to use from several threads at once.
 */
public final class CaveCarver {
    private static final int CHUNK_COLUMNS = 256;
    /** Caves stay this far above the dimension's bottom, clear of the bedrock floor. */
    private static final int ROCK_KEPT_ABOVE_BOTTOM_BLOCKS = 5;

    private final List<PartShape> shapes = new ArrayList<>();
    private final int depthMinY;
    private final int depthMaxY;
    private final int fadeBlocks;
    private final int surfaceClearance;
    private final boolean opensToSurface;

    /**
     * @param seed          the seed of this cave in this world; two caves with the same settings and seed carve the same
     * @param worldBottomY  world Y of the dimension's lowest block
     * @param worldHeight   the dimension's height in blocks
     */
    public CaveCarver(Cave cave, NoiseFactory noise, long seed, int worldBottomY, int worldHeight) {
        CaveNoise caveNoise = new CaveNoise(noise, seed);
        cave.tunnel().ifPresent(tunnel -> shapes.add(new PartShape(CavePart.TUNNEL, new TunnelShape(caveNoise, tunnel))));
        cave.cavern().ifPresent(cavern -> shapes.add(new PartShape(CavePart.CAVERN, new CavernShape(caveNoise, cavern))));
        Range depth = cave.depth().orElse(null);
        int lowest = depth == null ? 0 : (int) Math.floor(Math.min(depth.min(), depth.max())) - worldBottomY;
        int highest = depth == null ? worldHeight - 1 : (int) Math.ceil(Math.max(depth.min(), depth.max())) - worldBottomY;
        this.depthMinY = Math.max(ROCK_KEPT_ABOVE_BOTTOM_BLOCKS, lowest);
        this.depthMaxY = Math.min(worldHeight - 1, highest);
        this.fadeBlocks = Math.max(0, cave.fade());
        this.surfaceClearance = Math.max(0, cave.surfaceClearance());
        this.opensToSurface = cave.opensToSurface();
    }

    /** The seed the cave with id {@code cave} carves with in a world whose carve seed is {@code carveSeed}. */
    public static long seedOf(long carveSeed, ResourceId cave) {
        return carveSeed + (31L * cave.toString().hashCode());
    }

    public boolean opensToSurface() {
        return opensToSurface;
    }

    /** Which parts the cave has, in the order it carves them. */
    public List<CavePart> parts() {
        return shapes.stream().map(PartShape::part).toList();
    }

    /** {@link #carve}, telling the sink which part carved each block. A block two parts carve is reported for each. */
    public void carveByPart(int originX, int originZ, int[] surfaceY, double[] columnWeight, boolean[] sealed, PartSink sink) {
        if (shapes.isEmpty() || depthMaxY < depthMinY) {
            return;
        }
        CaveLimits limits = limitsOf(surfaceY, columnWeight, sealed);
        if (limits.carvesNothing()) {
            return;
        }
        ChunkLattice lattice = ChunkLattice.covering(originX, originZ, limits.lowestY(), limits.highestY());
        for (PartShape shape : shapes) {
            shape.shape().carve(lattice, limits, (localX, y, localZ) -> sink.carve(shape.part(), localX, y, localZ));
        }
    }

    /** Takes the blocks a cave carves, with the part that carved each. */
    @FunctionalInterface
    public interface PartSink {
        void carve(CavePart part, int localX, int y, int localZ);
    }

    private record PartShape(CavePart part, LatticeShape shape) {
    }

    /**
     * Carves the chunk whose first block is {@code originX}, {@code originZ}. Columns are indexed as
     * {@code (localX << 4) | localZ}.
     *
     * @param surfaceY     the height of the surface in each column
     * @param columnWeight how much each column lists this cave, from 0 to 1
     * @param sealed       columns whose surface the cave must not cut through, even if it opens to the surface
     */
    public void carve(int originX, int originZ, int[] surfaceY, double[] columnWeight, boolean[] sealed, CarveSink sink) {
        carveByPart(originX, originZ, surfaceY, columnWeight, sealed, (part, localX, y, localZ) -> sink.carve(localX, y, localZ));
    }

    private CaveLimits limitsOf(int[] surfaceY, double[] columnWeight, boolean[] sealed) {
        int[] columnTopY = new int[CHUNK_COLUMNS];
        boolean[] columnOpens = new boolean[CHUNK_COLUMNS];
        for (int column = 0; column < CHUNK_COLUMNS; column++) {
            columnOpens[column] = opensToSurface && !sealed[column];
            columnTopY[column] = columnOpens[column] ? surfaceY[column] : surfaceY[column] - surfaceClearance;
        }
        return new CaveLimits(depthMinY, depthMaxY, fadeBlocks, columnTopY, columnOpens, columnWeight);
    }
}
