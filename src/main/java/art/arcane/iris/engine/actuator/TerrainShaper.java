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
package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.terrain.BedrockSettings;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.iris.engine.actuator.ResolvedOres.YBand;
import art.arcane.iris.builtin.terrain.BiomeLayers;
import art.arcane.iris.builtin.terrain.ColumnSlope;
import art.arcane.iris.builtin.terrain.LayeredBiome;
import art.arcane.iris.builtin.terrain.PaletteExplosion;
import art.arcane.iris.builtin.terrain.TerrainPalettes;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisFeature;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;
import com.volmit.world.UpperDimension;

import java.util.Optional;
import java.util.OptionalInt;

/** What shaping terrain keeps for one world: the terrain stream's random and the palettes, layers and ores the columns are filled from. */
final class TerrainShaper {
    private static final BlockState BEDROCK = new BlockState("minecraft:bedrock");

    private final TerrainPalettes palettes;
    private final BiomeLayers layers;
    private final TerrainOres ores;
    private final RNG rng;
    private final boolean levelsBelowTheDimensionDeclareOres;

    static TerrainShaper of(PackWorld world) {
        return world.state(TerrainShaper.class, TerrainShaper::new);
    }

    TerrainShaper(PackWorld world) {
        this(world, TerrainPalettes.of(world), BiomeLayers.of(world), TerrainOres.of(world));
    }

    TerrainShaper(PackWorld world, TerrainPalettes palettes, BiomeLayers layers, TerrainOres ores) {
        this.palettes = palettes;
        this.layers = layers;
        this.ores = ores;
        this.rng = new RNG(IrisSeed.TERRAIN.of(world));
        this.levelsBelowTheDimensionDeclareOres = world.settings().declared(OreSettings.class).entries().stream()
                .anyMatch(entry -> entry.level() != Level.DIMENSION);
    }

    /** Fills the 16 x 16 columns of the chunk, one slice along x at a time. */
    void shape(ChunkStageContext context) {
        Chunk chunk = new Chunk(context);
        for (int sliceX = 0; sliceX < chunk.blocks.getWidth(); sliceX++) {
            shapeSliver(chunk, sliceX);
        }
    }

