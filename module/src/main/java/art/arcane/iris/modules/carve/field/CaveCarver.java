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
import art.arcane.iris.modules.carve.CaveDepth;
import art.arcane.iris.modules.carve.CaveValue;
import art.arcane.iris.modules.carve.field.CaveLimits.Columns;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One cave of a pack, ready to carve chunks of one world: its shapes with their noise built for the world's seed, and
 * the heights it may carve at. Heights count from the dimension's bottom. Safe to use from several threads at once.
 */
public final class CaveCarver {
    /** Caves stay this far above the dimension's bottom, clear of the bedrock floor. */
    private static final int ROCK_KEPT_ABOVE_BOTTOM_BLOCKS = 5;

    private final List<PartShape> shapes = new ArrayList<>();
    private final int worldBottomY;
    private final int worldHeight;
    private final ValueField depthMin;
    private final ValueField depthMax;
    private final ValueField fade;
    private final ValueField surfaceClearance;
    private final ValueField ceilingFade;
    private final boolean opensToSurface;

    /**
     * @param seed          the seed of this cave in this world; two caves with the same settings and seed carve the same
     * @param worldBottomY  world Y of the dimension's lowest block
     * @param worldHeight   the dimension's height in blocks
     */
    public CaveCarver(Cave cave, NoiseFactory noise, long seed, int worldBottomY, int worldHeight) {
        CaveNoise caveNoise = new CaveNoise(noise, seed);
        this.worldBottomY = worldBottomY;
        this.worldHeight = worldHeight;
        CaveDepth depth = cave.depth().orElse(null);
        this.depthMin = depth == null ? ValueField.constant(worldBottomY) : caveValue(depth.min(), caveNoise, "depth.min");
        this.depthMax = depth == null ? ValueField.constant(worldBottomY + worldHeight - 1) : caveValue(depth.max(), caveNoise, "depth.max");
        this.fade = caveValue(cave.fade(), caveNoise, "fade");
        this.surfaceClearance = caveValue(cave.surfaceClearance(), caveNoise, "surfaceClearance");
        this.ceilingFade = caveValue(cave.ceilingFade(), caveNoise, "ceilingFade");
        this.opensToSurface = cave.opensToSurface();
        cave.tunnel().ifPresent(tunnel -> shapes.add(new PartShape(CavePart.TUNNEL, new TunnelShape(caveNoise, tunnel))));
        cave.cavern().ifPresent(cavern -> shapes.add(new PartShape(CavePart.CAVERN, new CavernShape(caveNoise, cavern))));
    }

    private static ValueField caveValue(CaveValue value, CaveNoise caveNoise, String name) {
        return ValueField.inColumns(value, caveNoise, name, Cave.VALUE_CHANGE_BLOCKS);
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
        if (shapes.isEmpty()) {
            return;
        }
        CaveLimits limits = limitsOf(originX, originZ, surfaceY, columnWeight, sealed);
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

    private CaveLimits limitsOf(int originX, int originZ, int[] surfaceY, double[] columnWeight, boolean[] sealed) {
        Columns columns = Columns.of(columnWeight);
        fillBlocks(fade, originX, originZ, columns.fadeBlocks());
        fillBlocks(ceilingFade, originX, originZ, columns.ceilingFadeBlocks());
        boolean depthVaries = depthMin.varies() || depthMax.varies();
        int constantClearance = surfaceClearanceAt(originX, originZ);
        int constantDepthMinY = depthMinYAt(originX, originZ);
        int constantDepthMaxY = depthMaxYAt(originX, originZ);
        for (int column = 0; column < CaveLimits.CHUNK_COLUMNS; column++) {
            double x = originX + (column >> 4);
            double z = originZ + (column & 15);
            columns.depthMinY()[column] = depthVaries ? depthMinYAt(x, z) : constantDepthMinY;
            columns.depthMaxY()[column] = depthVaries ? depthMaxYAt(x, z) : constantDepthMaxY;
            boolean opens = opensToSurface && !sealed[column];
            columns.opensToSurface()[column] = opens;
            int clearance = surfaceClearance.varies() ? surfaceClearanceAt(x, z) : constantClearance;
            columns.topY()[column] = opens ? surfaceY[column] : surfaceY[column] - clearance;
        }
        return new CaveLimits(columns);
    }

    /** Fills {@code blocks} with the value in each column of the chunk, at least 0. */
    private static void fillBlocks(ValueField value, int originX, int originZ, double[] blocks) {
        if (!value.varies()) {
            Arrays.fill(blocks, Math.max(0D, value.min()));
            return;
        }
        for (int column = 0; column < CaveLimits.CHUNK_COLUMNS; column++) {
            blocks[column] = Math.max(0D, value.at(originX + (column >> 4), originZ + (column & 15)));
        }
    }

    private int depthMinYAt(double x, double z) {
        double lowest = Math.min(depthMin.at(x, z), depthMax.at(x, z));
        return Math.max(ROCK_KEPT_ABOVE_BOTTOM_BLOCKS, (int) Math.floor(lowest) - worldBottomY);
    }

    private int depthMaxYAt(double x, double z) {
        double highest = Math.max(depthMin.at(x, z), depthMax.at(x, z));
        return Math.min(worldHeight - 1, (int) Math.ceil(highest) - worldBottomY);
    }

    private int surfaceClearanceAt(double x, double z) {
        return (int) Math.round(Math.max(0D, surfaceClearance.at(x, z)));
    }
}
