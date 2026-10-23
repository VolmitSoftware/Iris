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
package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.terrain.BiomeLayers;
import art.arcane.iris.builtin.terrain.ColumnSlope;
import art.arcane.iris.builtin.terrain.PaletteExplosion;
import art.arcane.iris.builtin.terrain.TerrainPalettes;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import art.arcane.iris.engine.decorator.DecoratedBiome;
import art.arcane.iris.engine.decorator.DecorationScene;
import art.arcane.iris.engine.decorator.IrisCeilingDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.BiomeKind;
import com.volmit.world.TerrainSampler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A chunk's {@link CaveDressing} from the world's biome layers, terrain palettes and decorators, which carving shares with the terrain and decorant built-ins. */
final class BuiltInCaveDressing implements CaveDressing {
    private final BiomeLayers layers;
    private final TerrainPalettes palettes;
    private final FeatureSettings settings;
    private final BlockRegistry blocks;
    private final PaletteExplosion explosion;
    private final ColumnSlope slope;
    private final DecorationScene scene;
    private final Map<ResourceId, DecoratedBiome> decoratedBiomes = new HashMap<>();

    BuiltInCaveDressing(ChunkStageContext context) {
        TerrainSampler terrain = context.terrain();
        this.layers = BiomeLayers.of(context.world());
        this.palettes = TerrainPalettes.of(context.world());
        this.settings = context.settings();
        this.blocks = context.world().resources().blocks();
        this.explosion = PaletteExplosion.of(settings, context.originX(), context.originZ());
        this.slope = (x, z) -> terrain.column((int) x, (int) z).slope();
        this.scene = DecorationScene.of(context);
    }

    @Override
    public BlockState fluidAt(int x, int z) {
        return palettes.fluidAt(x, z);
    }

    @Override
    public BlockState wall(ResourceId biome, RNG rng, int x, int y, int z) {
        return layers.wall(layers.biome(biome, settings)).get(rng, x, y, z);
    }

    @Override
    public List<BlockState> floorLayers(ResourceId biome, int x, int z, RNG rng, int depth, int floorY) {
        return layers.generateLayers(layers.biome(biome, settings), explosion, x, z, rng, depth, floorY, slope);
    }

    @Override
    public List<BlockState> ceilingLayers(ResourceId biome, int x, int z, RNG rng, int depth) {
        return layers.generateCeilingLayers(layers.biome(biome, settings), explosion, x, z, rng, depth);
    }

    @Override
    public boolean hasDecorators(ResourceId biome, DecorationPart part) {
        return scene.decorators().hasDecorators(decorated(biome), part);
    }

    @Override
    public void decorateFloor(int localX, int localZ, int x, int z, ResourceId biome, int floorY, int airThickness) {
        new IrisSurfaceDecorator(scene).decorate(localX, localZ, x, x, x, z, z, z, decorated(biome), floorY, airThickness);
    }

    @Override
    public void decorateCeiling(int localX, int localZ, int x, int z, ResourceId biome, int ceilingY, int airThickness) {
        new IrisCeilingDecorator(scene).decorate(localX, localZ, x, x, x, z, z, z, decorated(biome), ceilingY, airThickness);
    }

    @Override
    public BlockState oreFor(BlockState rock, BlockState ore) {
        return blocks.oreFor(rock, ore);
    }

    /** The decorators' view of a cave biome: what it declares, under the key its settings come from, as other decorated biomes are. */
    private DecoratedBiome decorated(ResourceId biome) {
        return decoratedBiomes.computeIfAbsent(biome, id -> new DecoratedBiome(BiomeDecorators.biomeKey(id),
                settings.declaredBy(id, DecorantSettings.class).orElse(null), BiomeKind.CAVE));
    }
}
