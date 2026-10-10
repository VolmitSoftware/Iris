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

import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.mantle.BlockBox;
import com.volmit.world.BiomeSample;
import com.volmit.world.TerrainSample;
import com.volmit.world.TerrainSampler;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Iris's terrain streams for one invocation. The streams are computed, not prepared, so the bounds are
 * only the area the invocation may touch: a chunk or a placement widened by the pipeline's reach, at
 * full height. Heights, in the bounds and in the samples, are above the dimension's bottom, as the engine's
 * streams give them.
 */
final class IrisTerrainSampler implements TerrainSampler {
    private static final int CHUNK_WIDTH_BLOCKS = 16;

    private final Engine engine;
    private final PackResourceIds ids;
    private final BlockBox bounds;

    /** The chunk at {@code originX}, {@code originZ} widened by {@code reachBlocks}, over the full world height. */
    IrisTerrainSampler(Engine engine, PackResourceIds ids, int originX, int originZ, int reachBlocks) {
        this(engine, ids, originX - reachBlocks, originZ - reachBlocks,
                originX + CHUNK_WIDTH_BLOCKS + reachBlocks, originZ + CHUNK_WIDTH_BLOCKS + reachBlocks);
    }

    /** Columns from the minima, inclusive, to the maxima, exclusive, over the full world height. */
    IrisTerrainSampler(Engine engine, PackResourceIds ids, int minX, int minZ, int maxX, int maxZ) {
        this.engine = engine;
        this.ids = ids;
        this.bounds = ApiHeights.columns(engine, minX, minZ, maxX, maxZ);
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public TerrainSample column(int worldX, int worldZ) {
        requireInside(worldX, bounds.minY(), worldZ);
        double surfaceY = engine.getComplex().getHeightStream().get(worldX, worldZ);
        double slope = Math.abs(engine.getComplex().getSlopeStream().get(worldX, worldZ));
        int fluidHeight = engine.getDimension().getFluidHeight();
        OptionalInt fluidY = surfaceY < fluidHeight ? OptionalInt.of(fluidHeight) : OptionalInt.empty();
        return new TerrainSample(surfaceY, fluidY, slope);
    }

    @Override
    public double surfaceY(int worldX, int worldZ) {
        requireInside(worldX, bounds.minY(), worldZ);
        return engine.getComplex().getHeightStream().get(worldX, worldZ);
    }

    @Override
    public BiomeSample biome(int worldX, int y, int worldZ) {
        requireInside(worldX, y, worldZ);
        IrisBiome surface = engine.getSurfaceBiome(worldX, worldZ);
        Optional<IrisBiome> cave = Optional.ofNullable(engine.getCaveBiome(worldX, y, worldZ));
        return new BiomeSample(
                ids.of(engine.getRegion(worldX, worldZ).getLoadKey()),
                ids.of(surface.getLoadKey()),
                cave.map(biome -> ids.of(biome.getLoadKey())));
    }

    @Override
    public int fluidHeight() {
        return engine.getDimension().getFluidHeight();
    }

    /** The upper dimension's surface, but never closer above the terrain than the dimension's gap. */
    @Override
    public OptionalInt upperDimensionY(int worldX, int worldZ) {
        requireInside(worldX, bounds.minY(), worldZ);
        UpperDimensionContext upper = engine.getUpperContext();
        if (upper == null) {
            return OptionalInt.empty();
        }
        int surfaceY = (int) Math.round(engine.getComplex().getHeightStream().get((double) worldX, (double) worldZ));
        return OptionalInt.of(Math.max(upper.getUpperSurfaceY(worldX, worldZ), surfaceY + engine.getDimension().getUpperDimensionGap()));
    }

    private void requireInside(int worldX, int y, int worldZ) {
        if (!bounds.contains(worldX, y, worldZ)) {
            throw new IndexOutOfBoundsException("Block " + worldX + ", " + y + ", " + worldZ + " is outside the sampled area " + bounds);
        }
    }
}