    /** One plane from the sky to bedrock, one block thick along x. */
    private void shapeSliver(Chunk chunk, int xf) {
        Hunk<BlockState> h = chunk.blocks;
        int chunkHeight = h.getHeight();
        int chunkDepth = h.getDepth();
        int realX = xf + chunk.originX;

        for (int zf = 0; zf < chunkDepth; zf++) {
            int realZ = zf + chunk.originZ;
            int he = Math.min(chunkHeight, chunk.terrain.surfaceHeight(realX, realZ));
            int hf = Math.max(chunk.clampedFluidHeight, he);
            if (hf < 0) {
                continue;
            }

            int topY = Math.min(hf, chunkHeight - 1);
            BlockState fluid = palettes.fluidAt(realX, realZ);
            BlockState rock = palettes.rockAt(realX, realZ);
            BlockState mappedSurfaceBlock = chunk.terrain.surfaceBlock(realX, realZ).orElse(null);
            LayeredBiome biome = layers.biome(chunk.terrain.surfaceBiome(realX, realZ), chunk.settings);
            ColumnOres columnOres = chunk.oresAt(realX, realZ);
            ResolvedOres biomeOres = columnOres.biome;
            ResolvedOres regionOres = columnOres.region;
            YBand biomeSurfaceBand = biomeOres.surfaceBand();
            YBand regionSurfaceBand = regionOres.surfaceBand();
            YBand biomeUndergroundBand = biomeOres.undergroundBand();
            YBand regionUndergroundBand = regionOres.undergroundBand();
            ResolvedOres dimensionOres = chunk.dimensionOres;
            YBand dimensionSurfaceBand = dimensionOres.surfaceBand();
            YBand dimensionUndergroundBand = dimensionOres.undergroundBand();
            boolean hasSurfaceOres = biomeSurfaceBand.hasOres() || regionSurfaceBand.hasOres() || dimensionSurfaceBand.hasOres();
            boolean hasUndergroundOres = biomeUndergroundBand.hasOres() || regionUndergroundBand.hasOres() || dimensionUndergroundBand.hasOres();
            KList<BlockState> blocks = null;
            KList<BlockState> fblocks = null;

            for (int i = topY; i >= 0; i--) {
                if (i == 0 && chunk.bedrockEnabled) {
                    place(h, xf, i, zf, BEDROCK);
                    continue;
                }

                BlockState ore = null;
                if (hasSurfaceOres) {
                    if (biomeSurfaceBand.contains(i)) {
                        ore = biomeOres.generateSurface(realX, i, realZ);
                    }
                    if (ore == null && regionSurfaceBand.contains(i)) {
                        ore = regionOres.generateSurface(realX, i, realZ);
                    }
                    if (ore == null && dimensionSurfaceBand.contains(i)) {
                        ore = dimensionOres.generateSurface(realX, i, realZ);
                    }
                }
                if (ore != null) {
                    place(h, xf, i, zf, ore);
                    continue;
                }

                if (i > he && i <= hf) {
                    int fdepth = hf - i;
                    if (fblocks == null) {
                        fblocks = layers.generateSeaLayers(biome, realX, realZ, rng, hf - he);
                    }

                    if (fblocks.hasIndex(fdepth)) {
                        place(h, xf, i, zf, fblocks.get(fdepth));
                    } else {
                        place(h, xf, i, zf, fluid);
                    }
                    continue;
                }

                if (i <= he) {
                    int depth = he - i;
                    if (depth == 0 && mappedSurfaceBlock != null) {
                        place(h, xf, i, zf, mappedSurfaceBlock);
                        continue;
                    }
                    if (blocks == null) {
                        blocks = layers.generateLayers(biome, chunk.explosion, realX, realZ, rng, he, he, chunk.slope);
                    }

                    if (blocks.hasIndex(depth)) {
                        place(h, xf, i, zf, blocks.get(depth));
                        continue;
                    }

                    if (hasUndergroundOres) {
                        if (biomeUndergroundBand.contains(i)) {
                            ore = biomeOres.generateUnderground(realX, i, realZ);
                        }
                        if (ore == null && regionUndergroundBand.contains(i)) {
                            ore = regionOres.generateUnderground(realX, i, realZ);
                        }
                        if (ore == null && dimensionUndergroundBand.contains(i)) {
                            ore = dimensionOres.generateUnderground(realX, i, realZ);
                        }
                    }

                    place(h, xf, i, zf, ore != null ? ore : rock);
                }
            }

            if (chunk.upper.isPresent()) {
                fillUpperDimension(chunk, chunk.upper.get(), xf, zf, realX, realZ);
            }
        }
    }

    /** The upper dimension's ground, filled in from the top of the column down to where it ends. */
    private void fillUpperDimension(Chunk chunk, UpperDimension upper, int xf, int zf, int realX, int realZ) {
        Hunk<BlockState> h = chunk.blocks;
        int chunkHeight = h.getHeight();
        OptionalInt upperY = chunk.terrain.upperDimensionY(realX, realZ);
        if (upperY.isEmpty()) {
            return;
        }
        int upperSurfaceY = upperY.getAsInt();
        if (upperSurfaceY >= chunkHeight - 1) {
            return;
        }

        FeatureSettings upperSettings = upper.settings();
        BlockState upperRock = palettes.upperRockAt(upper, realX, realZ);
        BlockState upperMappedSurface = upper.surfaceBlock(realX, realZ).orElse(null);
        int upperThickness = chunkHeight - 1 - upperSurfaceY;
        LayeredBiome upperBiome = upperBiome(upperSettings, realX, realZ);
        KList<BlockState> upperBlocks = layers.generateLayers(upperBiome, chunk.upperExplosion(upperSettings, realX, realZ),
                realX, realZ, rng, upperThickness, upperThickness, chunk.slope);

        for (int y = chunkHeight - 1; y >= upperSurfaceY; y--) {
            if (y == chunkHeight - 1 && chunk.bedrockEnabled) {
                place(h, xf, y, zf, BEDROCK);
                continue;
            }
            if (y == upperSurfaceY && upperMappedSurface != null) {
                place(h, xf, y, zf, upperMappedSurface);
                continue;
            }
            int depthFromFace = y - upperSurfaceY;
            if (upperBlocks.hasIndex(depthFromFace)) {
                place(h, xf, y, zf, upperBlocks.get(depthFromFace));
            } else {
                place(h, xf, y, zf, upperRock);
            }
        }
    }

