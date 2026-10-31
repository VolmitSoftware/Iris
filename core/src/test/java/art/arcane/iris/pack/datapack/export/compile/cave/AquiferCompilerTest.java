package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AquiferCompilerTest {
    private static final Field SURFACE = new Field.Const(120.0);
    private static final Field SEA_FLOOR_SLOPE = new Field.Coord(Axis.X, Field.Tiling.CLAMP, -1000, 1000, -100.0, 300.0);
    private static final Field HIGH_ESTIMATE = new Field.Const(120.0);
    private static final int SEA_X = -275;
    private static final int LAND_X = 400;
    private static final int DEEP_SEA_X = -800;
    private static final int SHORE_X = -165;
    private static final int HILL_X = -125;
    private static final int SEA_FLOOR = 45;
    private static final int FLUID_HEIGHT = 63;
    private static final double FULLY_FLOODED = 0.8;
    private static final double DRY = -1.0;
    private static final double PARTIAL = 0.6;
    private static final int FLUID_BAND_HEIGHT = 40;
    private static final int FLUID_BAND_CENTER = 20;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void dryProfilesHaveNegativeFloodedness() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1));

        AquiferDef aquifer = context.aquifer();
        AquiferDef vanilla = AquiferDef.dry(SURFACE);
        assertEquals(vanilla.barrier(), aquifer.barrier());
        assertEquals(vanilla.spread(), aquifer.spread());
        assertEquals(vanilla.lava(), aquifer.lava());
        assertEquals(vanilla.exclusion(), aquifer.exclusion());
        assertEquals(vanilla.surfaceLevel(), aquifer.surfaceLevel());
        for (int y = CaveTestPacks.MIN_Y; y < CaveTestPacks.MAX_Y; y += 8) {
            assertEquals(DRY, CaveTestPacks.sample(aquifer.floodedness(), context.noises(), 17, y, -9), 0.0);
        }
    }

    @Test
    public void openWaterAboveTheTerrainFloodsToSeaLevel() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SEA_X, SEA_FLOOR + 1, 4) > FULLY_FLOODED);
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SEA_X, FLUID_HEIGHT, 4) > FULLY_FLOODED);
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SEA_X, FLUID_HEIGHT + 6, 4) > FULLY_FLOODED);
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SEA_X, SEA_FLOOR - 4, 4) > FULLY_FLOODED);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), SEA_X, SEA_FLOOR - 12, 4), 0.0);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), LAND_X, FLUID_HEIGHT - 10, 4), 0.0);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), LAND_X, SEA_FLOOR + 1, 4), 0.0);
    }

    @Test
    public void openWaterLiftStaysNearSeaLevel() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), DEEP_SEA_X, FLUID_HEIGHT - 40, 4), 0.0);
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), DEEP_SEA_X, FLUID_HEIGHT - 20, 4) > FULLY_FLOODED);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), SEA_X, FLUID_HEIGHT + 12, 4), 0.0);
    }

    @Test
    public void openWaterLiftStopsAtTheShore() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SHORE_X, FLUID_HEIGHT, 4) > FULLY_FLOODED);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), HILL_X, FLUID_HEIGHT + 7, 4), 0.0);
    }

    @Test
    public void openWaterLiftSkipsColumnsWithAHighSurfaceEstimate() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1), SEA_FLOOR_SLOPE, HIGH_ESTIMATE);

        assertEquals(DRY, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), SEA_X, SEA_FLOOR + 1, 4), 0.0);
    }

    @Test
    public void wetCavesBelowTheTerrainKeepPartialFlooding() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1).setAllowFluid(true), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SEA_X, SEA_FLOOR + 6, 4) > FULLY_FLOODED);
        assertEquals(PARTIAL, CaveTestPacks.sample(floodedness, context.noises(), SEA_X, SEA_FLOOR - 24, 4), 1.0e-6);
        assertEquals(PARTIAL, CaveTestPacks.sample(floodedness, context.noises(), LAND_X, FLUID_HEIGHT - 10, 4), 1.0e-6);
    }

    @Test
    public void lavaAboveMinus10ReportsLimit() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(60), CaveTestPacks.constantProfile(-0.1).setAllowLava(true));

        List<ExportDiagnostic> limits = context.diagnostics().stream().filter(diagnostic -> diagnostic.limit() == ExportLimit.LAVA_HEIGHT).toList();
        assertEquals(1, limits.size());
        assertEquals("dimensions/" + ExportTestPacks.DIMENSION, limits.getFirst().resource());
        for (int y = CaveTestPacks.MIN_Y; y < 0; y += 4) {
            assertEquals(DRY, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), 3, y, 5), 0.0);
        }
    }

    @Test
    public void lavaFillsToProfileLavaHeight() throws Exception {
        int lavaY = CaveTestPacks.MIN_Y + 20;
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1).setAllowLava(true));

        AquiferDef aquifer = context.aquifer();
        assertFalse(context.diagnostics().stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.LAVA_HEIGHT));
        assertEquals(PARTIAL, CaveTestPacks.sample(aquifer.floodedness(), context.noises(), 4, lavaY - 6, 4), 1.0e-6);
        assertEquals(PARTIAL, CaveTestPacks.sample(aquifer.floodedness(), context.noises(), 4, lavaY + 12, 4), 1.0e-6);
        assertEquals(DRY, CaveTestPacks.sample(aquifer.floodedness(), context.noises(), 4, lavaY + 13, 4), 0.0);
        assertEquals(DRY, CaveTestPacks.sample(aquifer.floodedness(), context.noises(), 4, 40, 4), 0.0);
        int lavaBand = Math.floorDiv(lavaY + 12, FLUID_BAND_HEIGHT);
        for (int band = Math.floorDiv(CaveTestPacks.MIN_Y, FLUID_BAND_HEIGHT); band <= lavaBand; band++) {
            double spread = CaveTestPacks.sample(aquifer.spread(), context.noises(), 7, band, -2);
            int level = band * FLUID_BAND_HEIGHT + FLUID_BAND_CENTER + (int) Math.floor(spread * 10.0 / 3.0) * 3;
            assertTrue("band " + band + " level " + level, level <= lavaY && level >= lavaY - 2);
            assertTrue(Math.abs(CaveTestPacks.sample(aquifer.lava(), context.noises(), 7, band, -2)) > 0.3);
        }
        assertTrue(Math.abs(CaveTestPacks.sample(aquifer.lava(), context.noises(), 7, lavaBand + 1, -2)) <= 0.3);
    }

    @Test
    public void wetProfilesFloodBelowSeaLevel() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), CaveTestPacks.constantProfile(-0.1).setAllowFluid(true));

        assertEquals(PARTIAL, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), 4, 30, 4), 1.0e-6);
        assertEquals(DRY, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), 4, 100, 4), 0.0);
        assertEquals(0.0, CaveTestPacks.sample(context.aquifer().lava(), context.noises(), 4, 0, 4), 0.0);
    }

    @Test
    public void bandProfileDecidesFloodednessInsideItsBand() throws Exception {
        JsonObject dimension = CaveTestPacks.dimension(20);
        JsonArray carving = new JsonArray();
        carving.add(CaveTestPacks.band("deep-band", "cave-band", -60, -30));
        dimension.add("carving", carving);
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension, CaveTestPacks.region(List.of("cave-dry"), null),
                Map.of("cave-dry", CaveTestPacks.constantProfile(-0.1), "cave-band", CaveTestPacks.constantProfile(-0.1).setAllowLava(true)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data, "cave-dry");

            new CaveStage().apply(context);

            assertEquals(PARTIAL, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), 4, -50, 4), 1.0e-6);
            assertEquals(DRY, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), 4, -62, 4), 0.0);
        } finally {
            data.close();
        }
    }

    private PackCompileContext compile(JsonObject dimension, IrisCaveProfile profile) throws Exception {
        return compile(dimension, profile, SURFACE, SURFACE);
    }

    private PackCompileContext compile(JsonObject dimension, IrisCaveProfile profile, Field height, Field estimate) throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension, CaveTestPacks.region(List.of("cave-only"), null),
                Map.of("cave-only", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data, "cave-only");
            context.setHeight(height);
            context.setSurfaceEstimate(estimate);
            new CaveStage().apply(context);
            return context;
        } finally {
            data.close();
        }
    }

    private static PackCompileContext context(IrisData data, String caveBiome) {
        PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.volumetric());
        context.setHeight(SURFACE);
        context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(new CaveTestPacks.CodeRange(caveBiome, -1.0, 1.0))));
        return context;
    }
}
