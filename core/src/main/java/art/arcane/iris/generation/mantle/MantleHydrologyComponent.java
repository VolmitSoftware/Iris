package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.CavePoolFiller;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;

import java.util.Arrays;
import java.util.List;

@ComponentFlag(ReservedFlag.RIVER_HYDROLOGY)
public final class MantleHydrologyComponent extends IrisMantleComponent {
    static final int PRIORITY = 1;
    static final int NO_WATER = Integer.MIN_VALUE;
    private static final int CHUNK = 16;
    private static final int GRID = CHUNK + 2;
    private static final int[] SIDES = {1, -1, GRID, -GRID};
    private static final SeaInletRasterizer INLETS = new SeaInletRasterizer();

    public MantleHydrologyComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.RIVER_HYDROLOGY, PRIORITY);
    }

    @Override
    public MatterGenerationPhase getGenerationPhase() {
        return MatterGenerationPhase.TERRAIN;
    }

    @Override
    protected int computeRadius() {
        return poolsActive() ? CavePoolFiller.REACH : 0;
    }

    @Override
    public int getInputRadius() {
        return poolsActive() ? CavePoolFiller.REACH : 0;
    }

    public static boolean isEnabledFor(IrisDimension dimension) {
        IrisHydrology hydrology = dimension.getHydrology();
        return dimension.isUseMantle() && hydrology != null && hydrology.isEnabled()
                && !dimension.getDisabledComponents().contains(ReservedFlag.RIVER_HYDROLOGY);
    }

    private static boolean poolsEnabledFor(IrisDimension dimension) {
        return isEnabledFor(dimension) && dimension.isCarvingEnabled() && !dimension.getDisabledComponents().contains(ReservedFlag.CARVED)
                && dimension.getHydrology().getUnderground().getCavePools().isEnabled();
    }

    private boolean poolsActive() {
        EngineMantle mantle = getEngineMantle();
        Engine engine = mantle == null ? null : mantle.getEngine();
        return engine != null && engine.getDimension() != null && poolsEnabledFor(engine.getDimension());
    }

    @Override
    public void generateLayer(MantleWriter writer, int chunkX, int chunkZ, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        if (!complex.allowsMantleChunkWrite(chunkX, chunkZ)) {
            return;
        }
        HydrologyRuntime runtime = complex.getHydrologyRuntime();
        ChunkWaterRaster own = CaveCeilingMask.ready(runtime.raster(chunkX, chunkZ), chunkX, chunkZ);
        List<SeaInlet> inlets = runtime.inlets(chunkX, chunkZ);
        if (!inlets.isEmpty()) {
            SeaInletRasterizer.SubterrainFluid subterrain = subterrainFluid(writer, complex, chunkX, chunkZ);
            for (SeaInlet inlet : inlets) {
                INLETS.write(inlet, chunkX, chunkZ, writer, HydrologyCaveCell.SEA_FLUID, "", subterrain);
            }
        }
        generateFalls(writer, complex, runtime, own, chunkX, chunkZ);
        if (poolsActive()) {
            HydrologySettings settings = runtime.settings();
            new CavePoolFiller(settings).fill(chunkX, chunkZ,
                    new MantleCarvedTerrain(writer, complex, getEngineMantle().getEngine(), chunkX, chunkZ), writer, settings.seed());
        }
    }

    private static SeaInletRasterizer.SubterrainFluid subterrainFluid(MantleWriter writer, IrisComplex complex, int chunkX, int chunkZ) {
        SubterrainPlanner planner = complex.getSubterrainPlanner();
        if (planner == null || planner.isEmpty()) {
            return SeaInletRasterizer.SubterrainFluid.NOWHERE;
        }
        List<SubterrainPlan> plans = planner.plansForBounds(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15);
        if (plans.isEmpty()) {
            return SeaInletRasterizer.SubterrainFluid.NOWHERE;
        }
        int minimumY = writer.getEngine().getWorld().minHeight();
        return (x, y, z) -> planner.sample(plans, x, y + minimumY, z).fluid();
    }

    private void generateFalls(MantleWriter writer, IrisComplex complex, HydrologyRuntime runtime, ChunkWaterRaster own, int chunkX,
                               int chunkZ) {
        if (!holdsWater(own)) {
            return;
        }
        ChunkWaterRaster[] around = new ChunkWaterRaster[9];
        for (int slot = 0; slot < around.length; slot++) {
            int rasterX = chunkX + slot % 3 - 1;
            int rasterZ = chunkZ + slot / 3 - 1;
            around[slot] = slot == 4 ? own : CaveCeilingMask.ready(runtime.raster(rasterX, rasterZ), rasterX, rasterZ);
        }
        int[] heads = new int[GRID * GRID];
        complex.effectiveWaterHeads(chunkX, chunkZ, around, heads);
        Falls falls = plan(chunkX, chunkZ, writer.getMantle().getWorldHeight(), heads);
        if (falls != null) {
            publish(writer, complex, around, falls);
        }
    }

    static Falls plan(int chunkX, int chunkZ, int worldHeight, int[] heads) {
        int[] bottom = null;
        int[] top = null;
        int[] source = null;
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int cell = (lz + 1) * GRID + lx + 1;
                int head = heads[cell];
                if (head == NO_WATER) {
                    continue;
                }
                int highest = head;
                int from = -1;
                for (int side : SIDES) {
                    if (heads[cell + side] > highest) {
                        highest = heads[cell + side];
                        from = cell + side;
                    }
                }
                if (from < 0) {
                    continue;
                }
                if (head < 0 || highest >= worldHeight) {
                    throw new IllegalStateException("Hydrology fall at " + ((chunkX << 4) + lx) + "," + ((chunkZ << 4) + lz) + " from "
                            + highest + " to " + head + " leaves the world height " + worldHeight + ".");
                }
                if (bottom == null) {
                    bottom = new int[CHUNK * CHUNK];
                    top = new int[CHUNK * CHUNK];
                    source = new int[CHUNK * CHUNK];
                    Arrays.fill(top, NO_WATER);
                }
                bottom[lz << 4 | lx] = head + 1;
                top[lz << 4 | lx] = highest;
                source[lz << 4 | lx] = from;
            }
        }
        return bottom == null ? null : new Falls(chunkX, chunkZ, bottom, top, source);
    }

    private static void publish(MantleWriter writer, IrisComplex complex, ChunkWaterRaster[] around, Falls falls) {
        SubterrainPlanner planner = complex.getSubterrainPlanner();
        boolean features = planner != null && !planner.isEmpty();
        int minimumY = features ? writer.getEngine().getWorld().minHeight() : 0;
        for (int index = 0; index < CHUNK * CHUNK; index++) {
            if (!falls.falling(index)) {
                continue;
            }
            int x = falls.blockX(index);
            int z = falls.blockZ(index);
            int sourceX = falls.sourceX(index);
            int sourceZ = falls.sourceZ(index);
            ChunkWaterRaster sourceRaster = around[((sourceZ >> 4) - falls.chunkZ() + 1) * 3 + (sourceX >> 4) - falls.chunkX() + 1];
            NativeBlockState falling = fallingFluidState(complex.resolveWaterFluid(sourceRaster.fluid(sourceX & 15, sourceZ & 15), sourceX, sourceZ));
            for (int y = falls.bottom()[index]; y <= falls.top()[index]; y++) {
                if (features && planner.sample(x, y + minimumY, z).owned()) {
                    continue;
                }
                writer.setData(x, y, z, falling);
                writer.setData(x, y, z, UpdateMatter.ON);
            }
        }
    }

    private static boolean holdsWater(ChunkWaterRaster raster) {
        if (raster.empty()) {
            return false;
        }
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                if (raster.waterHead(lx, lz) != Integer.MIN_VALUE) {
                    return true;
                }
            }
        }
        return false;
    }

    static NativeBlockState fallingFluidState(NativeBlockState fluid) {
        if (!levelled(fluid) || "8".equals(IrisProceduralBlocks.propertyValue(fluid, "level"))) {
            return fluid;
        }
        return fluid.withProperty("level", "8");
    }

    private static boolean levelled(NativeBlockState fluid) {
        if (IrisProceduralBlocks.hasProperty(fluid, "level")) {
            return true;
        }
        if (fluid.key() == null) {
            return false;
        }
        String material = IrisProceduralBlocks.materialKey(fluid);
        return material.equals("minecraft:water") || material.equals("minecraft:lava");
    }

    record Falls(int chunkX, int chunkZ, int[] bottom, int[] top, int[] source) {
        boolean falling(int index) {
            return top[index] != NO_WATER;
        }

        int blockX(int index) {
            return (chunkX << 4) + (index & 15);
        }

        int blockZ(int index) {
            return (chunkZ << 4) + (index >> 4);
        }

        int sourceX(int index) {
            return (chunkX << 4) - 1 + source[index] % GRID;
        }

        int sourceZ(int index) {
            return (chunkZ << 4) - 1 + source[index] / GRID;
        }
    }
}
