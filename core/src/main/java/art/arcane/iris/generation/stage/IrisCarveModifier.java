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
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.runtime.EngineAssignedModifier;
import art.arcane.iris.generation.mantle.TerrainMatterView;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.hydrology.IrisHydrology;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.IrisRiverMaterialConfig;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
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
    private static final MatterCavern BASIC_CAVERN = new MatterCavern(true, "", (byte) 0);
    private final RNG rng;
    private final NativeBlockState AIR = B.getState("CAVE_AIR");
    private final NativeBlockState LAVA = B.getState("LAVA");
    private final IrisDecorantActuator decorant;

    public IrisCarveModifier(Engine engine) {
        super(engine, "Carve");
        rng = new RNG(getEngine().getSeedManager().getCarve());
        decorant = new IrisDecorantActuator(engine);
    }

    @Override
    @ChunkCoordinates
    public void onModify(int x, int z, Hunk<NativeBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch caveStopwatch = PrecisionStopwatch.start();
        Mantle<Matter> mantle = getEngine().getMantle().getMantle();
        CaveInputs resolverState = new CaveInputs(getEngine());
        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = new Long2ObjectOpenHashMap<>(2048);
        IrisCarveScratch scratch = SCRATCH.get();
        scratch.reset();
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

        MantleChunk<Matter> mantleChunk = mantle.getChunk(x, z).use();
        try {
            PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
            int worldHeightSpan = getEngine().getWorld().maxHeight() - getEngine().getWorld().minHeight();
            int caveLavaHeight = getEngine().getDimension().getCaveLavaHeight();
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
                    chunkBlockZ
            );
            CarveResolver carveResolver = new CarveResolver(resolutionContext);
            TerrainMatterView.iterate(mantleChunk, MatterCavern.class, (xx, yy, zz, cavern) -> carveResolver.apply(
                    xx,
                    yy,
                    zz,
                    cavern,
                    dataIfPresent(mantleChunk, xx, yy, zz, HydrologyCaveCell.class)
            ));
            TerrainMatterView.iterate(mantleChunk, HydrologyCaveCell.class, (xx, yy, zz, hydrology) -> {
                if (dataIfPresent(mantleChunk, xx, yy, zz, MatterCavern.class) == null) {
                    carveResolver.apply(xx, yy, zz, null, hydrology);
                }
            });
            if (scratch.customCaveBiomePresent) {
                addInternalWallsFromMantle(mantleChunk, walls, columnMasks);
            } else {
                addInternalWallsFromMasks(walls, columnMasks);
            }
            addCrossChunkBoundaryWalls(mantle, mantleChunk, walls, boundaryMasks, x, z, surfaceHeights);
            getEngine().getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());

            PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
            try {
                IrisData wallData = getData();
                walls.forEach((rx, yy, rz, cavern) -> {
                    HydrologyCaveCell hydrology = dataIfPresent(
                            mantleChunk, rx, yy, rz, HydrologyCaveCell.class);
                    if (hydrology != null && hydrology.protectsPlacement()) {
                        return;
                    }
                    int worldX = rx + chunkBlockX;
                    int worldZ = rz + chunkBlockZ;
                    String customBiome = cavern.getCustomBiome();
                    IrisBiome biome = customBiome.isEmpty()
                            ? resolveCaveBiome(caveBiomeCache, worldX, yy, worldZ, resolverState)
                            : resolveCustomBiome(customBiomeCache, customBiome);

                    if (biome != null) {
                        NativeBlockState data = biome.getWall().get(rng, worldX, yy, worldZ, wallData);
                        int columnIndex = PowerOfTwoCoordinates.packLocal16(rx, rz);

                        if (data != null && B.isSolid(output.getRaw(rx, yy, rz)) && yy < surfaceHeights[columnIndex]) {
                            output.setRaw(rx, yy, rz, data);
                        }
                    }
                });

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    processColumnFromMask(
                            output,
                            mantleChunk,
                            mantle,
                            columnMasks[columnIndex],
                            columnIndex,
                            x,
                            z,
                            resolverState,
                            caveBiomeCache,
                            customBiomeCache
                    );
                }

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    if (boundaryMasks[columnIndex].isEmpty() || !columnMasks[columnIndex].isEmpty()) {
                        continue;
                    }
                    processBoundaryColumnFromMask(
                            output,
                            mantleChunk,
                            boundaryMasks[columnIndex],
                            walls,
                            columnIndex,
                            x,
                            z,
                            resolverState,
                            caveBiomeCache,
                            customBiomeCache
                    );
                }

                // Surface-break carving must not leave an ore cap suspended across the opening.
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
                    NativeBlockState surface = output.getRaw(localX, surfaceY, localZ);
                    NativeBlockState below = output.getRaw(localX, belowY, localZ);
                    if (isUnsupportedSurfaceOre(surface, below)) {
                        output.setRaw(localX, surfaceY, localZ, AIR);
                    }
                }
            } finally {
                getEngine().getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
            }
        } finally {
            getEngine().getMetrics().getCave().put(caveStopwatch.getMilliseconds());
            mantleChunk.release();
        }
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
            case WET_SOURCE -> fluid;
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
                NativeBlockState normalized = resolveHydrologyState(hydrology, current, null, AIR);
                if (normalized != current) {
                    context.output().setRaw(localX, y, localZ, normalized);
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
                        : getComplex().resolveHydrologyFluid(
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
            int chunkBlockZ
    ) {
    }

    private void addInternalWallsFromMasks(CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask columnMask = columnMasks[columnIndex];
            if (columnMask.isEmpty()) {
                continue;
            }

            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            int yy = columnMask.nextSetBit(0);
            while (yy >= 0) {
                if (rz < 15 && !columnMasks[columnIndex + 1].contains(yy)) {
                    walls.put(rx, yy, rz + 1, BASIC_CAVERN);
                }
                if (rx < 15 && !columnMasks[columnIndex + 16].contains(yy)) {
                    walls.put(rx + 1, yy, rz, BASIC_CAVERN);
                }
                if (rz > 0 && !columnMasks[columnIndex - 1].contains(yy)) {
                    walls.put(rx, yy, rz - 1, BASIC_CAVERN);
                }
                if (rx > 0 && !columnMasks[columnIndex - 16].contains(yy)) {
                    walls.put(rx - 1, yy, rz, BASIC_CAVERN);
                }
                yy = columnMask.nextSetBit(yy + 1);
            }
        }
    }

    private void addInternalWallsFromMantle(MantleChunk<Matter> mc, CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask columnMask = columnMasks[columnIndex];
            if (columnMask.isEmpty()) {
                continue;
            }

            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            int yy = columnMask.nextSetBit(0);
            while (yy >= 0) {
                MatterCavern cavern = composedCavernAt(mc, rx, yy, rz);
                if (cavern != null) {
                    if (rz < 15 && composedCavernAt(mc, rx, yy, rz + 1) == null) {
                        walls.put(rx, yy, rz + 1, cavern);
                    }
                    if (rx < 15 && composedCavernAt(mc, rx + 1, yy, rz) == null) {
                        walls.put(rx + 1, yy, rz, cavern);
                    }
                    if (rz > 0 && composedCavernAt(mc, rx, yy, rz - 1) == null) {
                        walls.put(rx, yy, rz - 1, cavern);
                    }
                    if (rx > 0 && composedCavernAt(mc, rx - 1, yy, rz) == null) {
                        walls.put(rx - 1, yy, rz, cavern);
                    }
                }
                yy = columnMask.nextSetBit(yy + 1);
            }
        }
    }

    private void addCrossChunkBoundaryWalls(
            Mantle<Matter> mantle,
            MantleChunk<Matter> mc,
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

        for (int yy = 1; yy <= maxY; yy++) {
            for (int offset = 0; offset < 16; offset++) {
                if (west != null) {
                    tryAddBoundaryWall(mc, west, walls, boundaryMasks, 0, yy, offset, 15, offset);
                }
                if (east != null) {
                    tryAddBoundaryWall(mc, east, walls, boundaryMasks, 15, yy, offset, 0, offset);
                }
                if (north != null) {
                    tryAddBoundaryWall(mc, north, walls, boundaryMasks, offset, yy, 0, offset, 15);
                }
                if (south != null) {
                    tryAddBoundaryWall(mc, south, walls, boundaryMasks, offset, yy, 15, offset, 0);
                }
            }
        }
    }

    private void tryAddBoundaryWall(
            MantleChunk<Matter> mc,
            MantleChunk<Matter> neighborChunk,
            CarveWallBuffer walls,
            CarveColumnMask[] boundaryMasks,
            int localX,
            int yy,
            int localZ,
            int neighborX,
            int neighborZ
    ) {
        if (composedCavernAt(mc, localX, yy, localZ) != null) {
            return;
        }

        MatterCavern neighbor = composedCavernAt(neighborChunk, neighborX, yy, neighborZ);
        if (neighbor == null) {
            return;
        }

        walls.put(localX, yy, localZ, neighbor);
        int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
        boundaryMasks[columnIndex].add(yy);
    }

    private MantleChunk<Matter> existingMantleChunk(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        TectonicPlate<Matter> plate = mantle.getLoadedRegions().get(Mantle.key(chunkX >> 5, chunkZ >> 5));
        if (plate == null || plate.isClosed()) {
            return null;
        }
        return plate.get(chunkX & 31, chunkZ & 31);
    }

    private MatterCavern composedCavernAt(MantleChunk<Matter> mantleChunk, int x, int y, int z) {
        return TerrainMatterView.getComposedCavern(mantleChunk, x, y, z);
    }

    private static <T> T dataIfPresent(MantleChunk<Matter> mantleChunk, int x, int y, int z, Class<T> type) {
        return TerrainMatterView.get(mantleChunk, x, y, z, type);
    }

    private void processColumnFromMask(
            Hunk<NativeBlockState> output,
            MantleChunk<Matter> mc,
            Mantle<Matter> mantle,
            CarveColumnMask columnMask,
            int columnIndex,
            int chunkX,
            int chunkZ,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
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
        CaveZone zone = new CaveZone();
        zone.setFloor(firstHeight);
        int buf = firstHeight - 1;
        int y = firstHeight;

        while (y >= 0) {
            if (y <= resolverState.height) {
                if (y == buf + 1) {
                    buf = y;
                    zone.ceiling = buf;
                } else {
                    if (zone.isValid(resolverState.height)) {
                        processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState,
                                caveBiomeCache, customBiomeCache);
                    }
                    zone = new CaveZone();
                    zone.setFloor(y);
                    buf = y;
                }
            }

            y = columnMask.nextSetBit(y + 1);
        }

        if (zone.isValid(resolverState.height)) {
            processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState,
                    caveBiomeCache, customBiomeCache);
        }
    }

    private void processBoundaryColumnFromMask(
            Hunk<NativeBlockState> output,
            MantleChunk<Matter> mantleChunk,
            CarveColumnMask boundaryMask,
            CarveWallBuffer walls,
            int columnIndex,
            int chunkX,
            int chunkZ,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
    ) {
        int firstHeight = boundaryMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
        int rz = columnIndex & 15;
        int worldX = rx + PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int worldZ = rz + PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int zoneFloor = firstHeight;
        int zoneCeiling = firstHeight;
        int y = boundaryMask.nextSetBit(firstHeight + 1);

        while (y >= 0) {
            if (y == zoneCeiling + 1) {
                zoneCeiling = y;
            } else {
                paintBoundaryZone(output, mantleChunk, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling,
                        resolverState, caveBiomeCache, customBiomeCache);
                zoneFloor = y;
                zoneCeiling = y;
            }
            y = boundaryMask.nextSetBit(y + 1);
        }

        paintBoundaryZone(output, mantleChunk, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling,
                resolverState, caveBiomeCache, customBiomeCache);
    }

    private void paintBoundaryZone(
            Hunk<NativeBlockState> output,
            MantleChunk<Matter> mantleChunk,
            CarveWallBuffer walls,
            int rx,
            int rz,
            int worldX,
            int worldZ,
            int zoneFloor,
            int zoneCeiling,
            CaveInputs resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
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
            HydrologyCaveCell floorHydrology = dataIfPresent(
                    mantleChunk, rx, zoneFloor, rz, HydrologyCaveCell.class);
            IrisRiverMaterialConfig bedMaterial = undergroundBedMaterial();
            KList<NativeBlockState> floorLayers = floorBiome.generateLayers(
                    resolverState.dimension, worldX, worldZ, rng, 3, zoneFloor, resolverState.data, resolverState.complex);
            for (int i = 0; i < zoneFloor - 1; i++) {
                if (!floorLayers.hasIndex(i)) {
                    break;
                }
                int floorY = zoneFloor - i - 1;
                if (floorY < 0) {
                    break;
                }
                HydrologyCaveCell hydrology = dataIfPresent(
                        mantleChunk, rx, floorY, rz, HydrologyCaveCell.class);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState existing = output.getRaw(rx, floorY, rz);
                NativeBlockState layer = resolveSubmergedCaveFloorLayer(
                        output, rx, floorY, rz,
                        paintUndergroundBedMaterial(
                                floorLayers.get(i), bedMaterial, floorHydrology, i,
                                rng, worldX, floorY, worldZ, resolverState.data),
                        floorHydrology);
                if (!B.isSolid(existing)
                        || !canReplaceHydrologyGuard(hydrology, layer, false)
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
            KList<NativeBlockState> ceilingLayers = ceilingBiome.generateCeilingLayers(
                    resolverState.dimension, worldX, worldZ, rng, 3, zoneCeiling, resolverState.data, resolverState.complex);
            for (int i = 0; i < ceilingLayers.size(); i++) {
                int ceilingY = zoneCeiling + i + 1;
                if (ceilingY >= worldMaxY) {
                    break;
                }
                HydrologyCaveCell hydrology = dataIfPresent(
                        mantleChunk, rx, ceilingY, rz, HydrologyCaveCell.class);
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
                if (!canReplaceHydrologyGuard(hydrology, layer, true)) {
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

    private void processZone(Hunk<NativeBlockState> output, MantleChunk<Matter> mc, Mantle<Matter> mantle,
                             CaveZone zone, int rx, int rz, int xx, int zz,
                             CaveInputs resolverState,
                             Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
                             Map<String, IrisBiome> customBiomeCache) {
        int maxY = output.getHeight();

        if (zone.ceiling + 1 < maxY && B.isDecorant(output.getRaw(rx, zone.ceiling + 1, rz))) {
            output.setRaw(rx, zone.ceiling + 1, rz, AIR);
        }

        if (B.isDecorant(output.getRaw(rx, zone.ceiling, rz))) {
            output.setRaw(rx, zone.ceiling, rz, AIR);
        }

        IrisBiome floorBiome = resolveCaveBoundaryBiome(mc, rx, zone.floor, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceilingBiome = resolveCaveBoundaryBiome(mc, rx, zone.ceiling, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        if (floorBiome == null && ceilingBiome == null) {
            normalizeCaveZoneWaterlogging(output, mc, zone, rx, rz, xx, zz);
            return;
        }

        if (floorBiome != null) {
            HydrologyCaveCell floorHydrology = dataIfPresent(
                    mc, rx, zone.floor, rz, HydrologyCaveCell.class);
            IrisRiverMaterialConfig bedMaterial = undergroundBedMaterial();
            KList<NativeBlockState> floorBlocks = floorBiome.generateLayers(resolverState.dimension, xx, zz, rng, 3, zone.floor, resolverState.data, resolverState.complex);
            for (int i = 0; i < zone.floor - 1; i++) {
                if (!floorBlocks.hasIndex(i)) {
                    break;
                }
                int y = zone.floor - i - 1;
                HydrologyCaveCell hydrology = dataIfPresent(mc, rx, y, rz, HydrologyCaveCell.class);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState block = resolveSubmergedCaveFloorLayer(
                        output, rx, y, rz,
                        paintUndergroundBedMaterial(
                                floorBlocks.get(i), bedMaterial, floorHydrology, i,
                                rng, xx, y, zz, resolverState.data),
                        floorHydrology);
                NativeBlockState existing = output.getRaw(rx, y, rz);
                if (!B.isSolid(existing)
                        || !canReplaceHydrologyGuard(hydrology, block, false)
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
            KList<NativeBlockState> ceilingBlocks = ceilingBiome.generateCeilingLayers(resolverState.dimension, xx, zz, rng, 3, zone.ceiling, resolverState.data, resolverState.complex);
            for (int i = 0; i < ceilingBlocks.size(); i++) {
                int cy = zone.ceiling + i + 1;
                if (cy >= maxY) {
                    break;
                }
                HydrologyCaveCell hydrology = dataIfPresent(mc, rx, cy, rz, HydrologyCaveCell.class);
                if (hydrology != null
                        && hydrology.protectsPlacement()
                        && hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
                    continue;
                }
                NativeBlockState block = ceilingBlocks.get(i);
                NativeBlockState existing = output.getRaw(rx, cy, rz);
                if (!B.isSolid(existing) || !canReplaceHydrologyGuard(hydrology, block, true)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, cy, rz, B.toDeepSlateOre(existing, block));
                    continue;
                }
                output.setRaw(rx, cy, rz, block);
            }
        }

        normalizeCaveZoneWaterlogging(output, mc, zone, rx, rz, xx, zz);
    }

    public void decorateNaturalCaves(int blockX, int blockZ, Hunk<NativeBlockState> output) {
        Mantle<Matter> mantle = getEngine().getMantle().getMantle();
        MantleChunk<Matter> chunk = mantle.getChunk(blockX >> 4, blockZ >> 4).use();
        CaveInputs resolver = new CaveInputs(getEngine());
        Long2ObjectOpenHashMap<IrisBiome> caveBiomes = new Long2ObjectOpenHashMap<>(256);
        Map<String, IrisBiome> customBiomes = new HashMap<>();
        try {
            int width = output.getWidth();
            int depth = output.getDepth();
            int height = output.getHeight();
            for (int localX = 0; localX < width; localX++) {
                for (int localZ = 0; localZ < depth; localZ++) {
                    int worldX = blockX + localX;
                    int worldZ = blockZ + localZ;
                    int floor = -1;
                    for (int y = 1; y < height; y++) {
                        NativeBlockState state = output.getRaw(localX, y, localZ);
                        if (B.isSolid(state)) {
                            if (floor >= 0) {
                                CaveZone zone = new CaveZone();
                                zone.setFloor(floor);
                                zone.setCeiling(y - 1);
                                if (zone.isValid(resolver.height)
                                        && !getComplex().isTerrain3DOpening(worldX, floor, worldZ)) {
                                    if (markerRoll(worldX, zone.ceiling, worldZ, 0x9E3779B97F4A7C15L)) {
                                        mantle.set(worldX, zone.ceiling, worldZ, MarkerMatter.CAVE_CEILING);
                                    }
                                    if (markerRoll(worldX, zone.floor, worldZ, 0xC2B2AE3D27D4EB4FL)) {
                                        mantle.set(worldX, zone.floor, worldZ, MarkerMatter.CAVE_FLOOR);
                                    }
                                    IrisBiome floorBiome = resolveCaveBoundaryBiome(chunk, localX, floor, localZ,
                                            worldX, worldZ, resolver, caveBiomes, customBiomes);
                                    IrisBiome ceilingBiome = resolveCaveBoundaryBiome(chunk, localX, y - 1, localZ,
                                            worldX, worldZ, resolver, caveBiomes, customBiomes);
                                    decorateZone(output, zone, localX, localZ, worldX, worldZ,
                                            floorBiome, ceilingBiome);
                                }
                                floor = -1;
                            }
                        } else if (floor < 0 && B.isSolid(output.getRaw(localX, y - 1, localZ))) {
                            floor = y;
                        }
                    }
                }
            }
        } finally {
            chunk.release();
        }
    }

    private void decorateZone(Hunk<NativeBlockState> output, CaveZone zone,
                              int rx, int rz, int xx, int zz, IrisBiome floorBiome, IrisBiome ceilingBiome) {
        int maxY = output.getHeight();
        IrisDecorator[] surfaceDecorators = floorBiome == null
                ? new IrisDecorator[0]
                : floorBiome.getDecoratorBucket(IrisDecorationPart.NONE);
        if (surfaceDecorators.length > 0 && hasStableCaveFloorSupport(output, rx, zone.getFloor(), rz)) {
            decorant.getSurfaceDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, floorBiome, InferredType.CAVE, zone.getFloor() - 1, zone.airThickness());
        }

        IrisDecorator[] ceilingDecorators = ceilingBiome == null
                ? new IrisDecorator[0]
                : ceilingBiome.getDecoratorBucket(IrisDecorationPart.CEILING);
        if (ceilingDecorators.length > 0 && zone.getCeiling() + 1 < maxY && B.isSolid(output.getRaw(rx, zone.getCeiling() + 1, rz))) {
            decorant.getCeilingDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, ceilingBiome, InferredType.CAVE, zone.getCeiling(), zone.airThickness());
        }

    }

    private void normalizeCaveZoneWaterlogging(
            Hunk<NativeBlockState> output,
            MantleChunk<Matter> mantleChunk,
            CaveZone zone,
            int localX,
            int localZ,
            int worldX,
            int worldZ
    ) {
        int minimumY = Math.max(0, zone.floor - 1);
        int maximumY = Math.min(output.getHeight() - 1, zone.ceiling + 1);
        for (int y = minimumY; y <= maximumY; y++) {
            HydrologyCaveCell hydrology = dataIfPresent(
                    mantleChunk, localX, y, localZ, HydrologyCaveCell.class);
            if (hydrology == null) {
                continue;
            }
            MatterCavern baseline = dataIfPresent(
                    mantleChunk, localX, y, localZ, MatterCavern.class);
            NativeBlockState current = output.getRaw(localX, y, localZ);
            NativeBlockState columnFluid = getComplex().resolveHydrologyFluid(
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

    IrisBiome resolveCaveBoundaryBiome(MantleChunk<Matter> mantleChunk, int x, int y, int z, int worldX, int worldZ, CaveInputs resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        MatterCavern cavern = composedCavernAt(mantleChunk, x, y, z);
        return resolveCaveBoundaryBiome(
                cavern, worldX, y, worldZ, resolverState, caveBiomeCache, customBiomeCache);
    }

    IrisBiome resolveCaveBoundaryBiome(MatterCavern cavern, int worldX, int y, int worldZ, CaveInputs resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        if (resolverState.complex.isTerrain3DOpening(worldX, y, worldZ)) {
            return resolverState.complex.getTrueBiomeStream().get(worldX, worldZ);
        }
        if (cavern != null && !cavern.getCustomBiome().isEmpty()) {
            return resolveCustomBiome(customBiomeCache, cavern.getCustomBiome());
        }
        return resolveCaveBiome(caveBiomeCache, worldX, y, worldZ, resolverState);
    }

    /** The dimension's underground river bed palette, or null when nothing paints. */
    private IrisRiverMaterialConfig undergroundBedMaterial() {
        IrisHydrology hydrology = getDimension().getHydrology();
        if (hydrology == null) {
            return null;
        }

        IrisRiverMaterialConfig material = hydrology.getRivers().getUnderground().getBedMaterial();
        return material != null && material.isEnabled() ? material : null;
    }

    /**
     * Replaces a cave-floor biome layer with the underground bed palette for the top
     * {@code index} layers under a hydrology cell. Seal guards keep their biome layers.
     */
    static NativeBlockState paintUndergroundBedMaterial(
            NativeBlockState layer,
            IrisRiverMaterialConfig material,
            HydrologyCaveCell floorHydrology,
            int index,
            RNG rng,
            int x,
            int y,
            int z,
            IrisData data
    ) {
        if (material == null
                || !material.isEnabled()
                || floorHydrology == null
                || floorHydrology.action() == HydrologyCaveAction.SEAL_GUARD
                || index >= material.getDepth()) {
            return layer;
        }

        NativeBlockState painted = material.getPalette().get(rng, x, y, z, data);
        return painted == null ? layer : painted;
    }

    static boolean canReplaceHydrologyGuard(
            HydrologyCaveCell hydrology,
            NativeBlockState layer,
            boolean ceiling
    ) {
        if (hydrology == null || hydrology.action() != HydrologyCaveAction.SEAL_GUARD) {
            return true;
        }
        return layer != null
                && B.isSolid(layer)
                && !B.isFluid(layer)
                && (!ceiling || !isGravityAffected(layer));
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

        int roll = Math.floorMod(rng.nextParallelRNG(BlockPosition.toLong(x, y, z)).nextInt(), CAVE_BIOME_BLEND_TOTAL_WEIGHT);
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

    private IrisBiome sampleCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, CaveInputs resolverState) {
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
        private final int minimumY;
        private final DimensionStackContext stack;
        private final ProceduralStream<IrisBiome> surfaceBiomes;
        private final ProceduralStream<IrisBiome> caveBiomes;
        private final ProceduralStream<Double> heights;
        private final CaveColumn[] columns;

        CaveInputs(Engine engine) {
            this.engine = engine;
            complex = engine.getComplex();
            dimension = engine.getDimension();
            data = engine.getData();
            height = engine.getHeight();
            IrisContext context = IrisContext.get();
            boolean scoped = engine instanceof IrisEngine irisEngine
                    && irisEngine.hasGenerationRuntimeScope() && !engine.getPlatformHooks().isMainThread()
                    && context != null && context.getChunkContext() != null
                    && context.getChunkContext().getComplex() == complex
                    && context.getChunkContext().isNaturalTerrain();
            snapshot = scoped ? IrisDimensionCarvingResolver.snapshot(engine) : null;
            minimumY = scoped ? engine.getWorld().minHeight() : 0;
            stack = scoped ? engine.getDimensionStackContext() : null;
            surfaceBiomes = scoped ? complex.getTrueBiomeStream() : null;
            caveBiomes = scoped ? complex.getCaveBiomeStream() : null;
            heights = scoped ? complex.getHeightStream() : null;
            columns = scoped ? new CaveColumn[1024] : null;
        }

        IrisBiome resolve(int x, int y, int z) {
            if (snapshot == null) {
                return engine.getCaveBiome(x, y, z, state);
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
}
