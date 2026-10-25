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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.subterrain.SubterrainSurfaceMaterials;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.SubterrainChunkCells;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.runtime.EngineAssignedModifier;
import art.arcane.iris.generation.mantle.CaveCeilingMask;
import art.arcane.iris.generation.mantle.CaveTerrainSnapshot;
import art.arcane.iris.generation.mantle.TerrainMatterView;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveStorage;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.chunk.ColumnExtent;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Data;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.HashMap;
import java.util.Map;

public class IrisCarveModifier extends EngineAssignedModifier<NativeBlockState> {
    private static final byte LIQUID_FLUID = 1;
    private static final ThreadLocal<IrisCarveScratch> SCRATCH = ThreadLocal.withInitial(IrisCarveScratch::new);
    private static final int CAVE_BIOME_BLEND_RADIUS = 3;
    private static final int CAVE_BIOME_BLEND_CENTER_WEIGHT = 4;
    private static final int CAVE_BIOME_BLEND_TOTAL_WEIGHT = 8;
    private static final int SUBMERGED_FLOOR_SUBSTRATE_DEPTH = 8;
    private static final long RANDOM_MULTIPLIER = 0x5DEECE66DL;
    private static final long RANDOM_ADDEND = 0xBL;
    private static final long RANDOM_MASK = (1L << 48) - 1;
    private static final MatterCavern BASIC_CAVERN = new MatterCavern(true, "", (byte) 0);
    private final RNG rng;
    private final long caveBlendSeed;
    private final NativeBlockState AIR = B.getState("CAVE_AIR");
    private final NativeBlockState LAVA = B.getState("LAVA");
    private final IrisDecorantActuator decorant;

    public IrisCarveModifier(Engine engine) {
        super(engine, "Carve");
        caveBlendSeed = getEngine().getSeedManager().getCarve();
        rng = new RNG(caveBlendSeed);
        decorant = new IrisDecorantActuator(engine);
    }

    @Override
    @ChunkCoordinates
    public void onModify(int x, int z, Hunk<NativeBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch caveStopwatch = PrecisionStopwatch.start();
        Mantle<Matter> mantle = getEngine().getMantle().getMantle();
        CaveInputs resolverState = CaveInputs.forTerrainChunk(getEngine(), x, z);
        IrisCarveScratch scratch = SCRATCH.get();
        scratch.reset();
        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = scratch.caveBiomeCache;
        CarveWallBuffer walls = scratch.walls;
        CarveColumnMask[] columnMasks = scratch.columnMasks;
        CarveColumnMask[] boundaryMasks = scratch.boundaryMasks;
        int[] surfaceHeights = scratch.surfaceHeights;
        Map<String, IrisBiome> customBiomeCache = scratch.customBiomeCache;
        UpperDimensionContext upperCtx = getEngine().getUpperContext();
        boolean protectUpper = upperCtx != null && !getEngine().getDimension().isUpperDimensionCarving();
        int[] upperSurfaceHeights = protectUpper ? scratch.getOrCreateUpperSurfaceHeights() : null;
        DimensionStackContext stackContext = getEngine().getDimensionStackContext();
        DimensionStackLayout[] stackLayouts = stackContext == null ? null : new DimensionStackLayout[256];
        Terrain3DColumn[] terrainColumns = resolverState.complex.hasTerrain3D() ? new Terrain3DColumn[256] : null;
        int chunkBlockX = PowerOfTwoCoordinates.chunkToBlock(x);
        int chunkBlockZ = PowerOfTwoCoordinates.chunkToBlock(z);
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            surfaceHeights[columnIndex] = context.getRoundedHeight(localX, localZ);
            if (terrainColumns != null) {
                terrainColumns[columnIndex] = resolverState.complex.terrainColumn(chunkBlockX + localX, chunkBlockZ + localZ);
            }
            if (protectUpper) {
                int worldX = localX + chunkBlockX;
                int worldZ = localZ + chunkBlockZ;
                upperSurfaceHeights[columnIndex] = upperCtx.getEffectiveSurfaceY(worldX, worldZ);
            }
            if (stackLayouts != null) {
                stackLayouts[columnIndex] = context.getDimensionStackLayout(localX, localZ);
            }
        }

        MantleChunk<Matter> mantleChunk = mantle.useChunk(x, z);
        try {
            PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
            CaveTerrainSnapshot terrain = CaveTerrainSnapshot.capture(mantleChunk, x, z);
            if (context != null) {
                context.setCaveTerrain(terrain);
            }
            int worldHeightSpan = getEngine().getWorld().maxHeight() - getEngine().getWorld().minHeight();
            int caveLavaHeight = getEngine().getDimension().getCaveLavaHeight();
            SubterrainPlanner subterrainPlanner = resolverState.complex.getSubterrainPlanner();
            boolean subterrain = subterrainPlanner != null && !subterrainPlanner.isEmpty();
            CarveResolutionContext resolutionContext = new CarveResolutionContext(
                    output,
                    context,
                    scratch,
                    columnMasks,
                    upperSurfaceHeights,
                    stackLayouts,
                    terrainColumns,
                    worldHeightSpan,
                    caveLavaHeight,
                    chunkBlockX,
                    chunkBlockZ,
                    getEngine().getWorld().minHeight(),
                    resolverState.complex,
                    subterrainPlanner == null ? null : subterrainPlanner.chunkSampler(x, z),
                    subterrain ? caveCeilings(resolverState.complex, x, z) : null
            );
            CarveResolver carveResolver = new CarveResolver(resolutionContext);
            terrain.forEachCell(carveResolver::apply);
            if (scratch.customCaveBiomePresent) {
                addInternalWallsFromMantle(terrain, walls, columnMasks);
            } else {
                collectInternalWalls(scratch.wallMasks, columnMasks);
            }
            addCrossChunkBoundaryWalls(mantle, terrain, walls, boundaryMasks, x, z, surfaceHeights);
            getEngine().getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());

            PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
            try {
                WallPainter painter = new WallPainter(output, terrain, resolverState, caveBiomeCache, customBiomeCache,
                        surfaceHeights, getData(), chunkBlockX, chunkBlockZ);
                if (!scratch.customCaveBiomePresent) {
                    paintInternalWalls(painter, scratch.wallMasks, boundaryMasks);
                }
                walls.forEach(painter);

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    processColumnFromMask(
                            output,
                            terrain,
                            columnMasks[columnIndex],
                            columnIndex,
                            x,
                            z,
                            resolverState,
                            caveBiomeCache,
                            customBiomeCache,
                            scratch.layers
                    );
                }

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    if (boundaryMasks[columnIndex].isEmpty() || !columnMasks[columnIndex].isEmpty()) {
                        continue;
                    }
                    processBoundaryColumnFromMask(
                            output,
                            terrain,
                            boundaryMasks[columnIndex],
                            walls,
                            columnIndex,
                            x,
                            z,
                            resolverState,
                            caveBiomeCache,
                            customBiomeCache,
                            scratch.layers
                    );
                }

                // Surface-break carving must not leave an ore cap suspended across the opening.
                clearUnsupportedSurfaceOres(output, surfaceHeights, columnMasks, boundaryMasks,
                        CaveCeilingMask.ready(context.waterRaster(), x, z), AIR);
                restoreSubterrainSolids(output, x, z, false, (worldX, y, worldZ) -> terrain.hydrology(worldX & 15, y, worldZ & 15));
            } finally {
                getEngine().getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
            }
        } finally {
            getEngine().getMetrics().getCave().put(caveStopwatch.getMilliseconds());
            mantleChunk.release();
        }
    }

    static void clearUnsupportedSurfaceOres(Hunk<NativeBlockState> output, int[] surfaceHeights, CarveColumnMask[] columnMasks,
                                            CarveColumnMask[] boundaryMasks, ChunkWaterRaster water, NativeBlockState air) {
        for (int columnIndex = 0; columnIndex < surfaceHeights.length; columnIndex++) {
            int surfaceY = surfaceHeights[columnIndex];
            if (surfaceY <= 0 || surfaceY >= output.getHeight()) {
                continue;
            }
            int belowY = surfaceY - 1;
            if (!columnMasks[columnIndex].contains(belowY) && !boundaryMasks[columnIndex].contains(belowY)) {
                continue;
            }
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            if (surfaceY > water.caveCeiling(localX, localZ)) {
                continue;
            }
            if (isUnsupportedSurfaceOre(output.getRaw(localX, surfaceY, localZ), output.getRaw(localX, belowY, localZ))) {
                output.setRaw(localX, surfaceY, localZ, air);
            }
        }
    }

    public void preserveSubterrain(int blockX, int blockZ, Hunk<NativeBlockState> output) {
        SubterrainPlanner planner = getComplex().getSubterrainPlanner();
        if (planner == null || planner.isEmpty()) {
            return;
        }
        int minimumY = getEngine().getWorld().minHeight();
        SubterrainSurfaceMaterials materials = subterrainMaterials(planner, blockX >> 4, blockZ >> 4);
        int[] caveCeilings = caveCeilings(getComplex(), blockX >> 4, blockZ >> 4);
        MantleChunk<Matter> chunk = getEngine().getMantle().getMantle().useChunk(blockX >> 4, blockZ >> 4);
        try {
            preserveSubterrain(planner, materials, caveCeilings, minimumY, blockX, blockZ, output,
                    (worldX, y, worldZ) -> HydrologyCaveStorage.getIfPresent(chunk, worldX, y, worldZ));
        } finally {
            chunk.release();
        }
    }

    private void preserveSubterrain(SubterrainPlanner planner, SubterrainSurfaceMaterials materials, int[] caveCeilings, int minimumY,
                                    int blockX, int blockZ, Hunk<NativeBlockState> output, HydrologyCells hydrology) {
        SubterrainRasterizer.rasterize(planner, blockX >> 4, blockZ >> 4, (worldX, worldY, worldZ, cell) -> {
            int y = worldY - minimumY;
            if (y <= 0 || y >= output.getHeight()) {
                return;
            }
            int x = worldX & 15;
            int z = worldZ & 15;
            if (!cell.solid() && caveCeilings != null && y > caveCeilings[PowerOfTwoCoordinates.packLocal16(x, z)]) {
                return;
            }
            if (hydrology.at(worldX, y, worldZ) != null) {
                return;
            }
            NativeBlockState existing = output.getRaw(x, y, z);
            if (cell.solid() && !cell.room().reservedSolid() && (existing == null || !existing.isSolid())) {
                return;
            }
            if (materials.retainsFloorSupport(worldX, worldY, worldZ, cell, existing)) {
                return;
            }
            NativeBlockState planned = materials.state(worldX, worldY, worldZ, cell);
            if (cell.solid() || cell.room().reservedPassage() || cell.fluid() && (existing == null || B.isAir(existing) || B.isFluid(existing))) {
                output.setRaw(x, y, z, planned);
            } else {
                output.setRaw(x, y, z, IrisProceduralBlocks.normalizeWaterlogging(existing, cell.kind() == SubterrainCell.Kind.WATER ? planned : null, true));
            }
        });
    }

    static int[] caveCeilings(IrisComplex complex, int chunkX, int chunkZ) {
        int[] ceilings = new int[256];
        return CaveCeilingMask.fill(chunkX, chunkZ, complex.getHydrologyRuntime()::raster,
                new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD], ceilings) ? ceilings : null;
    }

    private void restoreSubterrainSolids(Hunk<NativeBlockState> output, int chunkX, int chunkZ, boolean preserveFloorSupports,
                                         HydrologyCells hydrology) {
        SubterrainPlanner planner = getComplex().getSubterrainPlanner();
        if (planner == null || planner.isEmpty()) {
            return;
        }
        int minimumY = getEngine().getWorld().minHeight();
        SubterrainSurfaceMaterials materials = subterrainMaterials(planner, chunkX, chunkZ);
        SubterrainRasterizer.rasterize(planner, chunkX, chunkZ, (worldX, worldY, worldZ, cell) -> {
            int y = worldY - minimumY;
            if (!cell.solid() || y <= 0 || y >= output.getHeight()) {
                return;
            }
            if (hydrology.at(worldX, y, worldZ) != null) {
                return;
            }
            NativeBlockState existing = output.getRaw(worldX & 15, y, worldZ & 15);
            if (preserveFloorSupports && materials.retainsFloorSupport(worldX, worldY, worldZ, cell, existing)) {
                return;
            }
            if (cell.room().reservedSolid() || existing != null && existing.isSolid()) {
                output.setRaw(worldX & 15, y, worldZ & 15, materials.state(worldX, worldY, worldZ, cell));
            }
        });
    }

    private SubterrainSurfaceMaterials subterrainMaterials(SubterrainPlanner planner, int chunkX, int chunkZ) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        return new SubterrainSurfaceMaterials(planner,
                planner.plansForBounds(blockX - 1, blockZ - 1, blockX + 16, blockZ + 16),
                getEngine().getDimension(), getComplex(), getData(), rng, getEngine().getWorld().minHeight());
    }

    static boolean hasExplicitCarveIntent(MatterCavern cavern) {
        return cavern != null && (isFluidIntent(cavern) || cavern.isLava() || cavern.getLiquid() == 3);
    }

    static boolean isFluidIntent(MatterCavern cavern) {
        return cavern != null && cavern.getLiquid() == LIQUID_FLUID;
    }

    static boolean shouldPreserveExistingFluid(MatterCavern cavern, NativeBlockState current) {
        return B.isFluid(current) && !hasExplicitCarveIntent(cavern);
    }

    static boolean usesDefaultLava(int caveLavaHeight, int y) {
        return y <= caveLavaHeight;
    }

    static boolean shouldSkipEmptyCarve(NativeBlockState current, boolean explicitCarveIntent) {
        return !explicitCarveIntent && (current == null || current.isAir());
    }

    static boolean isUnsupportedSurfaceOre(NativeBlockState surface, NativeBlockState below) {
        return B.isOre(surface) && !B.isSolid(below);
    }

    static NativeBlockState resolveExplicitCarveState(MatterCavern cavern, NativeBlockState fluid,
                                                        NativeBlockState lava, NativeBlockState air) {
        if (cavern == null) {
            return null;
        }
        if (isFluidIntent(cavern)) {
            return fluid;
        }
        if (cavern.isLava()) {
            return lava;
        }
        return cavern.getLiquid() == 3 ? air : null;
    }

    static boolean subterrainOwns(SubterrainCell feature, HydrologyCaveCell hydrology) {
        return hydrology == null && feature.owned() && (!feature.solid() || feature.room().reservedSolid());
    }

    static NativeBlockState sealGuardState(NativeBlockState current, NativeBlockState rock) {
        if (B.isFluid(current) || B.isWaterLogged(current)) {
            return normalizeWaterlogging(current, null);
        }
        if (rock != null && (B.isAir(current) || !current.isSolid() || IrisProceduralBlocks.isGravityAffected(current))) {
            return rock;
        }
        return normalizeWaterlogging(current, null);
    }

    static MatterCavern composeCavern(MatterCavern baseline, HydrologyCaveCell hydrology) {
        return hydrology == null ? baseline : hydrology.asCavern();
    }

    static NativeBlockState resolveHydrologyState(
            HydrologyCaveCell hydrology,
            NativeBlockState current,
            NativeBlockState fluid,
            NativeBlockState air
    ) {
        if (hydrology == null) {
            return null;
        }
        return switch (hydrology.action()) {
            case WET_SOURCE, POOL_SOURCE -> fluid;
            case FALLING_FLUID -> fallingFluidState(fluid);
            case DRY_AIR -> air;
            case SEAL_GUARD -> normalizeWaterlogging(current, null);
        };
    }

    static NativeBlockState normalizeWaterlogging(NativeBlockState state, NativeBlockState resultingFluid) {
        if (state == null || B.isFluid(state) || !IrisProceduralBlocks.hasProperty(state, "waterlogged")) {
            return state;
        }
        String target = resultingFluid != null && resultingFluid.isWater() ? "true" : "false";
        if (target.equals(IrisProceduralBlocks.propertyValue(state, "waterlogged"))) {
            return state;
        }
        return state.withProperty("waterlogged", target);
    }

    static NativeBlockState normalizeHydrologyWaterlogging(
            NativeBlockState state,
            MatterCavern baseline,
            HydrologyCaveCell hydrology,
            NativeBlockState columnFluid
    ) {
        if (hydrology == null) {
            return state;
        }
        MatterCavern composed = composeCavern(baseline, hydrology);
        NativeBlockState resultingFluid = isFluidIntent(composed) ? columnFluid : null;
        return normalizeWaterlogging(state, resultingFluid);
    }

    private static NativeBlockState fallingFluidState(NativeBlockState fluid) {
        if (fluid == null || !IrisProceduralBlocks.hasProperty(fluid, "level")) {
            return fluid;
        }
        if ("8".equals(IrisProceduralBlocks.propertyValue(fluid, "level"))) {
            return fluid;
        }
        return fluid.withProperty("level", "8");
    }

    private final class CarveResolver {
        private final CarveResolutionContext context;

        private CarveResolver(CarveResolutionContext context) {
            this.context = context;
        }

        private void apply(
                int x,
                int y,
                int z,
                MatterCavern baseline,
                HydrologyCaveCell hydrology
        ) {
            if (y >= context.worldHeightSpan() || y <= 0) {
                return;
            }

            int localX = x & 15;
            int localZ = z & 15;
            int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
            SubterrainCell feature = context.subterrainSampler() == null ? SubterrainCell.OUTSIDE
                    : context.subterrainSampler().sampleCandidates(context.chunkBlockX() + localX, y + context.minimumY(),
                    context.chunkBlockZ() + localZ);
            if (subterrainOwns(feature, hydrology)) {
                if (!feature.solid() && context.caveCeilings() != null && y > context.caveCeilings()[columnIndex]) {
                    return;
                }
                context.output().setRaw(localX, y, localZ, SubterrainRasterizer.state(feature));
                if (feature.carve()) {
                    context.columnMasks()[columnIndex].add(y);
                    if (!feature.room().biome().isEmpty()) {
                        context.scratch().customCaveBiomePresent = true;
                    }
                }
                return;
            }

            if (context.upperSurfaceHeights() != null && y >= context.upperSurfaceHeights()[columnIndex]) {
                return;
            }
            if (context.stackLayouts() != null
                    && context.stackLayouts()[columnIndex].isHostFeatureProtectedY(y)) {
                return;
            }

            NativeBlockState current = context.output().getRaw(localX, y, localZ);
            if (hydrology == null && context.terrainColumns() != null) {
                Terrain3DColumn column = context.terrainColumns()[columnIndex];
                if (column != null && y >= column.minY() && y < column.topY() && !column.isSolid(y)) {
                    return;
                }
            }
            if (hydrology != null && hydrology.action() == HydrologyCaveAction.SEAL_GUARD) {
                NativeBlockState sealed = sealGuardState(current, context.chunkContext().getRock().get(localX, localZ));
                if (sealed != current) {
                    context.output().setRaw(localX, y, localZ, sealed);
                }
                return;
            }

            MatterCavern cavern = composeCavern(baseline, hydrology);
            if (cavern == null || shouldPreserveExistingFluid(cavern, current)) {
                return;
            }

            context.columnMasks()[columnIndex].add(y);
            if (!cavern.getCustomBiome().isEmpty()) {
                context.scratch().customCaveBiomePresent = true;
            }

            boolean explicitCarveIntent = hasExplicitCarveIntent(cavern);
            if (shouldSkipEmptyCarve(current, explicitCarveIntent)) {
                return;
            }

            NativeBlockState fluid = null;
            if (isFluidIntent(cavern)) {
                fluid = hydrology == null
                        ? context.chunkContext().getFluid().get(localX, localZ)
                        : context.complex().resolveCaveCellFluid(
                                hydrology.fluidProfileKey(),
                                context.chunkBlockX() + localX,
                                context.chunkBlockZ() + localZ
                        );
            }
            if (hydrology != null) {
                context.output().setRaw(localX, y, localZ,
                        resolveHydrologyState(hydrology, current, fluid, AIR));
                return;
            }
            if (explicitCarveIntent) {
                context.output().setRaw(localX, y, localZ,
                        resolveExplicitCarveState(cavern, fluid, LAVA, AIR));
            } else if (usesDefaultLava(context.caveLavaHeight(), y)) {
                context.output().setRaw(localX, y, localZ, LAVA);
            } else {
                context.output().setRaw(localX, y, localZ, AIR);
            }
        }
    }

    private record CarveResolutionContext(
            Hunk<NativeBlockState> output,
            ChunkContext chunkContext,
            IrisCarveScratch scratch,
            CarveColumnMask[] columnMasks,
            int[] upperSurfaceHeights,
            DimensionStackLayout[] stackLayouts,
            Terrain3DColumn[] terrainColumns,
            int worldHeightSpan,
            int caveLavaHeight,
            int chunkBlockX,
            int chunkBlockZ,
            int minimumY,
            IrisComplex complex,
            SubterrainPlanner.ChunkSampler subterrainSampler,
            int[] caveCeilings
    ) {
    }

    static void collectInternalWalls(CarveColumnMask[] wallMasks, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            wallMasks[columnIndex].collectWalls(
                    columnMasks[columnIndex],
                    rz > 0 ? columnMasks[columnIndex - 1] : null,
                    rz < 15 ? columnMasks[columnIndex + 1] : null,
                    rx > 0 ? columnMasks[columnIndex - 16] : null,
                    rx < 15 ? columnMasks[columnIndex + 16] : null
            );
        }
    }

    private static void paintInternalWalls(WallPainter painter, CarveColumnMask[] wallMasks, CarveColumnMask[] boundaryMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask wallMask = wallMasks[columnIndex];
            if (wallMask.isEmpty()) {
                continue;
            }
            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            CarveColumnMask boundaryMask = boundaryMasks[columnIndex];
            for (int yy = wallMask.nextSetBit(0); yy >= 0; yy = wallMask.nextSetBit(yy + 1)) {
                if (!boundaryMask.contains(yy)) {
                    painter.accept(rx, yy, rz, BASIC_CAVERN);
                }
            }
        }
    }

    private final class WallPainter implements CarveWallBuffer.Consumer {
        private final Hunk<NativeBlockState> output;
        private final CaveTerrainSnapshot terrain;
        private final CaveInputs inputs;
        private final Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache;
        private final Map<String, IrisBiome> customBiomeCache;
        private final int[] surfaceHeights;
        private final IrisData data;
        private final int chunkBlockX;
        private final int chunkBlockZ;

        private WallPainter(Hunk<NativeBlockState> output, CaveTerrainSnapshot terrain, CaveInputs inputs,
                            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache,
                            int[] surfaceHeights, IrisData data, int chunkBlockX, int chunkBlockZ) {
            this.output = output;
            this.terrain = terrain;
            this.inputs = inputs;
            this.caveBiomeCache = caveBiomeCache;
            this.customBiomeCache = customBiomeCache;
            this.surfaceHeights = surfaceHeights;
            this.data = data;
            this.chunkBlockX = chunkBlockX;
            this.chunkBlockZ = chunkBlockZ;
        }

        @Override
        public void accept(int rx, int yy, int rz, MatterCavern cavern) {
            HydrologyCaveCell hydrology = terrain.hydrology(rx, yy, rz);
            int worldX = rx + chunkBlockX;
            int worldZ = rz + chunkBlockZ;
            if (hydrology != null && hydrology.protectsPlacement() || inputs.cells.cell(worldX, yy, worldZ).owned()
                    || yy >= surfaceHeights[PowerOfTwoCoordinates.packLocal16(rx, rz)] || !B.isSolid(output.getRaw(rx, yy, rz))) {
                return;
            }
            String customBiome = cavern.getCustomBiome();
            IrisBiome biome = customBiome.isEmpty()
                    ? resolveCaveBiome(caveBiomeCache, worldX, yy, worldZ, inputs)
                    : resolveCustomBiome(customBiomeCache, customBiome);
            if (biome == null) {
                return;
            }
            NativeBlockState wall = biome.getWall().get(rng, worldX, yy, worldZ, data);
            if (wall != null) {
                output.setRaw(rx, yy, rz, wall);
            }
        }
    }

    private void addInternalWallsFromMantle(CaveTerrainSnapshot terrain, CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask columnMask = columnMasks[columnIndex];
            if (columnMask.isEmpty()) {
                continue;
            }

            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            int yy = columnMask.nextSetBit(0);
            while (yy >= 0) {
                MatterCavern cavern = terrain.composedCavern(rx, yy, rz);
                if (cavern != null) {
                    if (rz < 15 && terrain.composedCavern(rx, yy, rz + 1) == null) {
                        walls.put(rx, yy, rz + 1, cavern);
                    }
                    if (rx < 15 && terrain.composedCavern(rx + 1, yy, rz) == null) {
                        walls.put(rx + 1, yy, rz, cavern);
                    }
                    if (rz > 0 && terrain.composedCavern(rx, yy, rz - 1) == null) {
                        walls.put(rx, yy, rz - 1, cavern);
                    }
                    if (rx > 0 && terrain.composedCavern(rx - 1, yy, rz) == null) {
                        walls.put(rx - 1, yy, rz, cavern);
                    }
                }
                yy = columnMask.nextSetBit(yy + 1);
            }
        }
    }

    private void addCrossChunkBoundaryWalls(
            Mantle<Matter> mantle,
            CaveTerrainSnapshot terrain,
            CarveWallBuffer walls,
            CarveColumnMask[] boundaryMasks,
            int chunkX,
            int chunkZ,
            int[] surfaceHeights
    ) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int maxSurfaceY = 0;
        for (int index = 0; index < surfaceHeights.length; index++) {
            if (surfaceHeights[index] > maxSurfaceY) {
                maxSurfaceY = surfaceHeights[index];
            }
        }
        int maxY = Math.min(getEngine().getWorld().maxHeight() - getEngine().getWorld().minHeight() - 1, maxSurfaceY + 1);
        if (maxY < 1) {
            return;
        }

        MantleChunk<Matter> west = existingMantleChunk(mantle, chunkX - 1, chunkZ);
        MantleChunk<Matter> east = existingMantleChunk(mantle, chunkX + 1, chunkZ);
        MantleChunk<Matter> north = existingMantleChunk(mantle, chunkX, chunkZ - 1);
        MantleChunk<Matter> south = existingMantleChunk(mantle, chunkX, chunkZ + 1);
        if (west == null && east == null && north == null && south == null) {
            return;
        }

        int height = maxY + 1;
        MatterCavern[] fromWest = west == null ? null : TerrainMatterView.getComposedFace(west, TerrainMatterView.Face.EAST, height);
        MatterCavern[] fromEast = east == null ? null : TerrainMatterView.getComposedFace(east, TerrainMatterView.Face.WEST, height);
        MatterCavern[] fromNorth = north == null ? null : TerrainMatterView.getComposedFace(north, TerrainMatterView.Face.SOUTH, height);
        MatterCavern[] fromSouth = south == null ? null : TerrainMatterView.getComposedFace(south, TerrainMatterView.Face.NORTH, height);
        for (int yy = 1; yy <= maxY; yy++) {
            for (int offset = 0; offset < 16; offset++) {
                int index = yy * 16 + offset;
                if (west != null) {
                    tryAddBoundaryWall(terrain.composedCavern(0, yy, offset), fromWest[index], walls, boundaryMasks, 0, yy, offset);
                }
                if (east != null) {
                    tryAddBoundaryWall(terrain.composedCavern(15, yy, offset), fromEast[index], walls, boundaryMasks, 15, yy, offset);
                }
                if (north != null) {
                    tryAddBoundaryWall(terrain.composedCavern(offset, yy, 0), fromNorth[index], walls, boundaryMasks, offset, yy, 0);
                }
                if (south != null) {
                    tryAddBoundaryWall(terrain.composedCavern(offset, yy, 15), fromSouth[index], walls, boundaryMasks, offset, yy, 15);
                }
            }
        }
    }

    private void tryAddBoundaryWall(
            MatterCavern current,
            MatterCavern neighbor,
            CarveWallBuffer walls,
            CarveColumnMask[] boundaryMasks,
            int localX,
            int yy,
            int localZ
    ) {
        if (current != null || neighbor == null) {
            return;
        }
        walls.put(localX, yy, localZ, neighbor);
        int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
        boundaryMasks[columnIndex].add(yy);
    }

    private MantleChunk<Matter> existingMantleChunk(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        TectonicPlate<Matter> plate = mantle.getLoadedRegion(chunkX >> 5, chunkZ >> 5);
        if (plate == null || plate.isClosed()) {
            return null;
        }
        return plate.get(chunkX & 31, chunkZ & 31);
    }

    private void processColumnFromMask(
            Hunk<NativeBlockState> output,
            CaveTerrainSnapshot terrain,
            CarveColumnMask columnMask,
            int columnIndex,
            int chunkX,
            int chunkZ,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache,
            CaveLayerMemo layers
    ) {
        if (columnMask == null || columnMask.isEmpty()) {
            return;
        }

        int firstHeight = columnMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
        int rz = columnIndex & 15;
        int worldX = rx + PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int worldZ = rz + PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int height = resolverState.height;
        layers.reset();
        int floor = firstHeight;
        while (floor >= 0 && floor <= height) {
            int end = columnMask.nextClearBit(floor) - 1;
            CaveZone zone = new CaveZone();
            zone.setFloor(floor);
            zone.setCeiling(Math.min(end, height));
            if (zone.isValid(height)) {
                processZone(output, terrain, zone, rx, rz, worldX, worldZ, resolverState,
                        caveBiomeCache, customBiomeCache, layers);
            }
            floor = columnMask.nextSetBit(end + 1);
        }
    }

    private void processBoundaryColumnFromMask(
            Hunk<NativeBlockState> output,
            CaveTerrainSnapshot terrain,
            CarveColumnMask boundaryMask,
            CarveWallBuffer walls,
            int columnIndex,
            int chunkX,
            int chunkZ,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache,
            CaveLayerMemo layers
    ) {
        int firstHeight = boundaryMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
        int rz = columnIndex & 15;
        int worldX = rx + PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int worldZ = rz + PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        layers.reset();
        int zoneFloor = firstHeight;
        int zoneCeiling = firstHeight;
        int y = boundaryMask.nextSetBit(firstHeight + 1);

        while (y >= 0) {
            if (y == zoneCeiling + 1) {
                zoneCeiling = y;
            } else {
                paintBoundaryZone(output, terrain, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling,
                        resolverState, caveBiomeCache, customBiomeCache, layers);
                zoneFloor = y;
                zoneCeiling = y;
            }
            y = boundaryMask.nextSetBit(y + 1);
        }

        paintBoundaryZone(output, terrain, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling,
                resolverState, caveBiomeCache, customBiomeCache, layers);
    }

    private void paintBoundaryZone(
            Hunk<NativeBlockState> output,
            CaveTerrainSnapshot terrain,
            CarveWallBuffer walls,
            int rx,
            int rz,
            int worldX,
            int worldZ,
            int zoneFloor,
            int zoneCeiling,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache,
            CaveLayerMemo layers
    ) {
        IrisBiome floorBiome = resolveCaveBoundaryBiome(
                walls.get(rx, zoneFloor, rz), worldX, zoneFloor, worldZ,
                resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceilingBiome = resolveCaveBoundaryBiome(
                walls.get(rx, zoneCeiling, rz), worldX, zoneCeiling, worldZ,
                resolverState, caveBiomeCache, customBiomeCache);
        if (floorBiome == null && ceilingBiome == null) {
            return;
        }

        if (floorBiome != null) {
            HydrologyCaveCell floorHydrology = terrain.hydrology(rx, zoneFloor, rz);
            KList<NativeBlockState> floorLayers = floorLayers(layers, floorBiome, worldX, worldZ, zoneFloor, resolverState);
            for (int i = 0; i < zoneFloor - 1; i++) {
                if (!floorLayers.hasIndex(i)) {
                    break;
                }
                int floorY = zoneFloor - i - 1;
                if (floorY < 0) {
                    break;
                }
                HydrologyCaveCell hydrology = terrain.hydrology(rx, floorY, rz);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState existing = output.getRaw(rx, floorY, rz);
                NativeBlockState layer = resolveSubmergedCaveFloorLayer(
                        output, rx, floorY, rz, floorLayers.get(i), floorHydrology);
                if (!B.isSolid(existing)
                        || !canReplaceHydrologyGuard(hydrology, layer)
                        || !canReplaceCaveFloorLayer(output, rx, floorY, rz, layer)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, floorY, rz, B.toDeepSlateOre(existing, layer));
                    continue;
                }
                output.setRaw(rx, floorY, rz, layer);
            }
        }

        if (ceilingBiome != null) {
            int worldMaxY = getEngine().getWorld().maxHeight() - getEngine().getWorld().minHeight();
            KList<NativeBlockState> ceilingLayers = ceilingLayers(layers, ceilingBiome, worldX, worldZ, zoneCeiling, resolverState);
            for (int i = 0; i < ceilingLayers.size(); i++) {
                int ceilingY = zoneCeiling + i + 1;
                if (ceilingY >= worldMaxY) {
                    break;
                }
                HydrologyCaveCell hydrology = terrain.hydrology(rx, ceilingY, rz);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState existing = output.getRaw(rx, ceilingY, rz);
                if (!B.isSolid(existing)) {
                    continue;
                }
                NativeBlockState layer = ceilingLayers.get(i);
                if (!canReplaceHydrologyGuard(hydrology, layer)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, ceilingY, rz, B.toDeepSlateOre(existing, layer));
                    continue;
                }
                output.setRaw(rx, ceilingY, rz, layer);
            }
        }
    }

    /**
     * 1-in-16 marker roll from a SplitMix64 finalizer over (carve seed, block position, salt).
     * Deterministic per seed and position, thread-order independent, allocation free.
     */
    private boolean markerRoll(int x, int y, int z, long salt) {
        long h = (getEngine().getSeedManager().getCarve() + salt) ^ BlockPosition.toLong(x, y, z);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h & 15L) == 0L;
    }

    private void processZone(Hunk<NativeBlockState> output, CaveTerrainSnapshot terrain,
                             CaveZone zone, int rx, int rz, int xx, int zz,
                             CaveInputs resolverState,
                             Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
                             Map<String, IrisBiome> customBiomeCache,
                             CaveLayerMemo layers) {
        int maxY = output.getHeight();

        if (zone.ceiling + 1 < maxY && B.isDecorant(output.getRaw(rx, zone.ceiling + 1, rz))) {
            output.setRaw(rx, zone.ceiling + 1, rz, AIR);
        }

        if (B.isDecorant(output.getRaw(rx, zone.ceiling, rz))) {
            output.setRaw(rx, zone.ceiling, rz, AIR);
        }

        IrisBiome floorBiome = resolveCaveBoundaryBiome(terrain, rx, zone.floor, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceilingBiome = resolveCaveBoundaryBiome(terrain, rx, zone.ceiling, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        if (floorBiome == null && ceilingBiome == null) {
            normalizeCaveZoneWaterlogging(output, terrain, zone, rx, rz, xx, zz);
            return;
        }

        if (floorBiome != null) {
            HydrologyCaveCell floorHydrology = terrain.hydrology(rx, zone.floor, rz);
            KList<NativeBlockState> floorBlocks = floorLayers(layers, floorBiome, xx, zz, zone.floor, resolverState);
            for (int i = 0; i < zone.floor - 1; i++) {
                if (!floorBlocks.hasIndex(i)) {
                    break;
                }
                int y = zone.floor - i - 1;
                HydrologyCaveCell hydrology = terrain.hydrology(rx, y, rz);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState block = resolveSubmergedCaveFloorLayer(
                        output, rx, y, rz, floorBlocks.get(i), floorHydrology);
                NativeBlockState existing = output.getRaw(rx, y, rz);
                if (!B.isSolid(existing)
                        || !canReplaceHydrologyGuard(hydrology, block)
                        || !canReplaceCaveFloorLayer(output, rx, y, rz, block)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, y, rz, B.toDeepSlateOre(existing, block));
                    continue;
                }
                output.setRaw(rx, y, rz, block);
            }
        }

        if (ceilingBiome != null) {
            KList<NativeBlockState> ceilingBlocks = ceilingLayers(layers, ceilingBiome, xx, zz, zone.ceiling, resolverState);
            for (int i = 0; i < ceilingBlocks.size(); i++) {
                int cy = zone.ceiling + i + 1;
                if (cy >= maxY) {
                    break;
                }
                HydrologyCaveCell hydrology = terrain.hydrology(rx, cy, rz);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState block = ceilingBlocks.get(i);
                NativeBlockState existing = output.getRaw(rx, cy, rz);
                if (!B.isSolid(existing) || !canReplaceHydrologyGuard(hydrology, block)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, cy, rz, B.toDeepSlateOre(existing, block));
                    continue;
                }
                output.setRaw(rx, cy, rz, block);
            }
        }

        normalizeCaveZoneWaterlogging(output, terrain, zone, rx, rz, xx, zz);
    }

    private KList<NativeBlockState> floorLayers(CaveLayerMemo layers, IrisBiome biome, int worldX, int worldZ, int floorY,
                                                CaveInputs inputs) {
        if (!hasHeightFreeFloorLayers(biome)) {
            return biome.generateLayers(inputs.dimension, worldX, worldZ, rng, 3, floorY, inputs.data, inputs.complex);
        }
        KList<NativeBlockState> remembered = layers.floor(biome);
        if (remembered != null) {
            return remembered;
        }
        KList<NativeBlockState> blocks = biome.generateLayers(inputs.dimension, worldX, worldZ, rng, 3, floorY, inputs.data, inputs.complex);
        layers.rememberFloor(biome, blocks);
        return blocks;
    }

    private KList<NativeBlockState> ceilingLayers(CaveLayerMemo layers, IrisBiome biome, int worldX, int worldZ, int ceilingY,
                                                  CaveInputs inputs) {
        KList<NativeBlockState> remembered = layers.ceiling(biome);
        if (remembered != null) {
            return remembered;
        }
        KList<NativeBlockState> blocks = biome.generateCeilingLayers(inputs.dimension, worldX, worldZ, rng, 3, ceilingY, inputs.data, inputs.complex);
        layers.rememberCeiling(biome, blocks);
        return blocks;
    }

    static boolean hasHeightFreeFloorLayers(IrisBiome biome) {
        if (biome.isLockLayers()) {
            return false;
        }
        KList<IrisBiomePaletteLayer> layers = biome.getLayers();
        for (int index = 0; index < layers.size(); index++) {
            if (!layers.get(index).getSlopeCondition().isDefault()) {
                return false;
            }
        }
        return true;
    }

    public void decorateNaturalCaves(int blockX, int blockZ, Hunk<NativeBlockState> output, ChunkContext context) {
        Mantle<Matter> mantle = getEngine().getMantle().getMantle();
        CaveTerrainSnapshot terrain = context == null ? null : context.getCaveTerrain();
        if (context != null) {
            context.setCaveTerrain(null);
        }
        if (terrain == null || !terrain.covers(blockX >> 4, blockZ >> 4)) {
            MantleChunk<Matter> chunk = mantle.useChunk(blockX >> 4, blockZ >> 4);
            try {
                terrain = CaveTerrainSnapshot.capture(chunk, blockX >> 4, blockZ >> 4);
            } finally {
                chunk.release();
            }
        }
        CaveInputs resolver = CaveInputs.forContentChunk(getEngine(), blockX >> 4, blockZ >> 4);
        boolean caveFloorMarkers = getDimension().hasUndergroundSpawners(getEngine());
        Long2ObjectOpenHashMap<IrisBiome> caveBiomes = new Long2ObjectOpenHashMap<>(256);
        Map<String, IrisBiome> customBiomes = new HashMap<>();
        int width = output.getWidth();
        int depth = output.getDepth();
        int height = output.getHeight();
        ColumnExtent extent = output instanceof ColumnExtent columnExtent ? columnExtent : null;
        SolidMemo solids = new SolidMemo();
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                int worldX = blockX + localX;
                int worldZ = blockZ + localZ;
                int floor = -1;
                int top = extent == null ? height - 1 : extent.highestStoredY(localX, localZ);
                boolean previousKnown = false;
                boolean previousSolid = false;
                for (int y = 1; y < height; y++) {
                    if (y > top) {
                        break;
                    }
                    boolean solid = solids.test(output.getRaw(localX, y, localZ));
                    boolean decorated = false;
                    if (solid) {
                        if (floor >= 0) {
                            CaveZone zone = new CaveZone();
                            zone.setFloor(floor);
                            zone.setCeiling(y - 1);
                            SubterrainCell floorCell = zone.isValid(resolver.height)
                                    ? resolver.cells.cell(worldX, floor, worldZ) : null;
                            if (floorCell != null && (floorCell.occupied()
                                    || !isNaturalTerrainOpening(resolver.complex, worldX, floor, worldZ, context))) {
                                if (caveFloorMarkers && markerRoll(worldX, zone.floor, worldZ, 0xC2B2AE3D27D4EB4FL)) {
                                    mantle.set(worldX, zone.floor, worldZ, MarkerMatter.CAVE_FLOOR);
                                }
                                IrisBiome floorBiome = resolveCaveBoundaryBiome(terrain, localX, floor, localZ,
                                        worldX, worldZ, resolver, caveBiomes, customBiomes);
                                IrisBiome ceilingBiome = resolveCaveBoundaryBiome(terrain, localX, y - 1, localZ,
                                        worldX, worldZ, resolver, caveBiomes, customBiomes);
                                decorateZone(output, zone, localX, localZ, worldX, worldZ,
                                        floorBiome, ceilingBiome, floorCell);
                                decorated = true;
                                if (extent != null) {
                                    top = extent.highestStoredY(localX, localZ);
                                }
                            }
                            floor = -1;
                        }
                    } else if (floor < 0 && (previousKnown ? previousSolid
                            : solids.test(output.getRaw(localX, y - 1, localZ)))) {
                        floor = y;
                    }
                    previousSolid = solid;
                    previousKnown = !decorated;
                }
            }
        }
        CaveTerrainSnapshot cells = terrain;
        restoreSubterrainSolids(output, blockX >> 4, blockZ >> 4, true, (worldX, y, worldZ) -> cells.hydrology(worldX & 15, y, worldZ & 15));
    }

    private void decorateZone(Hunk<NativeBlockState> output, CaveZone zone, int rx, int rz, int xx, int zz,
                              IrisBiome floorBiome, IrisBiome ceilingBiome, SubterrainCell feature) {
        boolean reservedPassage = feature.owned() && feature.room().reservedPassage();
        int maxY = output.getHeight();
        IrisDecorator[] surfaceDecorators = floorBiome == null
                ? new IrisDecorator[0]
                : floorBiome.getDecoratorBucket(IrisDecorationPart.NONE);
        if (!reservedPassage && surfaceDecorators.length > 0 && hasStableCaveFloorSupport(output, rx, zone.getFloor(), rz)) {
            decorant.getSurfaceDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, floorBiome, InferredType.CAVE, zone.getFloor() - 1, zone.airThickness());
        }

        IrisDecorator[] ceilingDecorators = ceilingBiome == null
                ? new IrisDecorator[0]
                : ceilingBiome.getDecoratorBucket(IrisDecorationPart.CEILING);
        int ceilingSpace = zone.airThickness() - (reservedPassage ? 3 : 0);
        if (ceilingSpace > 0 && ceilingDecorators.length > 0 && zone.getCeiling() + 1 < maxY && B.isSolid(output.getRaw(rx, zone.getCeiling() + 1, rz))) {
            decorant.getCeilingDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, ceilingBiome, InferredType.CAVE, zone.getCeiling(), ceilingSpace);
        }

    }

    private void normalizeCaveZoneWaterlogging(
            Hunk<NativeBlockState> output,
            CaveTerrainSnapshot terrain,
            CaveZone zone,
            int localX,
            int localZ,
            int worldX,
            int worldZ
    ) {
        if (!terrain.hasHydrology()) {
            return;
        }
        int minimumY = Math.max(0, zone.floor - 1);
        int maximumY = Math.min(output.getHeight() - 1, zone.ceiling + 1);
        for (int y = minimumY; y <= maximumY; y++) {
            HydrologyCaveCell hydrology = terrain.hydrology(localX, y, localZ);
            if (hydrology == null) {
                continue;
            }
            MatterCavern baseline = terrain.cavern(localX, y, localZ);
            NativeBlockState current = output.getRaw(localX, y, localZ);
            NativeBlockState columnFluid = getComplex().resolveCaveCellFluid(
                    hydrology.fluidProfileKey(),
                    worldX,
                    worldZ
            );
            NativeBlockState normalized = normalizeHydrologyWaterlogging(
                    current,
                    baseline,
                    hydrology,
                    columnFluid
            );
            if (normalized != current) {
                output.setRaw(localX, y, localZ, normalized);
            }
        }
    }

    IrisBiome resolveCaveBoundaryBiome(CaveTerrainSnapshot terrain, int x, int y, int z, int worldX, int worldZ, CaveInputs resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        MatterCavern cavern = terrain == null ? null : terrain.composedCavern(x, y, z);
        return resolveCaveBoundaryBiome(
                cavern, worldX, y, worldZ, resolverState, caveBiomeCache, customBiomeCache);
    }

    IrisBiome resolveCaveBoundaryBiome(MatterCavern cavern, int worldX, int y, int worldZ, CaveInputs resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        IrisBiome featureBiome = resolverState.cells.biome(worldX, y, worldZ);
        if (featureBiome != null) {
            return featureBiome;
        }
        if (isNaturalTerrainOpening(resolverState.complex, worldX, y, worldZ)) {
            return resolverState.complex.getTrueBiomeStream().get(worldX, worldZ);
        }
        if (cavern != null && !cavern.getCustomBiome().isEmpty()) {
            return resolveCustomBiome(customBiomeCache, cavern.getCustomBiome());
        }
        return resolveCaveBiome(caveBiomeCache, worldX, y, worldZ, resolverState);
    }

    private boolean isNaturalTerrainOpening(IrisComplex complex, int x, int y, int z) {
        if (complex.getTerrainTransform() == null) {
            return complex.isTerrain3DOpening(x, y, z);
        }
        if (!complex.hasTerrain3D()) {
            return false;
        }
        Terrain3DColumn column = naturalOpeningColumn(complex, x, z);
        return column != null && y >= column.minY() && y < column.topY() && !column.isSolid(y)
                && !getEngine().isAdditionalTerrainOwned(x, y, z);
    }

    static Terrain3DColumn naturalOpeningColumn(IrisComplex complex, int x, int z) {
        return complex.hydrologyOwnsTerrain(x, z) ? null : complex.naturalTerrainColumn(x, z);
    }

    private boolean isNaturalTerrainOpening(IrisComplex complex, int x, int y, int z, ChunkContext context) {
        ChunkContext.TransitionTerrainColumn transition = context == null ? null
                : context.getTransitionTerrain(x & 15, z & 15);
        if (transition == null) {
            return isNaturalTerrainOpening(complex, x, y, z);
        }
        Terrain3DColumn natural = naturalOpeningColumn(complex, x, z);
        return natural != null && IrisDecorantActuator.isTransitionTerrainOpening(natural, transition, y)
                && !getEngine().isAdditionalTerrainOwned(x, y, z);
    }

    static boolean canReplaceHydrologyGuard(HydrologyCaveCell hydrology, NativeBlockState layer) {
        if (hydrology == null || hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
            return true;
        }
        return layer != null
                && B.isSolid(layer)
                && !B.isFluid(layer)
                && !isGravityAffected(layer);
    }


    static boolean canReplaceCaveFloorLayer(Hunk<NativeBlockState> output, int x, int y, int z, NativeBlockState layer) {
        return !isGravityAffected(layer) || y > 0 && B.isSolid(output.getRaw(x, y - 1, z));
    }

    static NativeBlockState resolveSubmergedCaveFloorLayer(
            Hunk<NativeBlockState> output,
            int x,
            int y,
            int z,
            NativeBlockState layer,
            HydrologyCaveCell hydrologyAbove
    ) {
        if (hydrologyAbove == null || !hydrologyAbove.isWet() || !isVegetatedHydrologyBed(layer)) {
            return layer;
        }
        int minimumY = Math.max(0, y - SUBMERGED_FLOOR_SUBSTRATE_DEPTH);
        for (int substrateY = y - 1; substrateY >= minimumY; substrateY--) {
            NativeBlockState substrate = output.getRaw(x, substrateY, z);
            if (B.isSolid(substrate)
                    && !B.isFluid(substrate)
                    && !isVegetatedHydrologyBed(substrate)
                    && !isGravityAffected(substrate)) {
                return substrate;
            }
        }
        return layer;
    }

    private static boolean isVegetatedHydrologyBed(NativeBlockState state) {
        if (state == null) {
            return false;
        }
        String key = IrisProceduralBlocks.materialKey(state);
        return key.equals("minecraft:grass_block") || key.equals("minecraft:moss_block");
    }

    static boolean hasStableCaveFloorSupport(Hunk<NativeBlockState> output, int x, int floorY, int z) {
        if (floorY <= 0) {
            return false;
        }
        NativeBlockState support = output.getRaw(x, floorY - 1, z);
        if (!B.isSolid(support)) {
            return false;
        }
        return !isGravityAffected(support) || floorY > 1 && B.isSolid(output.getRaw(x, floorY - 2, z));
    }

    static boolean isGravityAffected(NativeBlockState state) {
        return IrisProceduralBlocks.isGravityAffected(state);
    }

    private IrisBiome resolveCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, CaveInputs resolverState) {
        IrisBiome center = sampleCaveBiome(caveBiomeCache, x, y, z, resolverState);
        if (center == null) {
            return null;
        }

        int roll = Math.floorMod(caveBlendRandom(caveBlendSeed, x, y, z), CAVE_BIOME_BLEND_TOTAL_WEIGHT);
        if (roll < CAVE_BIOME_BLEND_CENTER_WEIGHT) {
            return center;
        }
        roll -= CAVE_BIOME_BLEND_CENTER_WEIGHT;
        IrisBiome neighbor = switch (roll) {
            case 0 -> sampleCaveBiome(caveBiomeCache, x + CAVE_BIOME_BLEND_RADIUS, y, z, resolverState);
            case 1 -> sampleCaveBiome(caveBiomeCache, x - CAVE_BIOME_BLEND_RADIUS, y, z, resolverState);
            case 2 -> sampleCaveBiome(caveBiomeCache, x, y, z + CAVE_BIOME_BLEND_RADIUS, resolverState);
            default -> sampleCaveBiome(caveBiomeCache, x, y, z - CAVE_BIOME_BLEND_RADIUS, resolverState);
        };
        return neighbor != null ? neighbor : center;
    }

    static int caveBlendRandom(long seed, int x, int y, int z) {
        long scrambled = ((seed + BlockPosition.toLong(x, y, z)) ^ RANDOM_MULTIPLIER) & RANDOM_MASK;
        return (int) (((scrambled * RANDOM_MULTIPLIER + RANDOM_ADDEND) & RANDOM_MASK) >>> 16);
    }

    private IrisBiome sampleCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, CaveInputs resolverState) {
        if (resolverState.captures(x, z)) {
            return resolverState.resolve(x, y, z);
        }
        long key = BlockPosition.toLong(x, y, z);
        IrisBiome cachedBiome = caveBiomeCache.get(key);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        IrisBiome resolvedBiome = resolverState.resolve(x, y, z);
        if (resolvedBiome != null) {
            caveBiomeCache.put(key, resolvedBiome);
        }
        return resolvedBiome;
    }

    private IrisBiome resolveCustomBiome(Map<String, IrisBiome> customBiomeCache, String customBiome) {
        if (customBiomeCache.containsKey(customBiome)) {
            return customBiomeCache.get(customBiome);
        }

        IrisBiome loaded = getEngine().getData().getBiomeLoader().load(customBiome);
        customBiomeCache.put(customBiome, loaded);
        return loaded;
    }

    static final class CaveInputs {
        private final Engine engine;
        private final IrisComplex complex;
        private final IrisDimension dimension;
        private final IrisData data;
        private final int height;
        private final IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        private final IrisDimensionCarvingResolver.Snapshot snapshot;
        private final SubterrainChunkCells cells;
        private final boolean subterrainFirst;
        private final int minimumY;
        private final DimensionStackContext stack;
        private final ProceduralStream<IrisBiome> surfaceBiomes;
        private final ProceduralStream<IrisBiome> caveBiomes;
        private final ProceduralStream<Double> heights;
        private final CaveColumn[] columns;
        private final boolean chunkLimited;
        private final int chunkX;
        private final int chunkZ;

        CaveInputs(Engine engine) {
            this(engine, false, 0, 0);
        }

        static CaveInputs forTerrainChunk(Engine engine, int chunkX, int chunkZ) {
            return new CaveInputs(engine, false, chunkX, chunkZ);
        }

        /**
         * Inputs for the content stage of one chunk. A coordinate inside that chunk resolves under the chunk's
         * own generation route, exactly the binding the natural terrain stage samples with, so it takes the
         * captured column path; any other coordinate keeps the per-coordinate history dispatch.
         */
        static CaveInputs forContentChunk(Engine engine, int chunkX, int chunkZ) {
            return new CaveInputs(engine, true, chunkX, chunkZ);
        }

        private CaveInputs(Engine engine, boolean contentChunk, int chunkX, int chunkZ) {
            this.engine = engine;
            complex = engine.getComplex();
            dimension = engine.getDimension();
            data = engine.getData();
            height = engine.getHeight();
            cells = SubterrainChunkCells.of(engine, chunkX, chunkZ);
            IrisContext context = IrisContext.get();
            IrisEngine irisEngine = engine instanceof IrisEngine candidate ? candidate : null;
            boolean bound = irisEngine != null && irisEngine.hasGenerationRuntimeScope();
            boolean worker = irisEngine != null && !engine.getPlatformHooks().isMainThread();
            boolean generating = bound && worker
                    && context != null && context.getChunkContext() != null
                    && context.getChunkContext().getComplex() == complex;
            boolean natural = generating && context.getChunkContext().isNaturalTerrain();
            boolean unrouted = !bound && worker && !irisEngine.hasGenerationHistoryRouting();
            boolean scoped = natural || generating && contentChunk || unrouted;
            this.subterrainFirst = unrouted;
            this.chunkLimited = !natural && !unrouted;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            snapshot = scoped ? IrisDimensionCarvingResolver.snapshot(engine) : null;
            minimumY = scoped ? engine.getWorld().minHeight() : 0;
            stack = scoped ? engine.getDimensionStackContext() : null;
            surfaceBiomes = scoped ? complex.getTrueBiomeStream() : null;
            caveBiomes = scoped ? complex.getCaveBiomeStream() : null;
            heights = scoped ? complex.getHeightStream() : null;
            columns = scoped ? new CaveColumn[1024] : null;
        }

        boolean captures(int x, int z) {
            return snapshot != null && (!chunkLimited || (x >> 4) == chunkX && (z >> 4) == chunkZ);
        }

        IrisBiome resolve(int x, int y, int z) {
            if (!captures(x, z)) {
                return engine.getCaveBiome(x, y, z, state);
            }
            if (subterrainFirst) {
                IrisBiome feature = cells.biome(x, y, z);
                if (feature != null) {
                    return feature;
                }
            }
            IrisBiome configured = snapshot.resolveBiome(x, y + minimumY, z);
            if (configured != null) {
                return configured;
            }
            long key = ((long) x << 32) | (z & 0xffffffffL);
            int index = (x * 31 + z) & (columns.length - 1);
            CaveColumn column = columns[index];
            if (column == null || column.key != key) {
                IrisBiome surface = surfaceBiomes.get(x, z);
                IrisBiome cave = caveBiomes.get(x, z);
                int surfaceY = heights.get(x, z).intValue();
                if (cave == null || cave.getLoadKey() == null) {
                    cave = surface;
                    if (stack != null) {
                        DimensionStackLayout.Layer layer = stack.getLayout(x, z).surfaceLayer();
                        if (layer != null && layer.biome() != null) {
                            cave = layer.biome();
                        }
                    }
                }
                column = new CaveColumn(key, surface, cave, surfaceY,
                        cave == null ? 0 : Math.max(0, cave.getCaveMinDepthBelowSurface()));
                columns[index] = column;
            }
            int depth = column.surfaceY - y;
            return column.cave == null || depth <= 0 || depth < column.minimumDepth
                    ? column.surface : column.cave;
        }
    }

    private record CaveColumn(long key, IrisBiome surface, IrisBiome cave, int surfaceY, int minimumDepth) {
    }

    private static final class SolidMemo {
        private NativeBlockState state;
        private boolean solid;

        private boolean test(NativeBlockState candidate) {
            if (candidate != state) {
                state = candidate;
                solid = B.isSolid(candidate);
            }
            return solid;
        }
    }

    @Data
    public static class CaveZone {
        private int ceiling = -1;
        private int floor = -1;

        public int airThickness() {
            return (ceiling - floor) - 1;
        }

        public boolean isValid(int height) {
            return floor < ceiling && ceiling - floor >= 1 && floor >= 0 && ceiling <= height && airThickness() > 0;
        }

        public String toString() {
            return floor + "-" + ceiling;
        }
    }

    @FunctionalInterface
    interface HydrologyCells {
        HydrologyCaveCell at(int worldX, int y, int worldZ);
    }
}
