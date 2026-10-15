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

import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.TerrainSample;
import com.volmit.world.BiomeKind;
import com.volmit.world.TerrainSampler;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;

/** Terrain for the tests of the carve module: a surface height per column, a biome sample per block and an optional dimension above. */
public final class CarveTestTerrain implements TerrainSampler {
    private static final int MARGIN = 16;

    /** The biome sample of a block. */
    @FunctionalInterface
    public interface Biomes {
        BiomeSample at(int x, int y, int z);
    }

    private final int originX;
    private final int originZ;
    private final int height;
    private final int fluidHeight;
    private final IntBinaryOperator surface;
    private Biomes biomes = (x, y, z) -> new BiomeSample(CarveTestWorld.id("region"), CarveTestWorld.id("plains"), Optional.empty());
    private IntBinaryOperator upper;

    public CarveTestTerrain(int originX, int originZ, int height, int fluidHeight, IntBinaryOperator surface) {
        this.originX = originX;
        this.originZ = originZ;
        this.height = height;
        this.fluidHeight = fluidHeight;
        this.surface = surface;
    }

    public CarveTestTerrain biomes(Biomes biomes) {
        this.biomes = biomes;
        return this;
    }

    public CarveTestTerrain upperDimension(IntBinaryOperator upper) {
        this.upper = upper;
        return this;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(originX - MARGIN, 0, originZ - MARGIN, originX + 16 + MARGIN, height, originZ + 16 + MARGIN);
    }

    @Override
    public BiomeKind surfaceKind(int worldX, int worldZ) {
        return BiomeKind.LAND;
    }

    @Override
    public int minWorldY() {
        return CarveTestWorld.MIN_HEIGHT;
    }

    @Override
    public TerrainSample column(int worldX, int worldZ) {
        int surfaceY = surface.applyAsInt(worldX, worldZ);
        return new TerrainSample(surfaceY, surfaceY < fluidHeight ? OptionalInt.of(fluidHeight) : OptionalInt.empty(), 0D);
    }

    @Override
    public double surfaceY(int worldX, int worldZ) {
        require(worldX, 0, worldZ);
        return surface.applyAsInt(worldX, worldZ);
    }

    @Override
    public BiomeSample biome(int worldX, int y, int worldZ) {
        require(worldX, y, worldZ);
        return biomes.at(worldX, y, worldZ);
    }

    @Override
    public Optional<ResourceId> caveBiome(int worldX, int y, int worldZ) {
        return biome(worldX, y, worldZ).caveBiome();
    }

    @Override
    public int fluidHeight() {
        return fluidHeight;
    }

    @Override
    public OptionalInt upperDimensionY(int worldX, int worldZ) {
        require(worldX, 0, worldZ);
        return upper == null ? OptionalInt.empty() : OptionalInt.of(upper.applyAsInt(worldX, worldZ));
    }

    private void require(int worldX, int y, int worldZ) {
        if (!bounds().contains(worldX, y, worldZ)) {
            throw new IndexOutOfBoundsException(worldX + ", " + y + ", " + worldZ + " is outside " + bounds());
        }
    }
}
