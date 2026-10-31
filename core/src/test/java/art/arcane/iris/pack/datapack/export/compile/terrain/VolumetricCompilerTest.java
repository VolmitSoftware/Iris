package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseStyleCompiler;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.compile.noise.ValueDistributionFit;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VolumetricCompilerTest {
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(DataVersion.V26_3);
    private static final int SEA_LEVEL = 63;
    private static final double VOLUME_AMPLITUDE = 24.0;
    private static final double VOLUME_SCALE = 96.0;
    private static final double CRACK_DEPTH = 12.0;
    private static final double CRACK_WIDTH = 15.0;
    private static final double CRACK_SCALE = 96.0;
    private static final long MARGINAL_SEED = 8191L;
    private static final int MARGINAL_SAMPLES = 6000;
    private static final int MARGINAL_SPAN = 1 << 20;
    private static final int SHAPED_FLOOR = SEA_LEVEL + 8;
    private static final int SHAPED_CEILING = 320;
    private static final String ISLAND = """
            {"name":"Sky","floatingChildBiomes":[{"topShapeMode":"FLAT","maxTopHeight":10,"minHeightAboveSurface":160,
             "maxHeightAboveSurface":160,"bottomDepthMin":4,"bottomDepthMax":4,"footprintThreshold":0.5}]}""";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void terrain3dVariantsSitBehindGates() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 20, 140), TerrainTestPacks.link("hills", 10, 60));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_9", 12, "SIMPLEX"));
        TerrainTestPacks.biome(pack, "a", "{\"name\":\"A\",\"generators\":[" + TerrainTestPacks.link("hills", 20, 140)
                + "],\"terrain3D\":" + TerrainTestPacks.TERRAIN3D + "}");
        TerrainTestPacks.biome(pack, "b", "{\"name\":\"B\",\"generators\":[" + TerrainTestPacks.link("hills", 10, 60)
                + "],\"terrain3D\":{\"amplitude\":12,\"horizontalScale\":32,\"verticalScale\":12}}");
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            Compiled compiled = compile(data, registry, TerrainTestSupport.noiseStyles(), codes);
            int shaped = 0;

            assertTrue(compiled.volumetric().axes().contains(Axis.Y));
            assertTrue(TerrainTestSupport.unguardedExpensiveBranches(compiled.volumetric()).toString(),
                    TerrainTestSupport.unguardedExpensiveBranches(compiled.volumetric()).isEmpty());
            for (int x = -800; x <= 800; x += 37) {
                for (int z = -800; z <= 800; z += 41) {
                    assertEquals(0.0, TerrainTestSupport.sample(compiled.volumetric(), registry, DataVersion.V26_3, x, SEA_LEVEL + 4, z), 0.0);
                    double surface = TerrainTestSupport.sample(compiled.height(), registry, DataVersion.V26_3, x, 0, z);
                    double displacement = TerrainTestSupport.sample(compiled.volumetric(), registry, DataVersion.V26_3, x, (int) surface, z);
                    assertTrue("displacement " + displacement, Math.abs(displacement) <= 24.0 + 12.0 + 1.0e-3);
                    if (Math.abs(displacement) > 0.5) {
                        shaped++;
                    }
                }
            }
            assertTrue("terrain3D should shape some columns, got " + shaped, shaped > 0);
        } finally {
            data.close();
        }
    }

    @Test
    public void displacementFollowsTheVolumetricNoiseMarginal() throws Exception {
        IrisData data = IrisData.openRuntime(uniformTerrain3d(temporaryFolder.newFolder("pack"), VOLUME_AMPLITUDE, 0));
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            Compiled compiled = compile(data, registry, NoiseStyleCompiler.create(new ArrayList<>()), TerrainTestSupport.twoBiomeCodes(registry, 0.0));
            CNG iris = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).createNoCache(new RNG(MARGINAL_SEED), data);
            SplittableRandom random = new SplittableRandom(MARGINAL_SEED);
            double[] exported = new double[MARGINAL_SAMPLES];
            double[] expected = new double[MARGINAL_SAMPLES];
            for (int index = 0; index < MARGINAL_SAMPLES; index++) {
                int x = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
                int y = random.nextInt(SHAPED_FLOOR, SHAPED_CEILING);
                int z = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
                exported[index] = TerrainTestSupport.sample(compiled.volumetric(), registry, DataVersion.V26_3, x, y, z) / VOLUME_AMPLITUDE;
                expected[index] = Math.clamp(iris.noiseFastSigned3D(x * 64.0 / VOLUME_SCALE, y * 64.0 / VOLUME_SCALE, z * 64.0 / VOLUME_SCALE), -1.0, 1.0);
            }
            Arrays.sort(exported);
            Arrays.sort(expected);

            for (double level : new double[]{0.02, 0.05, 0.25, 0.75, 0.95, 0.98}) {
                assertEquals("quantile " + level, ValueDistributionFit.quantile(expected, level), ValueDistributionFit.quantile(exported, level), 0.05);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void fissuresOpenAsOftenAsTheVolumetricCrackNoiseAllows() throws Exception {
        IrisData data = IrisData.openRuntime(uniformTerrain3d(temporaryFolder.newFolder("pack"), 0, CRACK_DEPTH));
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            Compiled compiled = compile(data, registry, NoiseStyleCompiler.create(new ArrayList<>()), TerrainTestSupport.twoBiomeCodes(registry, 0.0));
            CNG iris = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).createNoCache(new RNG(MARGINAL_SEED), data);
            SplittableRandom random = new SplittableRandom(MARGINAL_SEED);
            int exported = 0;
            int expected = 0;
            for (int index = 0; index < MARGINAL_SAMPLES; index++) {
                int x = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
                int y = random.nextInt(SHAPED_FLOOR, SHAPED_CEILING);
                int z = random.nextInt(-MARGINAL_SPAN, MARGINAL_SPAN);
                if (TerrainTestSupport.sample(compiled.volumetric(), registry, DataVersion.V26_3, x, y, z) < 0.0) {
                    exported++;
                }
                double frequency = 64.0 / CRACK_SCALE;
                double distance = CRACK_SCALE * 0.5 * Math.clamp(iris.noiseFastSigned3D(x * frequency, y * frequency * 0.25, z * frequency), -1.0, 1.0);
                if (Math.abs(distance) < CRACK_WIDTH) {
                    expected++;
                }
            }

            assertEquals(expected / (double) MARGINAL_SAMPLES, exported / (double) MARGINAL_SAMPLES, 0.02);
        } finally {
            data.close();
        }
    }

    @Test
    public void floatingIslandIsSolidBetweenItsBaseAndTop() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"), "", "");
        TerrainTestPacks.biome(pack, "b", ISLAND);
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            Compiled compiled = compile(data, registry, TerrainTestSupport.constantStyles(0.75), codes);
            int island = 0;
            int ground = 0;

            assertTrue(TerrainTestSupport.unguardedExpensiveBranches(compiled.volumetric()).isEmpty());
            for (int x = -1200; x <= 1200; x += 53) {
                double code = TerrainTestSupport.sample(codes.surfaceCode(), registry, DataVersion.V26_3, x, 0, 9);
                boolean sky = code > 0.2;
                for (int y : new int[]{150, 155, 156, 163, 170, 171, 180}) {
                    double density = SEA_LEVEL - y + TerrainTestSupport.sample(compiled.volumetric(), registry, DataVersion.V26_3, x, y, 9);
                    boolean solid = density > 0.0;
                    assertEquals("x " + x + " y " + y + " density " + density, sky && y >= 156 && y <= 170, solid);
                }
                if (sky) {
                    island++;
                } else {
                    ground++;
                }
            }
            assertTrue(island > 0 && ground > 0);
        } finally {
            data.close();
        }
    }

    @Test
    public void unexportedIslandFeaturesReportDiagnostics() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"), "", "");
        TerrainTestPacks.biome(pack, "b", """
                {"name":"Sky","floatingChildBiomes":[{"topShapeMode":"BIOME","wallWarpStyle":{"style":"SIMPLEX"},"localFluidHeight":3}]}""");
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            List<ExportDiagnostic> diagnostics = new ArrayList<>();
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            TerrainHeight height = new HeightCompiler(TerrainTestSupport.noiseStyles(), diagnostics).compile(codes, dimension, data, registry, CAPABILITIES);

            new VolumetricCompiler(TerrainTestSupport.noiseStyles(), diagnostics).compile(height, codes, dimension, data, registry, CAPABILITIES);

            assertEquals(1, diagnostics.size());
            assertEquals(ExportLimit.FLOATING_ISLAND, diagnostics.getFirst().limit());
            assertEquals("biomes/b", diagnostics.getFirst().resource());
        } finally {
            data.close();
        }
    }

    @Test
    public void packWithoutVolumetricFeaturesIsNeutral() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20), TerrainTestPacks.link("hills", 40, 90));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

            Compiled compiled = compile(data, registry, TerrainTestSupport.noiseStyles(), TerrainTestSupport.twoBiomeCodes(registry, 0.0));

            assertEquals(new Field.Const(0.0), compiled.volumetric());
        } finally {
            data.close();
        }
    }

    private static File uniformTerrain3d(File folder, double amplitude, double crackDepth) throws Exception {
        File pack = TerrainTestPacks.twoBiomes(folder, TerrainTestPacks.link("flat", 100, 100), TerrainTestPacks.link("flat", 100, 100));
        TerrainTestPacks.generator(pack, "flat", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_9", 12, "FLAT"));
        String terrain = "{\"amplitude\":" + amplitude + ",\"horizontalScale\":" + VOLUME_SCALE + ",\"verticalScale\":" + VOLUME_SCALE
                + ",\"densityStyle\":{\"style\":\"SIMPLEX\"},\"crackDepth\":" + crackDepth + ",\"crackWidth\":" + CRACK_WIDTH
                + ",\"crackScale\":" + CRACK_SCALE + ",\"crackStyle\":{\"style\":\"SIMPLEX\"},\"minimumSlope\":0,\"fluidClearance\":0,\"fluidFade\":1}";
        for (String key : new String[]{"a", "b"}) {
            TerrainTestPacks.biome(pack, key, "{\"name\":\"" + key + "\",\"generators\":[" + TerrainTestPacks.link("flat", 100, 100)
                    + "],\"terrain3D\":" + terrain + "}");
        }
        return pack;
    }

    private static Compiled compile(IrisData data, NoiseRegistry registry, StyleCompiler styles, BiomeCodes codes) {
        IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        TerrainHeight terrain = new HeightCompiler(styles, diagnostics).compile(codes, dimension, data, registry, CAPABILITIES);
        Field volumetric = new VolumetricCompiler(styles, diagnostics).compile(terrain, codes, dimension, data, registry, CAPABILITIES);
        return new Compiled(terrain.height(), volumetric);
    }

    private record Compiled(Field height, Field volumetric) {
    }
}
