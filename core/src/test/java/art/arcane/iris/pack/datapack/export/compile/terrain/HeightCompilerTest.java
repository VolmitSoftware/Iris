package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeightCompilerTest {
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(DataVersion.V26_3);
    private static final int SEA_LEVEL = 63;
    private static final int BLEND_RADIUS = 12;
    private static final int BOUNDS_STEP = 4;
    private static final double LOW_MIDPOINT = 10.0;
    private static final double HIGH_MIDPOINT = 80.0;
    private static final double STEPS = 4.0;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void heightRootIsTwoDimensional() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20), TerrainTestPacks.link("hills", 40, 90));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

            Field height = compile(data, registry, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(registry, 0.0));

            assertEquals(HeightCompiler.HEIGHT, ((Field.Shared) height).name());
            FieldValidator.requireTwoDimensional(height, "height");
            assertFalse(height.axes().contains(Axis.Y));
            for (int x = -500; x <= 500; x += 41) {
                double value = TerrainTestSupport.sample(height, registry, DataVersion.V26_3, x, 0, x / 3);
                assertTrue("height " + value, value >= SEA_LEVEL && value <= SEA_LEVEL + 90);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void bandBlendRmsWithin0_13() throws Exception {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        double rms = blendRms(registry, TerrainTestSupport.twoBiomeCodes(registry, 0.0));

        assertTrue("band blend rms " + rms, rms <= 0.13);
    }

    @Test
    public void steppedSelectorBandBlendRmsWithin0_13() throws Exception {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        double rms = blendRms(registry, steppedCodes(registry));

        assertTrue("stepped band blend rms " + rms, rms <= 0.13);
    }

    @Test
    public void slopeIsTheForwardDifferenceOfTheRelief() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 40), TerrainTestPacks.link("hills", 0, 40));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            TerrainHeight terrain = new HeightCompiler(TerrainTestSupport.noiseStyles(), new ArrayList<>())
                    .compile(TerrainTestSupport.singleBiomeCodes("a"), dimension, data, registry, CAPABILITIES);

            for (int x = -900; x <= 900; x += 113) {
                int z = 2 * x / 3;
                double here = TerrainTestSupport.sample(terrain.height(), registry, DataVersion.V26_3, x, 0, z);
                double east = (TerrainTestSupport.sample(terrain.height(), registry, DataVersion.V26_3, x + 4, 0, z) - here) / 4.0;
                double south = (TerrainTestSupport.sample(terrain.height(), registry, DataVersion.V26_3, x, 0, z + 4) - here) / 4.0;
                double slope = TerrainTestSupport.sample(terrain.slope(), registry, DataVersion.V26_3, x, 0, z);
                assertEquals("slope at " + x + "," + z, Math.sqrt(east * east + south * south), slope, 1.0e-3);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void slopeNeverCopiesTheBiomeSelector() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20), TerrainTestPacks.link("hills", 40, 90));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            TerrainHeight terrain = new HeightCompiler(TerrainTestSupport.noiseStyles(), new ArrayList<>())
                    .compile(TerrainTestSupport.twoBiomeCodes(registry, 0.0), dimension, data, registry, CAPABILITIES);
            String selector = TerrainTestSupport.NAMESPACE + ":" + TerrainTestSupport.SELECTOR_ID;

            for (Field node : TerrainTestSupport.nodes(new Field.Binary(Field.BinaryOp.ADD, terrain.height(), terrain.slope()))) {
                if (node instanceof Field.Noise noise && noise.noiseId().equals(selector)) {
                    assertTrue("selector sampled with a shift: " + noise, noise.shiftX() == null && noise.shiftZ() == null);
                }
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void heightCliffsStepByTheBandSpan() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("terraces", 0, 51), TerrainTestPacks.link("terraces", 0, 51));
        TerrainTestPacks.generator(pack, "terraces", """
                {"interpolator":{"function":"BILINEAR_STARCAST_9","horizontalScale":12},"cliffHeightMin":50,"cliffHeightMax":50,
                 "composite":[{"style":{"style":"SIMPLEX"},"seed":3}]}""");
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            Field height = compile(data, registry, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(registry, 0.0));
            double step = 51.0 * 50.0 / 255.0;

            for (int x = -600; x <= 600; x += 5) {
                double rise = (TerrainTestSupport.sample(height, registry, DataVersion.V26_3, x, 0, 17) - SEA_LEVEL) / step;
                assertEquals("terrace " + rise + " at x " + x, Math.rint(rise), rise, 1.0e-3);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void silentBucketsLeaveSeaLevel() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 0), TerrainTestPacks.link("hills", 0, 0));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

            Field height = compile(data, registry, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(registry, 0.0));

            assertEquals(new Field.Shared(HeightCompiler.HEIGHT, new Field.Const(SEA_LEVEL)), height);
        } finally {
            data.close();
        }
    }

    @Test
    public void wideBucketsBlendOnCoarseLattices() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("ranges", 0, 20), TerrainTestPacks.link("ranges", 60, 120));
        TerrainTestPacks.generator(pack, "ranges", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_12", 200, "SIMPLEX"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

            Field height = compile(data, registry, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(registry, 0.0));

            assertTrue(containsGrid(height, 512));
        } finally {
            data.close();
        }
    }

    @Test
    public void compileIsDeterministic() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20) + "," + TerrainTestPacks.link("dunes", 5, 9),
                TerrainTestPacks.link("hills", 40, 90));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        TerrainTestPacks.generator(pack, "dunes", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_9", 23, "PERLIN"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry first = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            NoiseRegistry second = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

            Field one = compile(data, first, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(first, 0.0));
            Field two = compile(data, second, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(second, 0.0));

            assertEquals(one, two);
            assertEquals(first.definitions(), second.definitions());
        } finally {
            data.close();
        }
    }

    private double blendRms(NoiseRegistry registry, BiomeCodes codes) throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20), TerrainTestPacks.link("hills", 60, 100));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_9", BLEND_RADIUS, "SIMPLEX"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            Field height = compile(data, registry, TerrainTestSupport.constantStyles(0.5), codes);
            IrisBands iris = new IrisBands(codes.surfaceCode(), registry);
            double squared = 0.0;
            int zone = 0;
            for (int x = 0; x < 768; x += 3) {
                for (int z = 0; z < 768; z += 3) {
                    double reference = SEA_LEVEL + iris.height(x, z);
                    double weight = (reference - SEA_LEVEL - LOW_MIDPOINT) / (HIGH_MIDPOINT - LOW_MIDPOINT);
                    if (weight < 0.02 || weight > 0.98) {
                        continue;
                    }
                    double exported = TerrainTestSupport.sample(height, registry, DataVersion.V26_3, x, 0, z);
                    double error = (exported - reference) / (HIGH_MIDPOINT - LOW_MIDPOINT);
                    squared += error * error;
                    zone++;
                }
            }
            assertTrue("transition zone too small: " + zone, zone > 500);
            return Math.sqrt(squared / zone);
        } finally {
            data.close();
        }
    }

    private static BiomeCodes steppedCodes(NoiseRegistry registry) {
        BiomeCodes smooth = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        Field.Partition partition = (Field.Partition) smooth.surfaceCode();
        Field stepped = new Field.Round(Field.RoundMode.ROUND, FieldMacros.scale(partition.input(), STEPS), new Field.Const(1.0));
        Field code = new Field.Partition(stepped, List.of(0.5), partition.leaves());
        return new BiomeCodes(code, smooth.caveCode(), smooth.class3d(), smooth.layout(), smooth.loadKeyByBiomeId());
    }

    private static Field compile(IrisData data, NoiseRegistry registry, StyleCompiler styles, BiomeCodes codes) {
        IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
        return new HeightCompiler(styles, new ArrayList<>()).compile(codes, dimension, data, registry, CAPABILITIES).height();
    }

    private static boolean containsGrid(Field root, int cell) {
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (field instanceof Field.Grid grid && grid.cellXZ() == cell) {
                return true;
            }
            for (Field child : FieldMacros.children(field)) {
                pending.push(child);
            }
        }
        return false;
    }

    private static final class IrisBands implements NoiseBoundsProvider {
        private final Field code;
        private final NoiseRegistry registry;
        private final Map<Long, NoiseBounds> samples = new HashMap<>();
        private final Map<Long, NoiseBounds> corners = new HashMap<>();

        private IrisBands(Field code, NoiseRegistry registry) {
            this.code = code;
            this.registry = registry;
        }

        @Override
        public NoiseBounds noise(double x, double z) {
            int blockX = (int) x;
            int blockZ = (int) z;
            return samples.computeIfAbsent(key(blockX, blockZ), ignored -> {
                double value = TerrainTestSupport.sample(code, registry, DataVersion.V26_3, blockX, 0, blockZ);
                return value < 0.2 ? new NoiseBounds(0.0, 20.0) : new NoiseBounds(60.0, 100.0);
            });
        }

        private double height(int x, int z) {
            int gridX = Math.floorDiv(x, BOUNDS_STEP) * BOUNDS_STEP;
            int gridZ = Math.floorDiv(z, BOUNDS_STEP) * BOUNDS_STEP;
            double fractionX = (x - gridX) / (double) BOUNDS_STEP;
            double fractionZ = (z - gridZ) / (double) BOUNDS_STEP;
            NoiseBounds northWest = corner(gridX, gridZ);
            NoiseBounds northEast = corner(gridX + BOUNDS_STEP, gridZ);
            NoiseBounds southWest = corner(gridX, gridZ + BOUNDS_STEP);
            NoiseBounds southEast = corner(gridX + BOUNDS_STEP, gridZ + BOUNDS_STEP);
            double low = bilinear(northWest.min(), northEast.min(), southWest.min(), southEast.min(), fractionX, fractionZ);
            double high = bilinear(northWest.max(), northEast.max(), southWest.max(), southEast.max(), fractionX, fractionZ);
            return low + (high - low) * 0.5;
        }

        private NoiseBounds corner(int x, int z) {
            return corners.computeIfAbsent(key(x, z),
                    ignored -> IrisInterpolation.getNoiseBounds(InterpolationMethod.BILINEAR_STARCAST_9, x, z, BLEND_RADIUS, this));
        }

        private static double bilinear(double northWest, double northEast, double southWest, double southEast, double fractionX, double fractionZ) {
            double north = northWest + (northEast - northWest) * fractionX;
            double south = southWest + (southEast - southWest) * fractionX;
            return north + (south - north) * fractionZ;
        }

        private static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }
}