    /** What the biome of the upper dimension at the column declares about its layers. */
    private LayeredBiome upperBiome(FeatureSettings upperSettings, int worldX, int worldZ) {
        return new LayeredBiome(biomeLevel(upperSettings, art.arcane.iris.builtin.terrain.LayersSettings.class, worldX, worldZ), null, null,
                biomeLevel(upperSettings, art.arcane.iris.builtin.terrain.LockLayersSettings.class, worldX, worldZ),
                biomeLevel(upperSettings, art.arcane.iris.builtin.terrain.LockLayersMaxSettings.class, worldX, worldZ), null);
    }

    private static <S extends IrisFeature> S biomeLevel(FeatureSettings settings, Class<S> type, int worldX, int worldZ) {
        return settings.at(type, worldX, worldZ).level(Level.BIOME).orElse(null);
    }

    /** A block the palette has none of leaves the column as it is. */
    private static void place(Hunk<BlockState> blocks, int x, int y, int z, BlockState block) {
        if (block != null) {
            blocks.set(x, y, z, block);
        }
    }

    /** The ores of a column's region and biome. */
    private record ColumnOres(ResolvedOres region, ResolvedOres biome) {
        static final ColumnOres NONE = new ColumnOres(ResolvedOres.NONE, ResolvedOres.NONE);
    }

    /** What one chunk's columns share: its views, its dimension's switches and ores. */
    private final class Chunk {
        final Hunk<BlockState> blocks;
        final TerrainSampler terrain;
        final FeatureSettings settings;
        final int originX;
        final int originZ;
        final int clampedFluidHeight;
        final boolean bedrockEnabled;
        final PaletteExplosion explosion;
        final ColumnSlope slope;
        final Optional<UpperDimension> upper;
        final ResolvedOres dimensionOres;
        private final boolean hideOres;
        private PaletteExplosion upperExplosion;

        Chunk(ChunkStageContext context) {
            this.blocks = context.hunk(BlockState.class);
            this.terrain = context.terrain();
            this.settings = context.settings();
            this.originX = context.originX();
            this.originZ = context.originZ();
            this.clampedFluidHeight = Math.min(blocks.getHeight(), terrain.fluidHeight());
            int centreX = context.centreX();
            int centreZ = context.centreZ();
            this.bedrockEnabled = settings.at(BedrockSettings.class, centreX, centreZ).dimension().map(BedrockSettings::bedrock).orElse(true);
            this.hideOres = settings.at(HideOresSettings.class, centreX, centreZ).dimension().map(HideOresSettings::hide).orElse(false);
            this.explosion = PaletteExplosion.of(settings, centreX, centreZ);
            this.slope = (x, z) -> terrain.column((int) x, (int) z).slope();
            this.upper = terrain.upperDimension();
            // Dimension-level ore lookups are chunk-invariant; resolving them per column paid the settings lookup times 256 per chunk for a constant.
            this.dimensionOres = hideOres ? ResolvedOres.NONE : ores.of(settings.at(OreSettings.class, centreX, centreZ).dimension().orElse(null));
        }

        /** What the upper dimension declares about exploded palettes: a setting of the dimension, so the same for every column of the chunk. */
        PaletteExplosion upperExplosion(FeatureSettings upperSettings, int worldX, int worldZ) {
            if (upperExplosion == null) {
                upperExplosion = PaletteExplosion.of(upperSettings, worldX, worldZ);
            }
            return upperExplosion;
        }

        /** The ores of the column's region and biome; none where the levels below the dimension declare none, or the dimension hides ores. */
        ColumnOres oresAt(int worldX, int worldZ) {
            if (hideOres || !levelsBelowTheDimensionDeclareOres) {
                return ColumnOres.NONE;
            }
            SettingsStack<OreSettings> declared = settings.at(OreSettings.class, worldX, worldZ);
            return new ColumnOres(ores.of(declared.level(Level.REGION).orElse(null)), ores.of(declared.level(Level.BIOME).orElse(null)));
        }
    }
}
