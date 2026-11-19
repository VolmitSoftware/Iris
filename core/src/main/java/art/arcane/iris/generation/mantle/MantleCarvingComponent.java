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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.cave.field.CngNoiseFactory;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.mantle.ListedCaveCarving.ChunkSurface;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.stream.BakedDoubleStream;
import art.arcane.iris.generation.stream.BakedStream;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.Arrays;

@ComponentFlag(ReservedFlag.CARVED)
public class MantleCarvingComponent extends IrisMantleComponent {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    /** Columns read beyond the chunk to find where surface fluids reach, which no cave may open under. */
    private static final int FLUID_BOUNDARY_PADDING = 3;
    private static final int FIELD_SIZE = CHUNK_SIZE + (FLUID_BOUNDARY_PADDING * 2);
    /** Loose rock is swept this far below the surface where a cave opens to it. */
    private static final int LOOSE_ROCK_SWEEP_DEPTH_BLOCKS = 16;
    /** An upper dimension's terrain needs this much room under the top of the world to be carved. */
    private static final int MINIMUM_UPPER_TERRAIN_THICKNESS_BLOCKS = 2;
    private static final int NEIGHBOURS = 9;
    private static final int[] NEIGHBOUR_DX = new int[NEIGHBOURS];
    private static final int[] NEIGHBOUR_DZ = new int[NEIGHBOURS];
    private static final long[][] NEIGHBOUR_COLUMNS = new long[NEIGHBOURS][4];
    private static final int[] FIELD_NEIGHBOUR = new int[FIELD_SIZE * FIELD_SIZE];
    private static final int[] FIELD_COLUMN = new int[FIELD_SIZE * FIELD_SIZE];
    private static final ThreadLocal<ChunkScratch> CHUNK_SCRATCH = ThreadLocal.withInitial(ChunkScratch::new);
    private static final ListedCaveCarving.Problems CAVE_PROBLEMS = new ListedCaveCarving.Problems() {
        @Override
        public void missingCave(String caveKey) {
            IrisLogging.warnOnce("missing-cave:" + caveKey, "Cave '%s' is listed under caves but the pack has no readable caves/%s.json", caveKey, caveKey);
        }

        @Override
        public void missingCaveBiome(String caveKey, String biomeKey) {
            IrisLogging.warnOnce("missing-cave-biome:" + caveKey + ":" + biomeKey, "Cave '%s' names the biome '%s', which the pack does not have", caveKey, biomeKey);
        }
    };

    private final Object caveCarvingLock = new Object();
    private volatile ListedCaveCarving caveCarving;

