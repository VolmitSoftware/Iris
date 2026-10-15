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

import art.arcane.iris.builtin.CaveDressing;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import art.arcane.iris.engine.decorator.IrisCeilingDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.terrain.BiomeLayers;
import art.arcane.iris.engine.terrain.TerrainPalettes;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CaveDressing} over Iris's biome layers, decorators and terrain palettes: what the carve module asks of the features that
 * are not modules yet, in IrisApi's types. Transitional, like the interface.
 */
final class IrisCaveDressing implements CaveDressing {
    private final Engine engine;
    private final IrisPackResources resources;
    private final BiomeLayers layers;
    private final BiomeDecorators decorators;
    private final TerrainPalettes palettes;
    private final IrisSurfaceDecorator surfaceDecorator;
    private final IrisCeilingDecorator ceilingDecorator;

    IrisCaveDressing(Engine engine, IrisPackResources resources, ModuleLog log) {
        this.engine = engine;
        this.resources = resources;
        this.layers = BiomeLayers.of(engine, log);
        this.decorators = BiomeDecorators.of(engine);
        this.palettes = TerrainPalettes.of(engine);
        this.surfaceDecorator = new IrisSurfaceDecorator(engine);
        this.ceilingDecorator = new IrisCeilingDecorator(engine);
    }

    @Override
    public boolean fluidPresentAt(int x, int z) {
        return B.isFluid(palettes.fluidAt(x, z));
    }

    @Override
    public BlockState fluidAt(Hunk<BlockState> chunk, int x, int z) {
        return nullable(states(chunk), palettes.fluidAt(x, z));
    }

    @Override
    public BlockState wall(Hunk<BlockState> chunk, ResourceId biome, RNG rng, int x, int y, int z) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        if (irisBiome == null) {
            return null;
        }
        return nullable(states(chunk), layers.wall(irisBiome).get(rng, x, y, z));
    }

    @Override
    public List<BlockState> floorLayers(Hunk<BlockState> chunk, ResourceId biome, int x, int z, RNG rng, int depth, int floorY) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        if (irisBiome == null) {
            return List.of();
        }
        return toApi(chunk, layers.generateLayers(irisBiome, engine.getDimension(), x, z, rng, depth, floorY, engine.getData(), engine.getComplex()));
    }

    @Override
    public List<BlockState> ceilingLayers(Hunk<BlockState> chunk, ResourceId biome, int x, int z, RNG rng, int depth, int ceilingY) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        if (irisBiome == null) {
            return List.of();
        }
        return toApi(chunk, layers.generateCeilingLayers(irisBiome, engine.getDimension(), x, z, rng, depth, ceilingY, engine.getData(), engine.getComplex()));
    }

    @Override
    public boolean hasDecorators(ResourceId biome, DecorationPart part) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        return irisBiome != null && decorators.hasDecorators(irisBiome, part);
    }

    @Override
    public void decorateFloor(Hunk<BlockState> chunk, int localX, int localZ, int x, int z, ResourceId biome, int floorY, int airThickness) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        if (irisBiome == null) {
            return;
        }
        surfaceDecorator.decorate(localX, localZ, x, x, x, z, z, z, platform(chunk), irisBiome, InferredType.CAVE, floorY, airThickness);
    }

    @Override
    public void decorateCeiling(Hunk<BlockState> chunk, int localX, int localZ, int x, int z, ResourceId biome, int ceilingY, int airThickness) {
        IrisBiome irisBiome = resources.irisBiome(biome);
        if (irisBiome == null) {
            return;
        }
        ceilingDecorator.decorate(localX, localZ, x, x, x, z, z, z, platform(chunk), irisBiome, InferredType.CAVE, ceilingY, airThickness);
    }

    @Override
    public BlockState deepSlateOre(Hunk<BlockState> chunk, BlockState existing, BlockState layer) {
        PlatformBlockStates states = states(chunk);
        return states.toApi(B.toDeepSlateOre(states.toPlatform(existing), states.toPlatform(layer)));
    }

    private List<BlockState> toApi(Hunk<BlockState> chunk, List<PlatformBlockState> blocks) {
        PlatformBlockStates states = states(chunk);
        List<BlockState> converted = new ArrayList<>(blocks.size());
        for (PlatformBlockState block : blocks) {
            converted.add(nullable(states, block));
        }
        return converted;
    }

    /** A state or null where the layer gave none; IrisApi's conversion reads null as air. */
    private static BlockState nullable(PlatformBlockStates states, PlatformBlockState block) {
        return block == null ? null : states.toApi(block);
    }

    private static PlatformBlockStates states(Hunk<BlockState> chunk) {
        return hunk(chunk).states();
    }

    private static art.arcane.iris.util.project.hunk.Hunk<PlatformBlockState> platform(Hunk<BlockState> chunk) {
        return hunk(chunk).platform();
    }

    private static BlockStateHunk hunk(Hunk<BlockState> chunk) {
        if (chunk instanceof BlockStateHunk hunk) {
            return hunk;
        }
        throw new IllegalArgumentException("Built-in modules dress the chunks Iris hands to them, not " + chunk);
    }
}
