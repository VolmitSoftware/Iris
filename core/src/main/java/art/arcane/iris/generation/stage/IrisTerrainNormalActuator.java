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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedActuator;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.IrisSurfaceRiverBedConfig;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.hydrology.IrisRiverMaterialConfig;
import art.arcane.iris.generation.hydrology.IrisSurfaceRiverBankConfig;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import lombok.Getter;

public class IrisTerrainNormalActuator extends EngineAssignedActuator<NativeBlockState> {
    private static final BoundBlockState BEDROCK = BoundBlockState.of("BEDROCK");
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");
    @Getter
    private final RNG rng;

    public IrisTerrainNormalActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getSeedManager().getTerrain());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<NativeBlockState> h, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        paint(x, z, h, context);
        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }

    /**
     * Paints every column of the chunk from its top down to bedrock. Columns are walked z-major so
     * consecutive columns share the cache lines of the x-minor block buffer.
     *
     * @param x the chunk x in blocks
     * @param z the chunk z in blocks
     */
    @BlockCoordinates
    public void paint(int x, int z, Hunk<NativeBlockState> h, ChunkContext context) {
        ColumnPainter painter = new ColumnPainter(x, z, h, context);
        int width = h.getWidth();
        int depth = h.getDepth();
        for (int zf = 0; zf < depth; zf++) {
            for (int xf = 0; xf < width; xf++) {
                painter.paint(xf, zf);
            }
        }
    }

    /**
     * The palette index for a column that erosion cut {@code cut} blocks down into. The offset is
     * clamped to the deepest authored layer so an exposed bank shows soil rather than raw rock,
     * while a depth past the authored soil still runs off the end and falls through to rock. A
     * cut of zero collapses to {@code depth}, leaving every uncut column untouched.
     */
    static int strataIndex(int depth, int cut, int paletteSize) {
        return depth + Math.min(cut, Math.max(0, paletteSize - 1 - depth));
    }

    /**
     * The river material that owns this column's top layers, or null when the biome layers stand.
     * Roles are exclusive: a channel bed, then the shore strip, then the eroded bank.
     */
    static IrisRiverMaterialConfig hydrologyRoleMaterial(
            HydrologyColumnLayer terrainLayer,
            IrisRiverMaterialConfig bedMaterial,
            IrisRiverMaterialConfig shoreMaterial,
            IrisRiverMaterialConfig bankMaterial
    ) {
        if (terrainLayer == null || !terrainLayer.terrainOwned()) {
            return null;
        }

        IrisRiverMaterialConfig material;
        if (terrainLayer.channel()) {
            material = bedMaterial;
        } else if (terrainLayer.shore()) {
            material = shoreMaterial;
        } else if (terrainLayer.grading()) {
            material = bankMaterial;
        } else {
            material = null;
        }

        return material != null && material.isEnabled() ? material : null;
    }

    /**
     * Replaces a biome layer with the river material for the top {@code depth} blocks. A painted
     * gravity block still goes through the bed padding swap after this.
     */
    static NativeBlockState paintHydrologyMaterial(
            NativeBlockState layerBlock,
            IrisRiverMaterialConfig material,
            int depth,
            RNG rng,
            int x,
            int y,
            int z,
            IrisData data
    ) {
        if (material == null || !material.isEnabled() || depth >= material.getDepth()) {
            return layerBlock;
        }

        NativeBlockState painted = material.getPalette().get(rng, x, y, z, data);
        return painted == null ? layerBlock : painted;
    }

    private final class ColumnPainter {
        private final int x;
        private final int z;
        private final Hunk<NativeBlockState> h;
        private final ChunkContext context;
        private final int chunkHeight;
        private final IrisDimension dimension;
        private final IrisData data;
        private final IrisComplex complex;
        private final ProceduralStream<Double> riverWaterSurfaceStream;
        private final boolean bedrockEnabled;
        private final boolean hideOres;
        private final boolean transitionDisplaced;
        private final ChunkedDataCache<IrisBiome> biomeCache;
        private final ChunkedDataCache<IrisRegion> regionCache;
        private final ChunkedDataCache<NativeBlockState> rockCache;
        private final UpperDimensionContext upperContext;
        private final IrisOreBands dimensionSurfaceOres;
        private final IrisOreBands dimensionUndergroundOres;
        private final boolean exposeCutStrata;
        private final IrisSurfaceRiverBedConfig riverBed;
        private final boolean padRiverBed;
        private final IrisRiverMaterialConfig bedMaterial;
        private final IrisRiverMaterialConfig shoreMaterial;
        private final IrisRiverMaterialConfig bankMaterial;
        private final NativeBlockState air;
        private final NativeBlockState bedrock;
        private IrisOreBands biomeSurfaceOres;
        private IrisOreBands regionSurfaceOres;
        private IrisOreBands biomeUndergroundOres;
        private IrisOreBands regionUndergroundOres;

        private ColumnPainter(int x, int z, Hunk<NativeBlockState> h, ChunkContext context) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.context = context;
            chunkHeight = h.getHeight();
            dimension = getDimension();
            data = getData();
            complex = getComplex();
            riverWaterSurfaceStream = complex.getRiverWaterSurfaceStream();
            bedrockEnabled = dimension.isBedrock();
            hideOres = dimension.isHideOresForHiddenOre();
            transitionDisplaced = complex.getTransitionDisplacement() != null;
            biomeCache = context.getBiome();
            regionCache = context.getRegion();
            rockCache = context.getRock();
            upperContext = getEngine().getUpperContext();
            dimensionSurfaceOres = hideOres ? IrisOreBands.EMPTY : dimension.getSurfaceOreBands();
            dimensionUndergroundOres = hideOres ? IrisOreBands.EMPTY : dimension.getUndergroundOreBands();
            IrisSurfaceRiverBankConfig riverBanks = dimension.getHydrology() == null
                    ? null
                    : dimension.getHydrology().getRivers().getSurface().getBanks();
            exposeCutStrata = riverBanks != null && riverBanks.isExposeCutStrata();
            riverBed = dimension.getHydrology() == null
                    ? null
                    : dimension.getHydrology().getRivers().getSurface().getBed();
            padRiverBed = riverBed != null && !riverBed.isAllowGravityBlocks();
            bedMaterial = riverBed == null ? null : riverBed.getMaterial();
            shoreMaterial = riverBanks == null ? null : riverBanks.getShoreMaterial();
            bankMaterial = riverBanks == null ? null : riverBanks.getBankMaterial();
            air = AIR.get();
            bedrock = BEDROCK.get();
        }

        private void paint(int xf, int zf) {
            int realX = xf + x;
            int realZ = zf + z;
            IrisBiome biome = biomeCache.get(xf, zf);
            IrisRegion region = regionCache.get(xf, zf);
            int he = Math.min(chunkHeight, context.getRoundedHeight(xf, zf));
            int surfaceFluidHeight = Math.min(
                    chunkHeight,
                    (int) Math.round(riverWaterSurfaceStream.getDouble(realX, realZ))
            );
            int hf = Math.max(surfaceFluidHeight, he);
            if (hf < 0) {
                return;
            }

            int topY = Math.min(hf, chunkHeight - 1);
            HydrologyColumnSample hydrology = complex.sampleHydrologyColumn(realX, realZ);
            HydrologyColumnLayer hydrologyFluid = hydrology == null
                    ? null
                    : hydrology.primarySurfaceFluidLayerOrNull();
            HydrologyColumnLayer hydrologyTerrain = hydrology == null
                    ? null
                    : hydrology.primarySurfaceLayerOrNull();
            Terrain3DColumn terrainColumn = complex.terrainColumn(realX, realZ, hydrology);
            int terrainSpan = terrainColumn == null ? -1 : terrainColumn.spanCount() - 1;
            int layerSurfaceY = he;
            int layerCeilingY = 0;
            boolean exposeRiverStrata = exposeCutStrata
                    && hydrologyTerrain != null
                    && hydrologyTerrain.terrainOwned()
                    && !hydrologyTerrain.channel();
            Terrain3DColumn naturalColumn = exposeRiverStrata ? complex.naturalTerrainColumn(realX, realZ) : null;
            int cut = 0;
            boolean riverOwned = padRiverBed && hydrologyTerrain != null && hydrologyTerrain.terrainOwned();
            IrisRiverMaterialConfig roleMaterial = hydrologyRoleMaterial(
                    hydrologyTerrain, bedMaterial, shoreMaterial, bankMaterial);
            int paintDepth = roleMaterial == null ? 0 : roleMaterial.getDepth();
            NativeBlockState fluid = hydrologyFluid == null || transitionDisplaced
                    ? complex.resolveSurfaceFluid(realX, realZ)
                    : complex.resolveHydrologyFluid(hydrologyFluid.profileKey(), realX, realZ);
            NativeBlockState rock = rockCache.get(xf, zf);
            NativeBlockState mappedSurfaceBlock = complex.getImageMapRuntime().sampleSurfaceBlock(realX, realZ);
            biomeSurfaceOres = hideOres ? IrisOreBands.EMPTY : biome.getSurfaceOreBands();
            regionSurfaceOres = hideOres ? IrisOreBands.EMPTY : region.getSurfaceOreBands();
            biomeUndergroundOres = hideOres ? IrisOreBands.EMPTY : biome.getUndergroundOreBands();
            regionUndergroundOres = hideOres ? IrisOreBands.EMPTY : region.getUndergroundOreBands();
            boolean hasSurfaceOres = biomeSurfaceOres.hasOres() || regionSurfaceOres.hasOres() || dimensionSurfaceOres.hasOres();
            boolean hasUndergroundOres = biomeUndergroundOres.hasOres() || regionUndergroundOres.hasOres() || dimensionUndergroundOres.hasOres();
            int bedrockFloor = bedrockEnabled ? 1 : 0;
            KList<NativeBlockState> blocks = null;
            KList<NativeBlockState> ceilingBlocks = null;
            KList<NativeBlockState> fblocks = null;

            for (int i = topY; i >= 0; i--) {
                if (i == 0 && bedrockEnabled) {
                    h.setRaw(xf, i, zf, bedrock);
                    continue;
                }

                if (terrainColumn != null && i <= he) {
                    while (terrainSpan >= 0 && i < terrainColumn.ceiling(terrainSpan)) {
                        terrainSpan--;
                        blocks = null;
                        ceilingBlocks = null;
                    }
                    if (terrainSpan < 0 || i > terrainColumn.floor(terrainSpan)) {
                        h.setRaw(xf, i, zf, air);
                        continue;
                    }
                    layerSurfaceY = terrainColumn.floor(terrainSpan);
                    layerCeilingY = terrainColumn.ceiling(terrainSpan);
                }

                NativeBlockState ore = hasSurfaceOres ? surfaceOre(realX, i, realZ) : null;
                if (ore != null) {
                    h.setRaw(xf, i, zf, ore);
                    continue;
                }

                if (i > he && i <= hf) {
                    int fdepth = hf - i;
                    if (hydrologyFluid == null && fblocks == null) {
                        fblocks = biome.generateSeaLayers(realX, realZ, rng, hf - he, data);
                    }
                    h.setRaw(xf, i, zf, HydrologyFluidLayerSelector.select(
                            fblocks,
                            fdepth,
                            fluid,
                            hydrologyFluid != null
                    ));
                    continue;
                }

                if (i <= he) {
                    int depth = layerSurfaceY - i;
                    if (depth == 0 && mappedSurfaceBlock != null) {
                        h.setRaw(xf, i, zf, mappedSurfaceBlock);
                        continue;
                    }
                    if (blocks == null) {
                        if (exposeRiverStrata) {
                            int naturalSurfaceY = naturalColumn == null ? hydrology.naturalHeight() : naturalColumn.surfaceY(layerSurfaceY);
                            cut = Math.max(0, naturalSurfaceY - layerSurfaceY);
                        }
                        blocks = biome.generateLayers(dimension, realX, realZ, rng,
                                layerSurfaceY + cut + 1 - bedrockFloor, layerSurfaceY + cut, data, complex);
                    }

                    int deepFloor = Math.max(bedrockFloor, layerCeilingY > 0 ? layerCeilingY + 2 : 0);
                    if (i >= deepFloor && depth >= Math.max(blocks.size(), paintDepth)) {
                        paintDeep(xf, zf, realX, realZ, i, deepFloor, rock, hasSurfaceOres, hasUndergroundOres);
                        i = deepFloor;
                        continue;
                    }

                    if (layerCeilingY > 0 && depth >= 2 && i - layerCeilingY < 2) {
                        if (ceilingBlocks == null) {
                            ceilingBlocks = biome.generateCeilingLayers(dimension, realX, realZ, rng,
                                    2, layerCeilingY, data, complex);
                        }
                        int ceilingDepth = i - layerCeilingY;
                        h.setRaw(xf, i, zf, ceilingBlocks.hasIndex(ceilingDepth)
                                ? ceilingBlocks.get(ceilingDepth) : rock);
                        continue;
                    }

                    int strataIndex = strataIndex(depth, cut, blocks.size());
                    NativeBlockState layerBlock = paintHydrologyMaterial(
                            blocks.hasIndex(strataIndex) ? blocks.get(strataIndex) : null,
                            roleMaterial, depth, rng, realX, i, realZ, data);
                    if (layerBlock != null) {
                        if (riverOwned && depth <= riverBed.getPadding() && IrisProceduralBlocks.isGravityAffected(layerBlock)) {
                            layerBlock = riverBed.getPaddingPalette().get(rng, realX, i, realZ, data);
                        }
                        h.setRaw(xf, i, zf, layerBlock);
                        continue;
                    }

                    ore = hasUndergroundOres ? undergroundOre(realX, i, realZ) : null;
                    h.setRaw(xf, i, zf, ore != null ? ore : rock);
                }
            }

            if (upperContext != null) {
                paintUpper(xf, zf, realX, realZ);
            }
        }

        /**
         * Paints the solid run from {@code top} down to {@code floor}, which lies below every biome
         * layer, river material, mapped surface and cave ceiling of its span: only ores or rock land there.
         */
        private void paintDeep(int xf, int zf, int realX, int realZ, int top, int floor, NativeBlockState rock,
                               boolean hasSurfaceOres, boolean hasUndergroundOres) {
            for (int y = top; y >= floor; y--) {
                NativeBlockState ore = hasSurfaceOres ? surfaceOre(realX, y, realZ) : null;
                if (ore == null && hasUndergroundOres) {
                    ore = undergroundOre(realX, y, realZ);
                }
                h.setRaw(xf, y, zf, ore != null ? ore : rock);
            }
        }

        private NativeBlockState surfaceOre(int realX, int y, int realZ) {
            NativeBlockState ore = null;
            if (biomeSurfaceOres.contains(y)) {
                ore = biomeSurfaceOres.generate(realX, y, realZ, rng, data);
            }
            if (ore == null && regionSurfaceOres.contains(y)) {
                ore = regionSurfaceOres.generate(realX, y, realZ, rng, data);
            }
            if (ore == null && dimensionSurfaceOres.contains(y)) {
                ore = dimensionSurfaceOres.generate(realX, y, realZ, rng, data);
            }
            return ore;
        }

        private NativeBlockState undergroundOre(int realX, int y, int realZ) {
            NativeBlockState ore = null;
            if (biomeUndergroundOres.contains(y)) {
                ore = biomeUndergroundOres.generate(realX, y, realZ, rng, data);
            }
            if (ore == null && regionUndergroundOres.contains(y)) {
                ore = regionUndergroundOres.generate(realX, y, realZ, rng, data);
            }
            if (ore == null && dimensionUndergroundOres.contains(y)) {
                ore = dimensionUndergroundOres.generate(realX, y, realZ, rng, data);
            }
            return ore;
        }

        private void paintUpper(int xf, int zf, int realX, int realZ) {
            UpperDimensionContext.Column upperColumn = upperContext.sampleColumn(realX, realZ);
            int upperSurfaceY = upperColumn.surfaceY();
            if (upperSurfaceY >= chunkHeight - 1) {
                return;
            }

            IrisBiome upperBiome = upperContext.getUpperBiome(realX, realZ);
            NativeBlockState upperRock = upperContext.getRockBlock(realX, realZ);
            NativeBlockState upperMappedSurface = upperContext.getSurfaceBlock(realX, realZ);
            KList<NativeBlockState> upperBlocks = null;
            int paletteSourceY = -1;

            for (int y = chunkHeight - 1; y >= upperSurfaceY; y--) {
                if (y == chunkHeight - 1 && bedrockEnabled) {
                    h.setRaw(xf, y, zf, bedrock);
                    continue;
                }
                if (!upperColumn.isSolid(y)) {
                    h.setRaw(xf, y, zf, air);
                    continue;
                }
                int faceY = upperColumn.faceY(y);
                int sourceSurfaceY = upperColumn.height() - 1 - faceY;
                if (sourceSurfaceY != paletteSourceY) {
                    paletteSourceY = sourceSurfaceY;
                    upperBlocks = null;
                }
                if (y == faceY && upperMappedSurface != null) {
                    h.setRaw(xf, y, zf, upperMappedSurface);
                    continue;
                }
                int depthFromFace = y - faceY;
                if (upperBlocks == null && upperBiome != null) {
                    upperBlocks = upperBiome.generateLayersWithSlope(upperContext.getDimension(),
                            realX, realZ, rng, sourceSurfaceY + 1 - (bedrockEnabled ? 1 : 0), sourceSurfaceY,
                            upperContext.getData(), upperContext.getSurfaceSlopeStream(sourceSurfaceY));
                }
                if (upperBlocks != null && upperBlocks.hasIndex(depthFromFace)) {
                    h.setRaw(xf, y, zf, upperBlocks.get(depthFromFace));
                } else {
                    h.setRaw(xf, y, zf, upperRock);
                }
            }
        }
    }
}
