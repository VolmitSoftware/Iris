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
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.iris.generation.chunk.ColumnRunHunk;
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
    private static final int SLOT_SEA_LAYERS = 1;
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

    private static IrisMaterialPalette rolePalette(WaterRole role, IrisHydrologyMaterials materials) {
        if (materials == null) {
            return null;
        }
        return switch (role) {
            case NONE -> null;
            case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> materials.getBed();
            case SHORE, POND_SHORE, DAM -> materials.getShore();
            case BANK -> materials.getBank();
        };
    }

    private static int roleDepth(WaterRole role, IrisHydrologyMaterials materials) {
        return switch (role) {
            case SHORE, POND_SHORE, DAM -> materials.getShoreDepth();
            case NONE, BANK, CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> 1;
        };
    }

    private static NativeBlockState paintWaterMaterial(
            NativeBlockState layerBlock,
            IrisMaterialPalette palette,
            int paintDepth,
            int depth,
            RNG rng,
            int x,
            int y,
            int z,
            IrisData data
    ) {
        if (palette == null || depth >= paintDepth) {
            return layerBlock;
        }

        NativeBlockState painted = palette.get(rng, x, y, z, data);
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
        private final Engine oreEngine;
        private final IrisComplex complex;
        private final ProceduralStream<Double> waterHeadStream;
        private final ProceduralStream<WaterRole> waterRoleStream;
        private final boolean bedrockEnabled;
        private final boolean hideOres;
        private final boolean transitionDisplaced;
        private final ChunkedDataCache<IrisBiome> biomeCache;
        private final ChunkedDataCache<IrisRegion> regionCache;
        private final ChunkedDataCache<NativeBlockState> rockCache;
        private final ChunkedDataCache<NativeBlockState> fluidCache;
        private final UpperDimensionContext upperContext;
        private final IrisOreBands dimensionSurfaceOres;
        private final IrisOreBands dimensionUndergroundOres;
        private final NativeBlockState air;
        private final NativeBlockState bedrock;
        private IrisOreBands biomeSurfaceOres;
        private IrisOreBands regionSurfaceOres;
        private IrisOreBands biomeUndergroundOres;
        private IrisOreBands regionUndergroundOres;
        private int surfaceOreTop;
        private int surfaceOreFloor;
        private int undergroundOreTop;
        private int undergroundOreFloor;
        private int columnX;
        private int columnZ;
        private NativeBlockState runState;
        private int runTop = -1;
        private int runBottom;

        private ColumnPainter(int x, int z, Hunk<NativeBlockState> h, ChunkContext context) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.context = context;
            chunkHeight = h.getHeight();
            dimension = getDimension();
            data = getData();
            oreEngine = data == null ? null : data.getEngine();
            complex = getComplex();
            waterHeadStream = complex.getWaterHeadStream();
            waterRoleStream = complex.getWaterRoleStream();
            bedrockEnabled = dimension.isBedrock();
            hideOres = dimension.isHideOresForHiddenOre();
            transitionDisplaced = complex.getTransitionDisplacement() != null;
            biomeCache = context.getBiome();
            regionCache = context.getRegion();
            rockCache = context.getRock();
            fluidCache = context.getFluid();
            upperContext = getEngine().getUpperContext();
            dimensionSurfaceOres = hideOres ? IrisOreBands.EMPTY : dimension.getSurfaceOreBands();
            dimensionUndergroundOres = hideOres ? IrisOreBands.EMPTY : dimension.getUndergroundOreBands();
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
                    (int) Math.round(waterHeadStream.getDouble(realX, realZ))
            );
            int hf = Math.max(surfaceFluidHeight, he);
            if (hf < 0) {
                return;
            }

            int topY = Math.min(hf, chunkHeight - 1);
            WaterRole role = waterRoleStream.get(realX, realZ);
            boolean wet = role.wet();
            boolean seaFloor = !wet || role == WaterRole.INLET;
            IrisHydrologyMaterials materials = role == WaterRole.NONE || role == WaterRole.INLET ? null
                    : complex.resolveWaterMaterials(realX, realZ);
            Terrain3DColumn terrainColumn = complex.terrainColumn(realX, realZ);
            int terrainSpan = terrainColumn == null ? -1 : terrainColumn.spanCount() - 1;
            int layerSurfaceY = he;
            int layerCeilingY = 0;
            boolean exposeStrata = role != WaterRole.NONE && seaFloor;
            Terrain3DColumn naturalColumn = exposeStrata ? complex.naturalTerrainColumn(realX, realZ) : null;
            int cut = 0;
            boolean keepGravityAway = materials != null && !materials.isAllowGravityBlocks();
            IrisMaterialPalette rolePalette = rolePalette(role, materials);
            int paintDepth = rolePalette == null ? 0 : roleDepth(role, materials);
            NativeBlockState fluid = wet
                    ? complex.resolveWaterFluid(realX, realZ)
                    : transitionDisplaced
                    ? complex.resolveSeaFluid(realX, realZ)
                    : fluidCache.get(xf, zf);
            NativeBlockState rock = rockCache.get(xf, zf);
            NativeBlockState mappedSurfaceBlock = complex.getImageMapRuntime().sampleSurfaceBlock(realX, realZ);
            biomeSurfaceOres = hideOres ? IrisOreBands.EMPTY : biome.getSurfaceOreBands();
            regionSurfaceOres = hideOres ? IrisOreBands.EMPTY : region.getSurfaceOreBands();
            biomeUndergroundOres = hideOres ? IrisOreBands.EMPTY : biome.getUndergroundOreBands();
            regionUndergroundOres = hideOres ? IrisOreBands.EMPTY : region.getUndergroundOreBands();
            surfaceOreTop = oreTop(biomeSurfaceOres, regionSurfaceOres, dimensionSurfaceOres);
            surfaceOreFloor = oreFloor(biomeSurfaceOres, regionSurfaceOres, dimensionSurfaceOres);
            undergroundOreTop = oreTop(biomeUndergroundOres, regionUndergroundOres, dimensionUndergroundOres);
            undergroundOreFloor = oreFloor(biomeUndergroundOres, regionUndergroundOres, dimensionUndergroundOres);
            columnX = xf;
            columnZ = zf;
            int bedrockFloor = bedrockEnabled ? 1 : 0;
            KList<NativeBlockState> blocks = null;
            KList<NativeBlockState> ceilingBlocks = null;
            KList<NativeBlockState> fblocks = null;

            for (int i = topY; i >= 0; i--) {
                if (i == 0 && bedrockEnabled) {
                    put(i, bedrock);
                    continue;
                }

                if (terrainColumn != null && i <= he) {
                    while (terrainSpan >= 0 && i < terrainColumn.ceiling(terrainSpan)) {
                        terrainSpan--;
                        blocks = null;
                        ceilingBlocks = null;
                    }
                    if (terrainSpan < 0 || i > terrainColumn.floor(terrainSpan)) {
                        put(i, air);
                        continue;
                    }
                    layerSurfaceY = terrainColumn.floor(terrainSpan);
                    layerCeilingY = terrainColumn.ceiling(terrainSpan);
                }

                NativeBlockState ore = i <= surfaceOreTop && i >= surfaceOreFloor ? surfaceOre(realX, i, realZ) : null;
                if (ore != null) {
                    put(i, ore);
                    continue;
                }

                if (i > he && i <= hf) {
                    int fdepth = hf - i;
                    if (seaFloor && fblocks == null) {
                        fblocks = biome.generateSeaLayers(realX, realZ, rng, role == WaterRole.INLET ? SLOT_SEA_LAYERS : hf - he, data);
                    }
                    put(i, HydrologyFluidLayerSelector.select(
                            fblocks,
                            fdepth,
                            fluid,
                            !seaFloor
                    ));
                    continue;
                }

                if (i <= he) {
                    int depth = layerSurfaceY - i;
                    if (depth == 0 && mappedSurfaceBlock != null) {
                        put(i, mappedSurfaceBlock);
                        continue;
                    }
                    if (blocks == null) {
                        if (exposeStrata) {
                            int naturalSurfaceY = naturalColumn == null
                                    ? complex.naturalTrueHeight(realX, realZ) : naturalColumn.surfaceY(layerSurfaceY);
                            cut = Math.max(0, naturalSurfaceY - layerSurfaceY);
                        }
                        blocks = biome.generateLayers(dimension, realX, realZ, rng,
                                layerSurfaceY + cut + 1 - bedrockFloor, layerSurfaceY + cut, data, complex);
                    }

                    int deepFloor = Math.max(bedrockFloor, layerCeilingY > 0 ? layerCeilingY + 2 : 0);
                    if (i >= deepFloor && depth >= Math.max(blocks.size(), paintDepth)) {
                        paintDeep(realX, realZ, i, deepFloor, rock);
                        i = deepFloor;
                        continue;
                    }

                    if (layerCeilingY > 0 && depth >= 2 && i - layerCeilingY < 2) {
                        if (ceilingBlocks == null) {
                            ceilingBlocks = biome.generateCeilingLayers(dimension, realX, realZ, rng,
                                    2, layerCeilingY, data, complex);
                        }
                        int ceilingDepth = i - layerCeilingY;
                        put(i, ceilingBlocks.hasIndex(ceilingDepth)
                                ? ceilingBlocks.get(ceilingDepth) : rock);
                        continue;
                    }

                    int strataIndex = strataIndex(depth, cut, blocks.size());
                    NativeBlockState layerBlock = paintWaterMaterial(
                            blocks.hasIndex(strataIndex) ? blocks.get(strataIndex) : null,
                            rolePalette, paintDepth, depth, rng, realX, i, realZ, data);
                    if (layerBlock != null) {
                        if (keepGravityAway && IrisProceduralBlocks.isGravityAffected(layerBlock)) {
                            layerBlock = rock;
                        }
                        put(i, layerBlock);
                        continue;
                    }

                    ore = i <= undergroundOreTop && i >= undergroundOreFloor ? undergroundOre(realX, i, realZ) : null;
                    put(i, ore != null ? ore : rock);
                }
            }
            flush();

            if (upperContext != null) {
                paintUpper(realX, realZ);
                flush();
            }
        }

        /**
         * Paints the solid run from {@code top} down to {@code floor}, which lies below every biome
         * layer, river material, mapped surface and cave ceiling of its span: only ores or rock land there.
         */
        private void paintDeep(int realX, int realZ, int top, int floor, NativeBlockState rock) {
            int bandTop = Math.min(top, Math.max(surfaceOreTop, undergroundOreTop));
            int bandFloor = Math.max(floor, Math.min(surfaceOreFloor, undergroundOreFloor));
            if (bandTop < bandFloor) {
                putRun(top, floor, rock);
                return;
            }
            if (top > bandTop) {
                putRun(top, bandTop + 1, rock);
            }
            for (int y = bandTop; y >= bandFloor; y--) {
                NativeBlockState ore = y <= surfaceOreTop && y >= surfaceOreFloor ? surfaceOre(realX, y, realZ) : null;
                if (ore == null && y <= undergroundOreTop && y >= undergroundOreFloor) {
                    ore = undergroundOre(realX, y, realZ);
                }
                put(y, ore != null ? ore : rock);
            }
            if (bandFloor > floor) {
                putRun(bandFloor - 1, floor, rock);
            }
        }

        private void put(int y, NativeBlockState state) {
            putRun(y, y, state);
        }

        private void putRun(int top, int bottom, NativeBlockState state) {
            if (runTop >= 0 && state == runState && top == runBottom - 1) {
                runBottom = bottom;
                return;
            }
            flush();
            runState = state;
            runTop = top;
            runBottom = bottom;
        }

        private void flush() {
            if (runTop < 0) {
                return;
            }
            ColumnRunHunk.fillColumnRun(h, columnX, columnZ, runBottom, runTop, runState);
            runTop = -1;
            runState = null;
        }

        private static int oreTop(IrisOreBands first, IrisOreBands second, IrisOreBands third) {
            int top = Integer.MIN_VALUE;
            if (first.hasOres()) {
                top = Math.max(top, first.highestY());
            }
            if (second.hasOres()) {
                top = Math.max(top, second.highestY());
            }
            if (third.hasOres()) {
                top = Math.max(top, third.highestY());
            }
            return top;
        }

        private static int oreFloor(IrisOreBands first, IrisOreBands second, IrisOreBands third) {
            int floor = Integer.MAX_VALUE;
            if (first.hasOres()) {
                floor = Math.min(floor, first.lowestY());
            }
            if (second.hasOres()) {
                floor = Math.min(floor, second.lowestY());
            }
            if (third.hasOres()) {
                floor = Math.min(floor, third.lowestY());
            }
            return floor;
        }

        private NativeBlockState surfaceOre(int realX, int y, int realZ) {
            NativeBlockState ore = null;
            if (biomeSurfaceOres.contains(y)) {
                ore = biomeSurfaceOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            if (ore == null && regionSurfaceOres.contains(y)) {
                ore = regionSurfaceOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            if (ore == null && dimensionSurfaceOres.contains(y)) {
                ore = dimensionSurfaceOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            return ore;
        }

        private NativeBlockState undergroundOre(int realX, int y, int realZ) {
            NativeBlockState ore = null;
            if (biomeUndergroundOres.contains(y)) {
                ore = biomeUndergroundOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            if (ore == null && regionUndergroundOres.contains(y)) {
                ore = regionUndergroundOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            if (ore == null && dimensionUndergroundOres.contains(y)) {
                ore = dimensionUndergroundOres.generate(realX, y, realZ, rng, data, oreEngine);
            }
            return ore;
        }

        private void paintUpper(int realX, int realZ) {
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
                    put(y, bedrock);
                    continue;
                }
                if (!upperColumn.isSolid(y)) {
                    put(y, air);
                    continue;
                }
                int faceY = upperColumn.faceY(y);
                int sourceSurfaceY = upperColumn.height() - 1 - faceY;
                if (sourceSurfaceY != paletteSourceY) {
                    paletteSourceY = sourceSurfaceY;
                    upperBlocks = null;
                }
                if (y == faceY && upperMappedSurface != null) {
                    put(y, upperMappedSurface);
                    continue;
                }
                int depthFromFace = y - faceY;
                if (upperBlocks == null && upperBiome != null) {
                    upperBlocks = upperBiome.generateLayersWithSlope(upperContext.getDimension(),
                            realX, realZ, rng, sourceSurfaceY + 1 - (bedrockEnabled ? 1 : 0), sourceSurfaceY,
                            upperContext.getData(), upperContext.getSurfaceSlopeStream(sourceSurfaceY));
                }
                if (upperBlocks != null && upperBlocks.hasIndex(depthFromFace)) {
                    put(y, upperBlocks.get(depthFromFace));
                } else {
                    put(y, upperRock);
                }
            }
        }
    }
}
