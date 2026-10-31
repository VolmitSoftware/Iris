package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaveFieldCompilerTest {
    private static final Field DEEP_SURFACE = new Field.Const(150.0);
    private static final double OPEN_SHARE_THRESHOLD = 0.15;
    private static final int BAND_HEIGHT = 16;
    private static final int FINE_ORIGIN_X = -900;
    private static final int BROAD_ORIGIN_X = 120;
    private static final int SHELL_BOXES = 6;
    private static final int SHELL_BOX = 24;
    private static final int SHELL_BOX_HEIGHT = 48;
    private static final int SHELL_BOX_Y = -40;
    private static final int SHELL_BOX_STEP_X = 130;
    private static final int SHELL_BOX_STEP_Z = 230;
    private static final long MIN_WALLS = 200L;
    private static final double SHELL_TOLERANCE = 0.2;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void profileSelectedByCaveCode() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-open", "cave-closed"), null),
                Map.of("cave-open", CaveTestPacks.constantProfile(5.0), "cave-closed", CaveTestPacks.constantProfile(-5.0)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(
                    new CaveTestPacks.CodeRange("cave-open", -1.0, 0.0),
                    new CaveTestPacks.CodeRange("cave-closed", 0.0, 1.0)));

            new CaveStage().apply(context);

            Field caves = context.caves();
            assertTrue(CaveTestPacks.caveField(caves) instanceof Field.Grid);
            assertTrue(((Field.Grid) CaveTestPacks.caveField(caves)).expr() instanceof Field.Mux);
            assertTrue(CaveTestPacks.sample(caves, context.noises(), -500, 0, 0) <= 0.0);
            assertTrue(CaveTestPacks.sample(caves, context.noises(), 500, 0, 0) > 0.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void uncoveredCaveCodesUseTheDimensionProfile() throws Exception {
        JsonObject dimension = CaveTestPacks.dimension(8);
        dimension.add("caveProfile", CaveTestPacks.json(CaveTestPacks.constantProfile(5.0)));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension,
                CaveTestPacks.region(List.of("cave-closed"), null),
                Map.of("cave-closed", CaveTestPacks.constantProfile(-5.0)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(
                    new CaveTestPacks.CodeRange("cave-closed", 0.0, 1.0)));

            new CaveStage().apply(context);

            assertTrue(CaveTestPacks.sample(context.caves(), context.noises(), -500, 0, 0) <= 0.0);
            assertTrue(CaveTestPacks.sample(context.caves(), context.noises(), 500, 0, 0) > 0.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void wallShellBandHoldsTheOneBlockWallOfTheCaveField() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-noise"), null), Map.of("cave-noise", CaveTestPacks.profile()));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(new CaveTestPacks.CodeRange("cave-noise", -1.0, 1.0)));

            new CaveStage().apply(context);

            double band = context.wallShellBand();
            Field field = context.caveField().orElseThrow();
            long solid = 0L;
            long walls = 0L;
            long shell = 0L;
            for (int column = 0; column < 64; column++) {
                int x = 37 * column - 1200;
                int z = 53 * (column % 8) + 400;
                for (int y = -40; y < 120; y += 2) {
                    double value = CaveTestPacks.sample(field, context.noises(), x, y, z);
                    if (value < 0.0) {
                        continue;
                    }
                    solid++;
                    if (value < band) {
                        shell++;
                    }
                    if (CaveTestPacks.sample(field, context.noises(), x + 1, y, z) < 0.0 || CaveTestPacks.sample(field, context.noises(), x - 1, y, z) < 0.0
                            || CaveTestPacks.sample(field, context.noises(), x, y, z + 1) < 0.0 || CaveTestPacks.sample(field, context.noises(), x, y, z - 1) < 0.0) {
                        walls++;
                    }
                }
            }
            assertTrue("band " + band, band > 0.0 && band < 0.5);
            assertTrue(walls + " walls of " + solid, walls > 50L);
            assertEquals("shell " + shell + " walls " + walls + " solid " + solid, 1.0, shell / (double) walls, 0.25);
        } finally {
            data.close();
        }
    }

    @Test
    public void wallShellPaintsTheOneBlockWallOfEveryProfile() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-fine", "cave-broad"), null), Map.of(
                        "cave-fine", CaveTestPacks.profile().setBaseDensityStyle(CaveTestPacks.style(NoiseStyle.SIMPLEX, 0.25)),
                        "cave-broad", CaveTestPacks.profile().setBaseDensityStyle(CaveTestPacks.style(NoiseStyle.SIMPLEX, 1.5))));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(
                    new CaveTestPacks.CodeRange("cave-fine", -1.0, 0.0),
                    new CaveTestPacks.CodeRange("cave-broad", 0.0, 1.0)));

            new CaveStage().apply(context);

            Field field = context.caveField().orElseThrow();
            double band = context.wallShellBand();
            ShellCount fine = shellCount(field, context, band, FINE_ORIGIN_X);
            ShellCount broad = shellCount(field, context, band, BROAD_ORIGIN_X);
            assertTrue(fine.toString(), fine.walls() > MIN_WALLS);
            assertTrue(broad.toString(), broad.walls() > MIN_WALLS);
            assertEquals(fine.toString(), 1.0, fine.ratio(), SHELL_TOLERANCE);
            assertEquals(broad.toString(), 1.0, broad.ratio(), SHELL_TOLERANCE);
        } finally {
            data.close();
        }
    }

    @Test
    public void caveBiomeWithoutProfileUsesItsRegionProfile() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-plain"), CaveTestPacks.constantProfile(5.0)),
                Map.of("cave-plain", new IrisCaveProfile()));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(
                    new CaveTestPacks.CodeRange("cave-plain", -1.0, 1.0)));

            new CaveStage().apply(context);

            assertTrue(CaveTestPacks.sample(context.caves(), context.noises(), 300, 0, 0) <= 0.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void threeDimensionalCellularOnlyInsideGrid() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile()
                .setBaseDensityStyle(CaveTestPacks.style(NoiseStyle.CELLULAR_IRIS_DOUBLE, 0.7))
                .setModules(new KList<>(List.of(CaveTestPacks.module(NoiseStyle.CELLULAR, 0.3, 0.0, false, new IrisRange(0, 256)))));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-cells"), null), Map.of("cave-cells", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.expensiveCellular(), List.of(
                    new CaveTestPacks.CodeRange("cave-cells", -1.0, 1.0)));

            new CaveStage().apply(context);

            List<Field.Noise> outside = new ArrayList<>();
            List<Field.Noise> inside = new ArrayList<>();
            collectCellular(context.caves(), false, inside, outside);
            assertFalse(inside.isEmpty());
            assertTrue(outside.toString(), outside.isEmpty());
            for (Field.Noise noise : inside) {
                assertTrue(noise.axes().contains(Axis.Y));
                assertEquals(CostClass.EXPENSIVE, noise.cost());
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void lowWeightCellularModulesUseSmoothSubstitutes() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile().setModules(new KList<>(List.of(
                CaveTestPacks.module(NoiseStyle.CELLULAR_BILINEAR, 0.05, 0.0, false, new IrisRange(0, 256)),
                CaveTestPacks.module(NoiseStyle.SIMPLEX_VASCULAR, 0.08, 0.1, true, new IrisRange(0, 256)),
                CaveTestPacks.module(NoiseStyle.VASCULAR, 0.2, 0.0, false, new IrisRange(0, 256)))));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-mixed"), null), Map.of("cave-mixed", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            FakeCaveStyles styles = FakeCaveStyles.volumetric();
            PackCompileContext context = deepContext(data, styles, List.of(new CaveTestPacks.CodeRange("cave-mixed", -1.0, 1.0)));

            new CaveStage().apply(context);

            List<NoiseStyle> requests = styles.requests();
            assertFalse(requests.toString(), requests.contains(NoiseStyle.CELLULAR_BILINEAR));
            assertFalse(requests.toString(), requests.contains(NoiseStyle.SIMPLEX_VASCULAR));
            assertTrue(requests.toString(), requests.contains(NoiseStyle.VASCULAR));
        } finally {
            data.close();
        }
    }

    @Test
    public void planarSmoothStylesLiftToVolume() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile()
                .setDensityThreshold(new IrisStyledRange(-0.1, 0.1, CaveTestPacks.style(NoiseStyle.SIMPLEX, 1.0)));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-smooth"), null), Map.of("cave-smooth", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.planar(), List.of(new CaveTestPacks.CodeRange("cave-smooth", -1.0, 1.0)));

            new CaveStage().apply(context);

            Field.Noise base = noiseContaining(context.caves(), "simplex_934447");
            Field.Noise threshold = noiseContaining(context.caves(), "simplex_489112");
            assertEquals(base.xzScale(), base.yScale(), 0.0);
            assertTrue(base.yScale() != 0.0);
            assertEquals(0.0, threshold.yScale(), 0.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void planarCellularStylesExportAsSmoothNoise() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile().setBaseDensityStyle(CaveTestPacks.style(NoiseStyle.CELLULAR_IRIS_DOUBLE, 0.7));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-cells"), null), Map.of("cave-cells", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.planar(), List.of(new CaveTestPacks.CodeRange("cave-cells", -1.0, 1.0)));

            new CaveStage().apply(context);

            Field.Noise base = noiseContaining(context.caves(), "simplex_934447");
            assertEquals(base.xzScale(), base.yScale(), 0.0);
            assertTrue(context.diagnostics().stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.NOISE_STYLE
                    && diagnostic.resource().equals("biomes/cave-cells")));
        } finally {
            data.close();
        }
    }

    @Test
    public void caveOpenShareWithinThresholdPerBand() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile()
                .setVerticalRange(new IrisRange(8, 240))
                .setVerticalEdgeFade(24)
                .setVerticalEdgeFadeStrength(0.2)
                .setDetailWeight(0.15)
                .setDensityThreshold(new IrisStyledRange(-0.15, 0.02, CaveTestPacks.style(NoiseStyle.SIMPLEX, 1.0)))
                .setThresholdBias(0.08)
                .setSurfaceClearance(6)
                .setAllowSurfaceBreak(true)
                .setSurfaceBreakStyle(CaveTestPacks.style(NoiseStyle.SIMPLEX, 0.08))
                .setSurfaceBreakNoiseThreshold(0.4)
                .setSurfaceBreakDepth(18)
                .setSurfaceBreakThresholdBoost(0.2)
                .setModules(new KList<>(List.of(
                        CaveTestPacks.module(NoiseStyle.SIMPLEX, 0.08, 0.1, true, new IrisRange(20, 120)),
                        CaveTestPacks.module(NoiseStyle.FRACTAL_FBM_SIMPLEX, 0.06, 0.0, false, new IrisRange(0, 256)))));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-real"), null), Map.of("cave-real", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            FakeCaveStyles styles = FakeCaveStyles.volumetric();
            PackCompileContext context = CaveTestPacks.context(data, styles);
            Field height = rollingSurface(context);
            context.setHeight(height);
            context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(new CaveTestPacks.CodeRange("cave-real", -1.0, 1.0))));

            new CaveStage().apply(context);

            IrisCaveReference reference = new IrisCaveReference(profile, styles, context.noises(), context.capabilities(), height);
            Field caves = context.caves();
            double referenceTotal = 0.0;
            int bands = 0;
            for (int bandMin = CaveTestPacks.MIN_Y; bandMin < CaveTestPacks.MAX_Y; bandMin += BAND_HEIGHT) {
                int compiledOpen = 0;
                int referenceOpen = 0;
                int samples = 0;
                for (int x = -300; x < 70; x += 37) {
                    for (int z = -250; z < 120; z += 37) {
                        for (int y = bandMin; y < bandMin + BAND_HEIGHT; y += 2) {
                            if (CaveTestPacks.sample(caves, context.noises(), x, y, z) <= 0.0) {
                                compiledOpen++;
                            }
                            if (reference.carved(x, y, z)) {
                                referenceOpen++;
                            }
                            samples++;
                        }
                    }
                }
                double compiledShare = compiledOpen / (double) samples;
                double referenceShare = referenceOpen / (double) samples;
                assertEquals("band " + bandMin, referenceShare, compiledShare, OPEN_SHARE_THRESHOLD);
                referenceTotal += referenceShare;
                bands++;
            }
            assertTrue("reference carves nothing", referenceTotal / bands > 0.05);
        } finally {
            data.close();
        }
    }

    @Test
    public void deepDarkBandIsMinUnioned() throws Exception {
        JsonObject dimension = CaveTestPacks.dimension(8);
        JsonArray carving = new JsonArray();
        carving.add(CaveTestPacks.band("deep-band", "cave-band", -40, -20));
        dimension.add("carving", carving);
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension,
                CaveTestPacks.region(List.of("cave-closed"), null),
                Map.of("cave-closed", CaveTestPacks.constantProfile(-5.0), "cave-band", CaveTestPacks.constantProfile(5.0)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(new CaveTestPacks.CodeRange("cave-closed", -1.0, 1.0)));

            new CaveStage().apply(context);

            Field caves = context.caves();
            Field union = ((Field.Grid) CaveTestPacks.caveField(caves)).expr();
            assertTrue(union instanceof Field.Binary binary && binary.op() == Field.BinaryOp.MIN);
            assertTrue(CaveTestPacks.sample(caves, context.noises(), 40, -30, 40) <= 0.0);
            assertTrue(CaveTestPacks.sample(caves, context.noises(), 40, 0, 40) > 0.0);
            assertTrue(CaveTestPacks.sample(caves, context.noises(), 40, -60, 40) > 0.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void expensiveSubtreesOnlySelectedThroughMuxOrGate() throws Exception {
        IrisCaveProfile lava = demandingProfile().setAllowFluid(true);
        JsonObject dimension = CaveTestPacks.dimension(20);
        JsonArray carving = new JsonArray();
        carving.add(CaveTestPacks.band("deep-band", "cave-band", -50, -20));
        dimension.add("carving", carving);
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension,
                CaveTestPacks.region(List.of("cave-lava", "cave-plain"), null),
                Map.of("cave-lava", lava, "cave-plain", CaveTestPacks.profile(), "cave-band", demandingProfile()));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.expensive());
            context.setHeight(warpedSurface(context));
            context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(
                    new CaveTestPacks.CodeRange("cave-lava", -1.0, 0.0),
                    new CaveTestPacks.CodeRange("cave-plain", 0.0, 1.0))));

            new CaveStage().apply(context);

            AquiferDef aquifer = context.aquifer();
            List<Field> roots = List.of(context.caves(), aquifer.barrier(), aquifer.floodedness(), aquifer.spread(), aquifer.lava(), aquifer.exclusion());
            boolean expensive = false;
            boolean muxed = false;
            for (Field root : roots) {
                for (Field node : CaveTestPacks.nodes(root)) {
                    expensive |= node.cost() == CostClass.EXPENSIVE;
                    muxed |= node instanceof Field.Mux;
                    for (Field child : CaveTestPacks.eagerChildren(node)) {
                        assertTrue(node.getClass().getSimpleName() + " evaluates an expensive child eagerly", child.cost() != CostClass.EXPENSIVE);
                    }
                }
            }
            assertTrue(expensive);
            assertTrue(muxed);
        } finally {
            data.close();
        }
    }

    @Test
    public void compiledCavesAreDeterministic() throws Exception {
        IrisCaveProfile profile = CaveTestPacks.profile()
                .setDensityThreshold(new IrisStyledRange(-0.15, 0.02, CaveTestPacks.style(NoiseStyle.SIMPLEX, 1.0)))
                .setAllowSurfaceBreak(true)
                .setAllowLava(true)
                .setModules(new KList<>(List.of(CaveTestPacks.module(NoiseStyle.SIMPLEX, 0.08, 0.1, true, new IrisRange(20, 120)))));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(20),
                CaveTestPacks.region(List.of("cave-one", "cave-two"), null),
                Map.of("cave-one", profile, "cave-two", CaveTestPacks.constantProfile(-0.2)));
        Compiled first = compileTwoCodes(pack);
        Compiled second = compileTwoCodes(pack);

        assertEquals(first.caves(), second.caves());
        assertEquals(first.aquifer(), second.aquifer());
        assertEquals(first.noises(), second.noises());
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    private Compiled compileTwoCodes(File pack) throws IOException {
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = deepContext(data, FakeCaveStyles.volumetric(), List.of(
                    new CaveTestPacks.CodeRange("cave-one", -1.0, 0.0),
                    new CaveTestPacks.CodeRange("cave-two", 0.0, 1.0)));
            new CaveStage().apply(context);
            return new Compiled(context.caves(), context.aquifer(), context.noises().definitions(), List.copyOf(context.diagnostics()));
        } finally {
            data.close();
        }
    }

    private static IrisCaveProfile demandingProfile() {
        return CaveTestPacks.profile()
                .setVerticalRange(new IrisRange(8, 240))
                .setVerticalEdgeFade(24)
                .setDetailWeight(0.15)
                .setDensityThreshold(new IrisStyledRange(-0.15, 0.02, CaveTestPacks.style(NoiseStyle.SIMPLEX, 1.0)))
                .setSurfaceClearance(6)
                .setAllowSurfaceBreak(true)
                .setAllowLava(true)
                .setModules(new KList<>(List.of(
                        CaveTestPacks.module(NoiseStyle.SIMPLEX, 0.08, 0.1, true, new IrisRange(20, 120)),
                        CaveTestPacks.module(NoiseStyle.CELLULAR, 0.3, 0.0, false, new IrisRange(0, 256)))));
    }

    private static PackCompileContext deepContext(IrisData data, FakeCaveStyles styles, List<CaveTestPacks.CodeRange> ranges) {
        PackCompileContext context = CaveTestPacks.context(data, styles);
        context.setHeight(DEEP_SURFACE);
        context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), ranges));
        return context;
    }

    private static Field rollingSurface(PackCompileContext context) {
        String id = context.noises().register(new NoiseDef("cave_test/surface", 0, List.of(1.0)));
        Field noise = new Field.Noise(id, 1.0 / 128.0, 0.0, null, null, null);
        return new Field.Curve(noise, List.of(
                new Field.Knot(-1.0, new Field.Const(50.0), 20.0),
                new Field.Knot(1.0, new Field.Const(90.0), 20.0)));
    }

    private static Field warpedSurface(PackCompileContext context) {
        String warp = context.noises().register(new NoiseDef("cave_test/surface_warp", 0, List.of(1.0)));
        String id = context.noises().register(new NoiseDef("cave_test/surface", 0, List.of(1.0)));
        Field shift = new Field.Noise(warp, 1.0 / 128.0, 0.0, null, null, null);
        Field noise = new Field.Shared("height", new Field.Noise(id, 1.0 / 128.0, 0.0, shift, null, shift));
        return new Field.Curve(noise, List.of(
                new Field.Knot(-1.0, new Field.Const(50.0), 20.0),
                new Field.Knot(1.0, new Field.Const(90.0), 20.0)));
    }

    private static ShellCount shellCount(Field field, PackCompileContext context, double band, int originX) {
        Map<String, NoiseDef> noises = CaveTestPacks.noises(context.noises());
        long solid = 0L;
        long walls = 0L;
        long shell = 0L;
        for (int box = 0; box < SHELL_BOXES; box++) {
            int x0 = originX + box * SHELL_BOX_STEP_X;
            int z0 = box * SHELL_BOX_STEP_Z - SHELL_BOX_STEP_Z;
            double[][][] values = new double[SHELL_BOX][SHELL_BOX_HEIGHT][SHELL_BOX];
            for (int x = 0; x < SHELL_BOX; x++) {
                for (int y = 0; y < SHELL_BOX_HEIGHT; y++) {
                    for (int z = 0; z < SHELL_BOX; z++) {
                        values[x][y][z] = FieldEvaluator.sample(field, noises, CaveTestPacks.SEED, DataVersion.V26_3, x0 + x, SHELL_BOX_Y + y, z0 + z);
                    }
                }
            }
            for (int x = 1; x < SHELL_BOX - 1; x++) {
                for (int y = 0; y < SHELL_BOX_HEIGHT; y++) {
                    for (int z = 1; z < SHELL_BOX - 1; z++) {
                        double value = values[x][y][z];
                        if (value < 0.0) {
                            continue;
                        }
                        solid++;
                        if (value < band) {
                            shell++;
                        }
                        if (values[x + 1][y][z] < 0.0 || values[x - 1][y][z] < 0.0 || values[x][y][z + 1] < 0.0 || values[x][y][z - 1] < 0.0) {
                            walls++;
                        }
                    }
                }
            }
        }
        return new ShellCount(solid, walls, shell);
    }

    private static Field.Noise noiseContaining(Field root, String fragment) {
        for (Field node : CaveTestPacks.nodes(root)) {
            if (node instanceof Field.Noise noise && noise.noiseId().contains(fragment)) {
                return noise;
            }
        }
        throw new AssertionError("No noise containing '" + fragment + "' in " + root);
    }

    private static void collectCellular(Field field, boolean insideGrid, List<Field.Noise> inside, List<Field.Noise> outside) {
        if (field instanceof Field.Noise noise && noise.noiseId().contains(FakeCaveStyles.CELLULAR_PREFIX)) {
            (insideGrid ? inside : outside).add(noise);
        }
        boolean childInside = insideGrid || field instanceof Field.Grid;
        for (Field child : CaveTestPacks.children(field)) {
            collectCellular(child, childInside, inside, outside);
        }
    }

    private record Compiled(Field caves, AquiferDef aquifer, List<NoiseDef> noises, List<ExportDiagnostic> diagnostics) {
    }

    private record ShellCount(long solid, long walls, long shell) {
        private double ratio() {
            return shell / (double) walls;
        }
    }
}
