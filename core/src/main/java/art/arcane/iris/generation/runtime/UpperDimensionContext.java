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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.volmlib.util.stream.ProceduralStream;

public class UpperDimensionContext implements DataProvider {
    private final DimensionTerrainContext terrainContext;
    private final ProceduralStream<Double> lowerHeightStream;
    private final CeilingLayout ceilingLayout;

    private UpperDimensionContext(DimensionTerrainContext terrainContext, Engine engine) {
        this.terrainContext = terrainContext;
        IrisComplex complex = engine.getComplex();
        lowerHeightStream = complex.getHeightStream();
        ceilingLayout = new CeilingLayout(engine.getHeight(), engine.getDimension().getUpperDimensionGap());
    }

    public static UpperDimensionContext create(Engine engine, IrisDimension upperDimension) {
        return new UpperDimensionContext(
                DimensionTerrainContext.forUpper(engine, upperDimension),
                engine
        );
    }

    static IrisRegion mappedRegion(
            IrisImageMapRuntime imageMapRuntime,
            IrisRegion proceduralRegion,
            double worldX,
            double worldZ
    ) {
        return DimensionTerrainContext.mappedRegion(
                imageMapRuntime, proceduralRegion, worldX, worldZ);
    }

    static double mappedTerrainHeight(
            IrisImageMapRuntime imageMapRuntime,
            double proceduralHeight,
            double worldX,
            double worldZ
    ) {
        return DimensionTerrainContext.mappedTerrainHeight(
                imageMapRuntime, proceduralHeight, worldX, worldZ);
    }

    static IrisBiome mappedBiome(
            IrisImageMapRuntime imageMapRuntime,
            IrisBiome proceduralBiome,
            double worldX,
            double worldZ
    ) {
        return DimensionTerrainContext.mappedBiome(
                imageMapRuntime, proceduralBiome, worldX, worldZ);
    }

    static PlatformBlockState mappedSurfaceBlock(
            IrisImageMapRuntime imageMapRuntime,
            PlatformBlockState proceduralBlock,
            double worldX,
            double worldZ
    ) {
        return DimensionTerrainContext.mappedSurfaceBlock(
                imageMapRuntime, proceduralBlock, worldX, worldZ);
    }

    public int getUpperSurfaceY(int x, int z) {
        return ceilingLayout.height() - 1
                - (int) Math.round(terrainContext.getNormalTerrainHeight(x, z));
    }

    public int getEffectiveSurfaceY(int x, int z) {
        int surfaceY = effectiveEnvelopeY(x, z);
        Terrain3DColumn column = terrainContext.terrainColumn(x, z);
        return clipSurfaceY(column, ceilingLayout.height(), surfaceY);
    }

    public Column sampleColumn(int x, int z) {
        Terrain3DColumn column = terrainContext.terrainColumn(x, z);
        return new Column(ceilingLayout.height(),
                clipSurfaceY(column, ceilingLayout.height(), effectiveEnvelopeY(x, z)), column);
    }

    private static int clipSurfaceY(Terrain3DColumn column, int height, int surfaceY) {
        if (column == null || surfaceY >= height) {
            return surfaceY;
        }
        int top = column.highestSolidY(height - 1 - surfaceY);
        return top <= 0 ? height : height - 1 - top;
    }

    private int effectiveEnvelopeY(int x, int z) {
        double depth = Math.max(
                0D,
                Math.min(ceilingLayout.height() - 1D, terrainContext.getNormalTerrainHeight(x, z))
        );
        int lowerSurfaceY = (int) Math.min(
                ceilingLayout.height(),
                Math.round(lowerHeightStream.getDouble(x, z))
        );
        return effectiveSurfaceY(depth, ceilingLayout, lowerSurfaceY);
    }

    static int effectiveSurfaceY(double depth, CeilingLayout layout, int lowerSurfaceY) {
        long mirroredSurface = layout.height() - 1L - Math.round(depth);
        long gapSurface = (long) lowerSurfaceY + layout.minimumGap();
        long surface = Math.max(0L, Math.max(mirroredSurface, gapSurface));
        return surface >= layout.height() - 1L ? layout.height() : (int) surface;
    }

    public IrisBiome getUpperBiome(int x, int z) {
        return terrainContext.getBiome(x, z);
    }

    public IrisRegion getUpperRegion(int x, int z) {
        return terrainContext.getRegion(x, z);
    }

    public PlatformBlockState getRockBlock(int x, int z) {
        return terrainContext.getRockBlock(x, z);
    }

    public PlatformBlockState getSurfaceBlock(int x, int z) {
        return terrainContext.getSurfaceBlock(x, z);
    }

    public ProceduralStream<Double> getSurfaceSlopeStream(int sourceY) {
        return terrainContext.getSurfaceSlopeStream(sourceY);
    }

    public IrisDimension getDimension() {
        return terrainContext.getDimension();
    }

    @Override
    public IrisData getData() {
        return terrainContext.getData();
    }

    public boolean isSelfReferencing() {
        return terrainContext.isSelfReferencing();
    }

    public record Column(int height, int surfaceY, Terrain3DColumn terrain) {
        public boolean ownsY(int y) {
            return y >= surfaceY && y < height;
        }

        public boolean isSolid(int y) {
            return ownsY(y) && (terrain == null || terrain.isSolid(height - 1 - y));
        }

        public int faceY(int y) {
            if (!isSolid(y)) {
                return -1;
            }
            return terrain == null ? surfaceY
                    : Math.max(surfaceY, height - 1 - terrain.surfaceY(height - 1 - y));
        }
    }

    record CeilingLayout(int height, int minimumGap) {
        CeilingLayout {
            if (height <= 0 || minimumGap < 0) {
                throw new IllegalArgumentException("Upper ceiling height must be positive and gap non-negative");
            }
        }
    }
}