    static {
        for (int neighbour = 0; neighbour < NEIGHBOURS; neighbour++) {
            NEIGHBOUR_DX[neighbour] = (neighbour / 3) - 1;
            NEIGHBOUR_DZ[neighbour] = (neighbour % 3) - 1;
        }
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int offsetX = fieldX - FLUID_BOUNDARY_PADDING;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int offsetZ = fieldZ - FLUID_BOUNDARY_PADDING;
                int neighbour = ((Math.floorDiv(offsetX, CHUNK_SIZE) + 1) * 3) + Math.floorDiv(offsetZ, CHUNK_SIZE) + 1;
                int column = ((offsetZ & (CHUNK_SIZE - 1)) << 4) | (offsetX & (CHUNK_SIZE - 1));
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                FIELD_NEIGHBOUR[fieldIndex] = neighbour;
                FIELD_COLUMN[fieldIndex] = column;
                NEIGHBOUR_COLUMNS[neighbour][column >>> 6] |= 1L << (column & 63);
            }
        }
    }

    public MantleCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.CARVED, 0);
    }

    @Override
    public MatterGenerationPhase getGenerationPhase() {
        return MatterGenerationPhase.TERRAIN;
    }

    @Override
    public void hotload() {
        super.hotload();
        // Carvers hold the pack's caves and the closed IrisData of the previous load. They rebuild
        // deterministically from the carve seed, so output is unchanged.
        synchronized (caveCarvingLock) {
            caveCarving = null;
        }
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsMantleChunkWrite(x, z)) {
            return;
        }
        ChunkScratch previous = CHUNK_SCRATCH.get();
        ChunkScratch scratch = previous.inUse ? new ChunkScratch() : previous;
        if (scratch != previous) {
            CHUNK_SCRATCH.set(scratch);
        }
        scratch.inUse = true;
        try {
            generateLayer(writer, x, z, context, scratch);
        } finally {
            scratch.release();
            if (scratch != previous) {
                CHUNK_SCRATCH.set(previous);
            }
        }
    }

    private void generateLayer(MantleWriter writer, int x, int z, ChunkContext context, ChunkScratch scratch) {
        IrisComplex complex = context.getComplex();
        Engine engine = getEngineMantle().getEngine();
        int[] chunkSurfaceHeights = prepareChunkSurfaceHeights(x, z, context, scratch.chunkSurfaceHeights);
        int[] caveCeilings = CaveCeilingMask.fill(x, z, complex.getHydrologyRuntime()::raster,
                scratch.caveCeilingField, scratch.caveCeilings) ? scratch.caveCeilings : null;
        ListedCaveCarving carving = caveCarving();
        PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
        CaveListing listing = carving.list(new EngineCaveTerrain(engine, complex, scratch.resolverState), x, z);
        engine.getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());
        carveListing(carving, listing, writer, x, z, complex, chunkSurfaceHeights, caveCeilings, scratch);
        rasterizeSubterrain(writer, x, z, complex, caveCeilings);
    }

    private void carveListing(ListedCaveCarving carving, CaveListing listing, MantleWriter writer, int x, int z, IrisComplex complex,
                              int[] chunkSurfaceHeights, int[] caveCeilings, ChunkScratch scratch) {
        MantleChunk<Matter> chunk = listing.isEmpty() ? null : writer.acquireChunk(x, z);
        if (chunk == null) {
            return;
        }
        PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
        long[] surfaceFluidBoundaries = fillSurfaceFluidBoundaries(complex, x, z, chunkSurfaceHeights, scratch);
        CaveFluidSupportPlan fluidSupportPlan = new CaveFluidSupportPlan();
        CaveCellSink sink = new MantleCaveCellSink(chunk, writer.getMantle().getWorldHeight());
        carving.carve(listing, x, z, new ChunkSurface(chunkSurfaceHeights, surfaceFluidBoundaries, caveCeilings), sink, fluidSupportPlan);
        UpperDimensionContext upperContext = getEngineMantle().getEngine().getUpperContext();
        if (upperContext != null && getDimension().isUpperDimensionCarving()) {
            carveUpperTerrain(upperContext, carving, listing, sink, x, z, fluidSupportPlan);
        }
        fluidSupportPlan.resolve(chunk);
        int sweepDepth = listing.opensToSurface() ? LOOSE_ROCK_SWEEP_DEPTH_BLOCKS : 0;
        CarveOrphanSweep.sweepChunk(chunk, chunkSurfaceHeights, sweepDepth, writer.getMantle().getWorldHeight() - 1,
                surfaceFluidBoundaries, caveCeilings);
        getEngineMantle().getEngine().getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
    }

    private long[] fillSurfaceFluidBoundaries(IrisComplex complex, int x, int z, int[] chunkSurfaceHeights, ChunkScratch scratch) {
        prefillFluidBoundaryField(complex, x, z, scratch);
        SurfaceFluidBoundaryPlan.fill(
                chunkSurfaceHeights,
                scratch.fieldSurfaceHeights,
                scratch.fieldHasFluid,
                scratch.fieldFluidHeights,
                FIELD_SIZE,
                FLUID_BOUNDARY_PADDING,
                scratch.surfaceFluidBoundaries
        );
        return scratch.surfaceFluidBoundaries;
    }

    private void rasterizeSubterrain(MantleWriter writer, int chunkX, int chunkZ, IrisComplex complex, int[] caveCeilings) {
        SubterrainPlanner planner = complex.getSubterrainPlanner();
        if (planner == null || planner.isEmpty()) {
            return;
        }
        int minimumY = getEngineMantle().getEngine().getWorld().minHeight();
        SubterrainRasterizer.rasterize(planner, chunkX, chunkZ, (x, worldY, z, cell) -> {
            int y = worldY - minimumY;
            if (y <= 0 || y >= writer.getMantle().getWorldHeight()) {
                return;
            }
            if (cell.solid() && !cell.room().reservedSolid()) {
                return;
            }
            if (!cell.solid() && caveCeilings != null && y > caveCeilings[PowerOfTwoCoordinates.packLocal16(x & 15, z & 15)]) {
                return;
            }
            if (cell.solid()) {
                writer.clearData(x, y, z, MatterCavern.class);
            } else {
                writer.setData(x, y, z, SubterrainRasterizer.cavern(cell));
            }
            if (cell.solid()) {
                writer.setData(x, y, z, SubterrainRasterizer.state(cell));
            } else {
                writer.clearData(x, y, z, NativeBlockState.class);
            }
        });
    }

    private void carveUpperTerrain(UpperDimensionContext upperContext, ListedCaveCarving carving, CaveListing listing, CaveCellSink sink,
                                   int cx, int cz, CaveFluidSupportPlan fluidSupportPlan) {
        int chunkHeight = getEngineMantle().getEngine().getHeight();
        int baseX = PowerOfTwoCoordinates.chunkToBlock(cx);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(cz);
        int lowestUpperSurfaceY = chunkHeight;
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                lowestUpperSurfaceY = Math.min(lowestUpperSurfaceY, upperContext.getEffectiveSurfaceY(baseX + localX, baseZ + localZ));
            }
        }
        if (lowestUpperSurfaceY >= chunkHeight - MINIMUM_UPPER_TERRAIN_THICKNESS_BLOCKS) {
            return;
        }
        carving.carveBeneathUpperTerrain(listing, cx, cz, lowestUpperSurfaceY, sink, fluidSupportPlan);
    }

    private ListedCaveCarving caveCarving() {
        ListedCaveCarving carving = caveCarving;
        if (carving != null) {
            return carving;
        }
        synchronized (caveCarvingLock) {
            if (caveCarving == null) {
                caveCarving = createCaveCarving();
            }
            return caveCarving;
        }
    }

    private ListedCaveCarving createCaveCarving() {
        Engine engine = getEngineMantle().getEngine();
        IrisDimension dimension = getDimension();
        IrisData data = engine.getData();
        CaveWorld world = new CaveWorld(engine.getWorld().minHeight(), engine.getHeight(), engine.getSeedManager().getCarve(),
                dimension.getFluidHeight(), dimension.getCaveLavaHeight());
        return new ListedCaveCarving(dimension.getCaves(), onlyDimensionListsCaves(dimension, data), world, new CngNoiseFactory(data),
                key -> data.getCaveLoader().load(key), key -> data.getBiomeLoader().load(key) != null, CAVE_PROBLEMS);
    }

    /** Whether no region and no biome the dimension reaches lists a cave, so every column lists what the dimension lists. */
    private static boolean onlyDimensionListsCaves(IrisDimension dimension, IrisData data) {
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            if (region != null && !region.getCaves().isEmpty()) {
                return false;
            }
        }
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            if (biome != null && !biome.getCaves().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void prefillFluidBoundaryField(IrisComplex complex, int chunkX, int chunkZ, ChunkScratch scratch) {
        int startX = PowerOfTwoCoordinates.chunkToBlock(chunkX) - FLUID_BOUNDARY_PADDING;
        int startZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ) - FLUID_BOUNDARY_PADDING;
        fillFieldHeights(complex.getNaturalHeightStream(), chunkX, chunkZ, startX, startZ, scratch);
        double fluidHeight = complex.getFluidHeight();
        Arrays.fill(scratch.fieldFluidHeights, fluidHeight);
        fillFieldFluidPresence(complex.getFluidStream(), chunkX, chunkZ, startX, startZ, Math.round(fluidHeight), scratch);
    }

    private void fillFieldHeights(ProceduralStream<Double> stream, int chunkX, int chunkZ, int startX, int startZ, ChunkScratch scratch) {
        double[] target = scratch.fieldSurfaceHeights;
        if (stream instanceof BakedDoubleStream baked) {
            double[][] neighbourHeights = scratch.neighbourHeights;
            for (int neighbour = 0; neighbour < NEIGHBOURS; neighbour++) {
                baked.fillColumns(chunkX + NEIGHBOUR_DX[neighbour], chunkZ + NEIGHBOUR_DZ[neighbour],
                        NEIGHBOUR_COLUMNS[neighbour], neighbourHeights[neighbour]);
            }
            for (int fieldIndex = 0; fieldIndex < target.length; fieldIndex++) {
                target[fieldIndex] = neighbourHeights[FIELD_NEIGHBOUR[fieldIndex]][FIELD_COLUMN[fieldIndex]];
            }
            return;
        }
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                target[(fieldX * FIELD_SIZE) + fieldZ] = stream.getDouble(worldX, startZ + fieldZ);
            }
        }
    }

    private void fillFieldFluidPresence(ProceduralStream<NativeBlockState> stream, int chunkX, int chunkZ, int startX, int startZ,
                                        long roundedFluidHeight, ChunkScratch scratch) {
        double[] surfaceHeights = scratch.fieldSurfaceHeights;
        boolean[] target = scratch.fieldHasFluid;
        if (stream instanceof BakedStream<?> baked) {
            long[][] submerged = scratch.neighbourSubmerged;
            for (long[] columns : submerged) {
                Arrays.fill(columns, 0L);
            }
            for (int fieldIndex = 0; fieldIndex < target.length; fieldIndex++) {
                if (Math.round(surfaceHeights[fieldIndex]) < roundedFluidHeight) {
                    int column = FIELD_COLUMN[fieldIndex];
                    submerged[FIELD_NEIGHBOUR[fieldIndex]][column >>> 6] |= 1L << (column & 63);
                }
            }
            Object[][] neighbourObjects = scratch.neighbourObjects;
            for (int neighbour = 0; neighbour < NEIGHBOURS; neighbour++) {
                long[] columns = submerged[neighbour];
                if ((columns[0] | columns[1] | columns[2] | columns[3]) != 0L) {
                    baked.fillColumns(chunkX + NEIGHBOUR_DX[neighbour], chunkZ + NEIGHBOUR_DZ[neighbour],
                            columns, neighbourObjects[neighbour]);
                }
            }
            for (int fieldIndex = 0; fieldIndex < target.length; fieldIndex++) {
                target[fieldIndex] = Math.round(surfaceHeights[fieldIndex]) < roundedFluidHeight
                        && B.isFluid((NativeBlockState) neighbourObjects[FIELD_NEIGHBOUR[fieldIndex]][FIELD_COLUMN[fieldIndex]]);
            }
            return;
        }
        for (int fieldX = 0; fieldX < FIELD_SIZE; fieldX++) {
            int worldX = startX + fieldX;
            for (int fieldZ = 0; fieldZ < FIELD_SIZE; fieldZ++) {
                int fieldIndex = (fieldX * FIELD_SIZE) + fieldZ;
                target[fieldIndex] = B.isFluid(stream.get(worldX, startZ + fieldZ))
                        && Math.round(surfaceHeights[fieldIndex]) < roundedFluidHeight;
            }
        }
    }

    protected int computeRadius() {
        return 1;
    }

    @Override
    public int getWriteRadius() {
        return 0;
    }

    static int[] prepareChunkSurfaceHeights(int chunkX, int chunkZ, ChunkContext context, int[] scratch) {
        int[] surfaceHeights = scratch;
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        ProceduralStream<Double> naturalHeight = context.getComplex().getNaturalHeightStream();
        double[] cachedChunkHeights = null;
        if (naturalHeight instanceof ChunkFillableDoubleStream2D cachedHeightStream) {
            cachedChunkHeights = CHUNK_SCRATCH.get().chunkSurfaceHeightSamples;
            cachedHeightStream.fillChunkDoubles(baseX, baseZ, cachedChunkHeights);
        }
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = baseZ + localZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                if (cachedChunkHeights != null) {
                    surfaceHeights[columnIndex] = (int) Math.round(cachedChunkHeights[(localZ << 4) + localX]);
                    continue;
                }
                surfaceHeights[columnIndex] = (int) Math.round(naturalHeight.getDouble(worldX, worldZ));
            }
        }
        return surfaceHeights;
    }

    private static final class ChunkScratch {
        private final double[] fieldSurfaceHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final double[] fieldFluidHeights = new double[FIELD_SIZE * FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[FIELD_SIZE * FIELD_SIZE];
        private final long[] surfaceFluidBoundaries = new long[CHUNK_AREA];
        private final int[] chunkSurfaceHeights = new int[CHUNK_AREA];
        private final double[] chunkSurfaceHeightSamples = new double[CHUNK_AREA];
        private final int[] caveCeilingField = new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD];
        private final int[] caveCeilings = new int[CHUNK_AREA];
        private final double[][] neighbourHeights = new double[NEIGHBOURS][CHUNK_AREA];
        private final Object[][] neighbourObjects = new Object[NEIGHBOURS][CHUNK_AREA];
        private final long[][] neighbourSubmerged = new long[NEIGHBOURS][4];
        private final IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        private boolean inUse;

        private void release() {
            for (Object[] objects : neighbourObjects) {
                Arrays.fill(objects, null);
            }
            inUse = false;
        }
    }
}
