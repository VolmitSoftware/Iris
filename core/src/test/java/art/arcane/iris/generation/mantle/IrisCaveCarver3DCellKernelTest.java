package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.slices.CavernMatter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public class IrisCaveCarver3DCellKernelTest {
    private static final long CARVE_SEED = -54_777_863_784_492_918L;
    private static final int WORLD_HEIGHT = 160;
    private static final int CHUNK_SPAN = 4;

    @Test
    public void warpModulesProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.WARP_MODULES, 0xdf617c77d3c3cc5eL, 67798);
    }

    @Test
    public void warpOnlyTerrainProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.WARP_TERRAIN, 0x7e0b63cab5bb3904L, 67036);
    }

    @Test
    public void styledThresholdBlendProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.STYLED_BLEND, 0x25792d49143774faL, 30387);
    }

    @Test
    public void fluidBoundaryProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.FLUID_BOUNDARY, 0xb102145856119afeL, 17584);
    }

    @Test
    public void noWarpModulesProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.NO_WARP_MODULES, 0x5a83dc20c89c6398L, 47534);
    }

    @Test
    public void verticalStepProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.VERTICAL_STEP, 0xac0413031ed65b61L, 48011);
    }

    @Test
    public void latticeProfileMatchesGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.LATTICE, 0xc7ffab155e6bdef9L, 72714);
    }

    @Test
    public void floorFluidProfilesSharingChunksMatchGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.FLOOR_FLUID_LAYERED, 0xb4c0fb23e071617dL, 68747);
    }

    @Test
    public void packStyleProfilesSharingFieldsMatchGoldenWriteSequence() throws Exception {
        assertScenario(Scenario.PACK_SHARED, 0xdbe20a1ca87f9cc7L, 269035);
    }

    @Test
    public void cellKernelDecidesCellsWithoutColumnEvaluation() throws Exception {
        IrisCaveCarver3D.CellCounters before = IrisCaveCarver3D.cellCounters();
        run(Scenario.WARP_MODULES, false);
        IrisCaveCarver3D.CellCounters after = IrisCaveCarver3D.cellCounters();
        long decided = after.decided() - before.decided();
        long evaluated = after.evaluated() - before.evaluated();
        assertTrue("decided=" + decided + " evaluated=" + evaluated, decided > 0L && evaluated > 0L);
    }

    private void assertScenario(Scenario scenario, long expectedHash, int expectedWrites) throws Exception {
        Result serial = run(scenario, false);
        Result parallel = run(scenario, true);
        assertTrue(serial.carved > 0);
        assertEquals(serial.hash, parallel.hash);
        assertEquals(serial.writes, parallel.writes);
        assertEquals(serial.carved, parallel.carved);
        assertEquals(expectedHash, serial.hash);
        assertEquals(expectedWrites, serial.writes);
    }

    private Result run(Scenario scenario, boolean parallel) throws Exception {
        if (!parallel) {
            return carveArea(scenario);
        }
        ForkJoinPool pool = new ForkJoinPool(4);
        try {
            return pool.submit(() -> carveArea(scenario)).get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Result carveArea(Scenario scenario) {
        Engine engine = createEngine(scenario);
        CaveDensityGrid grid = new CaveDensityGrid(4096);
        IrisCaveProfile[] profiles = scenario.profiles();
        IrisCaveCarver3D[] carvers = new IrisCaveCarver3D[profiles.length];
        for (int index = 0; index < profiles.length; index++) {
            carvers[index] = new IrisCaveCarver3D(engine, profiles[index], grid);
        }
        Capture capture = new Capture();
        int carved = 0;
        for (int chunkX = -2; chunkX < CHUNK_SPAN - 2; chunkX++) {
            for (int chunkZ = -1; chunkZ < CHUNK_SPAN - 1; chunkZ++) {
                MantleWriter writer = capture.writer(chunkX, chunkZ);
                CaveFluidSupportPlan supportPlan = new CaveFluidSupportPlan();
                for (int index = 0; index < carvers.length; index++) {
                    carved += carvers[index].carve(writer, chunkX, chunkZ, scenario.weights(index, chunkX, chunkZ),
                            0.08D, 0.24D, scenario.worldYRange(), scenario.surfaceHeights(chunkX, chunkZ),
                            scenario.boundaries(chunkX, chunkZ), null, null, supportPlan);
                }
                capture.resolving = true;
                supportPlan.resolve(writer.acquireChunk(chunkX, chunkZ));
                capture.finishResolve();
            }
        }
        return new Result(capture.hash, capture.writes, carved);
    }

    private Engine createEngine(Scenario scenario) {
        Engine engine = mock(Engine.class, withSettings().stubOnly());
        IrisData data = mock(IrisData.class, withSettings().stubOnly());
        IrisDimension dimension = mock(IrisDimension.class, withSettings().stubOnly());
        SeedManager seedManager = mock(SeedManager.class, withSettings().stubOnly());
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(CARVE_SEED).when(seedManager).getCarve();
        doReturn(new EngineMetrics(16)).when(engine).getMetrics();
        doReturn(IrisWorld.builder().minHeight(-32).maxHeight(WORLD_HEIGHT - 32).build()).when(engine).getWorld();
        doReturn(WORLD_HEIGHT).when(engine).getHeight();
        doReturn(110).when(engine).getHeight(anyInt(), anyInt());
        doReturn(24).when(dimension).getCaveLavaHeight();
        doReturn(70).when(dimension).getFluidHeight();
        doReturn(scenario == Scenario.VERTICAL_STEP ? 4 : 1).when(dimension).getCaveDensitySamplingStep();
        return engine;
    }

    private static IrisCaveProfile baseProfile(boolean warp, boolean modules) {
        IrisCaveProfile profile = new IrisCaveProfile();
        profile.setEnabled(true);
        profile.setVerticalRange(new IrisRange(4D, 150D));
        profile.setVerticalEdgeFade(14);
        profile.setVerticalEdgeFadeStrength(0.21D);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.21D));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.43D));
        profile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.37D));
        profile.setSurfaceBreakStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.140625D));
        profile.setBaseWeight(1D);
        profile.setDetailWeight(0.42D);
        profile.setWarpStrength(warp ? 3.7D : 0D);
        profile.setDensityThreshold(new IrisStyledRange(-0.18D, -0.18D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
        profile.setThresholdBias(0D);
        profile.setSampleStep(1);
        profile.setSurfaceClearance(5);
        profile.setAllowSurfaceBreak(true);
        profile.setSurfaceBreakNoiseThreshold(0.3D);
        profile.setSurfaceBreakDepth(14);
        profile.setSurfaceBreakThresholdBoost(0.21D);
        profile.setAllowFluid(false);
        profile.setAllowLava(true);
        KList<IrisCaveFieldModule> caveModules = new KList<>();
        if (modules) {
            caveModules.add(new IrisCaveFieldModule(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.33D),
                    0.27D, 0.05D, new IrisRange(0D, 90D), false));
            caveModules.add(new IrisCaveFieldModule(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.51D),
                    0.19D, -0.07D, new IrisRange(40D, 150D), true));
        }
        profile.setModules(caveModules);
        return profile;
    }

    private static IrisCaveProfile packProfile(NoiseStyle warp, double warpZoom, double warpStrength, NoiseStyle base,
                                               double baseZoom, double threshold, IrisCaveFieldModule... modules) {
        IrisCaveProfile profile = baseProfile(true, false);
        profile.setWarpStyle(new IrisGeneratorStyle(warp).zoomed(warpZoom));
        profile.setWarpStrength(warpStrength);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(base).zoomed(baseZoom));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.5D));
        profile.setBaseWeight(0.82D);
        profile.setDetailWeight(0.15D);
        profile.setDensityThreshold(new IrisStyledRange(threshold - 0.06D, threshold + 0.05D,
                new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.78D)));
        profile.setThresholdBias(0.12D);
        profile.setSampleStep(2);
        profile.setModules(new KList<>(modules));
        return profile;
    }

    private static IrisCaveFieldModule packModule(NoiseStyle style, double zoom, double weight, double threshold,
                                                  double minY, double maxY, boolean invert) {
        return new IrisCaveFieldModule(new IrisGeneratorStyle(style).zoomed(zoom), weight, threshold,
                new IrisRange(minY, maxY), invert);
    }

    private enum Scenario {
        PACK_SHARED,
        WARP_MODULES,
        WARP_TERRAIN,
        STYLED_BLEND,
        FLUID_BOUNDARY,
        NO_WARP_MODULES,
        VERTICAL_STEP,
        LATTICE,
        FLOOR_FLUID_LAYERED;

        IrisCaveProfile[] profiles() {
            if (this == PACK_SHARED) {
                return new IrisCaveProfile[]{
                        packProfile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D, NoiseStyle.PERLIN_IRIS, 0.72D, -0.02D,
                                packModule(NoiseStyle.FRACTAL_FBM_SIMPLEX, 0.24D, 0.08D, 0.03D, 10D, 140D, false),
                                packModule(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 0.05D, 0.07D, 4D, 90D, true)),
                        packProfile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D, NoiseStyle.FRACTAL_BILLOW_SIMPLEX, 0.656D, 0.01D,
                                packModule(NoiseStyle.CELLULAR_BILINEAR, 1.32D, 0.06D, 0.02D, 20D, 150D, false)),
                        packProfile(NoiseStyle.FRACTAL_WATER, 0.44D, 3.2D, NoiseStyle.PERLIN_IRIS, 0.72D, 0.04D,
                                packModule(NoiseStyle.FRACTAL_FBM_SIMPLEX, 0.24D, 0.08D, 0.03D, 10D, 140D, false),
                                packModule(NoiseStyle.SIMPLEX_VASCULAR, 1.02D, 0.05D, 0.07D, 4D, 90D, true)),
                        packProfile(NoiseStyle.PERLIN_IRIS, 0.35D, 2.4D, NoiseStyle.IRIS_DOUBLE, 0.68D, -0.04D,
                                packModule(NoiseStyle.VASCULAR_THIN, 1.28D, 0.04D, 0.05D, 8D, 120D, false))
                };
            }
            if (this == FLOOR_FLUID_LAYERED) {
                return new IrisCaveProfile[]{
                        baseProfile(true, true).setAllowFluid(true).setFluidRequiresFloor(true).setFluidMinDepthBelowSurface(4),
                        baseProfile(true, false).setAllowFluid(true).setFluidRequiresFloor(true).setFluidMinDepthBelowSurface(2)
                                .setAllowLava(false).setDensityThreshold(new IrisStyledRange(-0.24D, -0.08D,
                                        new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.45D)))
                };
            }
            return new IrisCaveProfile[]{profile()};
        }

        IrisCaveProfile profile() {
            return switch (this) {
                case WARP_MODULES, VERTICAL_STEP -> baseProfile(true, true);
                case WARP_TERRAIN -> baseProfile(true, false);
                case STYLED_BLEND -> baseProfile(false, false).setDensityThreshold(new IrisStyledRange(-0.32D, -0.04D,
                        new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.6D)));
                case FLUID_BOUNDARY -> baseProfile(true, true).setAllowFluid(true).setFluidRequiresFloor(false)
                        .setFluidMinDepthBelowSurface(6).setAllowLava(false);
                case NO_WARP_MODULES -> baseProfile(false, true);
                case LATTICE -> baseProfile(true, true).setSampleStep(3);
                case FLOOR_FLUID_LAYERED, PACK_SHARED -> baseProfile(true, true);
            };
        }

        double[] weights(int layer, int chunkX, int chunkZ) {
            double[] weights = new double[256];
            for (int index = 0; index < 256; index++) {
                int worldX = (chunkX << 4) + (index >> 4);
                int worldZ = (chunkZ << 4) + (index & 15);
                double blend = 0.5D + 0.6D * Math.sin(worldX * 0.11D) * Math.cos(worldZ * 0.07D);
                weights[index] = switch (this) {
                    case PACK_SHARED -> 0.5D + 0.6D * Math.sin(worldX * 0.13D + layer * 1.7D) * Math.cos(worldZ * 0.09D - layer);
                    case STYLED_BLEND, FLUID_BOUNDARY -> blend;
                    case FLOOR_FLUID_LAYERED -> layer == 0 ? blend : 1D - blend;
                    default -> 1D;
                };
            }
            return weights;
        }

        int[] surfaceHeights(int chunkX, int chunkZ) {
            int[] heights = new int[256];
            for (int index = 0; index < 256; index++) {
                int worldX = (chunkX << 4) + (index >> 4);
                int worldZ = (chunkZ << 4) + (index & 15);
                heights[index] = this == WARP_MODULES || this == LATTICE || this == FLOOR_FLUID_LAYERED
                        ? 118
                        : 96 + (int) Math.round(22D * Math.sin(worldX * 0.09D) + 15D * Math.cos(worldZ * 0.13D));
            }
            return heights;
        }

        IrisRange worldYRange() {
            return this == FLUID_BOUNDARY || this == FLOOR_FLUID_LAYERED ? new IrisRange(-20D, 100D) : null;
        }

        long[] boundaries(int chunkX, int chunkZ) {
            long[] boundaries = new long[256];
            Arrays.fill(boundaries, SurfaceFluidBoundaryPlan.boundary(SurfaceFluidBoundaryPlan.NO_BOUNDARY, Integer.MIN_VALUE));
            if (this != FLUID_BOUNDARY && this != WARP_TERRAIN) {
                return boundaries;
            }
            for (int index = 0; index < 256; index++) {
                int worldX = (chunkX << 4) + (index >> 4);
                if (Math.floorMod(worldX + chunkZ, 5) == 0) {
                    boundaries[index] = SurfaceFluidBoundaryPlan.boundary(60 + Math.floorMod(index, 7), 82);
                }
            }
            return boundaries;
        }
    }

    private record Result(long hash, int writes, int carved) {
    }

    private static final class Capture {
        private final Map<Long, Map<Integer, Matter>> chunks = new HashMap<>();
        private final IdentityHashMap<MatterCavern, Integer> identities = new IdentityHashMap<>();
        private final List<Long> resolved = new ArrayList<>();
        private long hash = 0xcbf29ce484222325L;
        private int writes;
        private boolean resolving;

        private MantleWriter writer(int chunkX, int chunkZ) {
            MantleWriter writer = mock(MantleWriter.class, withSettings().stubOnly());
            @SuppressWarnings("unchecked")
            Mantle<Matter> mantle = mock(Mantle.class, withSettings().stubOnly());
            @SuppressWarnings("unchecked")
            MantleChunk<Matter> chunk = mock(MantleChunk.class, withSettings().stubOnly());
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            Map<Integer, Matter> sections = chunks.computeIfAbsent(key, ignored -> new HashMap<>());
            doReturn(mantle).when(writer).getMantle();
            doReturn(WORLD_HEIGHT).when(mantle).getWorldHeight();
            doReturn(chunk).when(writer).acquireChunk(anyInt(), anyInt());
            doAnswer(invocation -> sections.get(invocation.getArgument(0, Integer.class))).when(chunk).get(anyInt());
            doAnswer(invocation -> sections.computeIfAbsent(invocation.getArgument(0, Integer.class),
                    sectionIndex -> createSection(chunkX, chunkZ, sectionIndex))).when(chunk).getOrCreate(anyInt());
            return writer;
        }

        private Matter createSection(int chunkX, int chunkZ, int sectionIndex) {
            mix(0x5EC7L);
            mix(chunkX);
            mix(chunkZ);
            mix(sectionIndex);
            Matter matter = mock(Matter.class, withSettings().stubOnly());
            MatterSlice<MatterCavern> slice = new RecordingCavernMatter(this, sectionIndex);
            doReturn(slice).when(matter).slice(MatterCavern.class);
            doReturn(slice).when(matter).getSlice(MatterCavern.class);
            return matter;
        }

        private void record(int sectionIndex, int x, int y, int z, MatterCavern value) {
            writes++;
            if (resolving) {
                resolved.add(((long) sectionIndex << 20) | ((long) x << 16) | ((long) y << 12) | ((long) z << 8) | value.getLiquid());
                return;
            }
            mix(sectionIndex);
            mix((x << 8) | (y << 4) | z);
            mix(value == null ? -1 : value.getLiquid());
            Integer identity = identities.get(value);
            if (identity == null) {
                identity = identities.size();
                identities.put(value, identity);
            }
            mix(identity);
        }

        private void finishResolve() {
            resolving = false;
            resolved.sort(Long::compare);
            for (long entry : resolved) {
                mix(entry);
            }
            resolved.clear();
        }

        private void mix(long value) {
            hash ^= value;
            hash *= 0x100000001b3L;
        }
    }

    private static final class RecordingCavernMatter extends CavernMatter {
        private final Capture capture;
        private final int sectionIndex;

        private RecordingCavernMatter(Capture capture, int sectionIndex) {
            super(16, 16, 16);
            this.capture = capture;
            this.sectionIndex = sectionIndex;
        }

        @Override
        public void set(int x, int y, int z, MatterCavern value) {
            super.set(x, y, z, value);
            capture.record(sectionIndex, x, y, z, value);
        }
    }
}
