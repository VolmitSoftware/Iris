package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void rockAwayFromOpenWaterIsDryAndTheRestIsVanilla() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), SURFACE, SURFACE);

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
    public void listedCavesDoNotChangeTheAquifer() throws Exception {
        JsonObject withCaves = CaveTestPacks.listing(CaveTestPacks.dimension(20), List.of("tunnels"));

        PackCompileContext context = compile(withCaves, SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);
        PackCompileContext without = compile(CaveTestPacks.dimension(20), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        assertEquals(without.aquifer(), context.aquifer());
    }

    @Test
    public void openWaterAboveTheTerrainFloodsToSeaLevel() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

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
        PackCompileContext context = compile(CaveTestPacks.dimension(20), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), DEEP_SEA_X, FLUID_HEIGHT - 40, 4), 0.0);
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), DEEP_SEA_X, FLUID_HEIGHT - 20, 4) > FULLY_FLOODED);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), SEA_X, FLUID_HEIGHT + 12, 4), 0.0);
    }

    @Test
    public void openWaterLiftStopsAtTheShore() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), SEA_FLOOR_SLOPE, SEA_FLOOR_SLOPE);

        Field floodedness = context.aquifer().floodedness();
        assertTrue(CaveTestPacks.sample(floodedness, context.noises(), SHORE_X, FLUID_HEIGHT, 4) > FULLY_FLOODED);
        assertEquals(DRY, CaveTestPacks.sample(floodedness, context.noises(), HILL_X, FLUID_HEIGHT + 7, 4), 0.0);
    }

    @Test
    public void openWaterLiftSkipsColumnsWithAHighSurfaceEstimate() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.dimension(20), SEA_FLOOR_SLOPE, HIGH_ESTIMATE);

        assertEquals(DRY, CaveTestPacks.sample(context.aquifer().floodedness(), context.noises(), SEA_X, SEA_FLOOR + 1, 4), 0.0);
    }

    private PackCompileContext compile(JsonObject dimension, Field height, Field estimate) throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder(), dimension, CaveTestPacks.region(), Map.of("tunnels", CaveTestPacks.LISTED_CAVE));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.volumetric());
            context.setHeight(height);
            context.setSurfaceEstimate(estimate);
            new CaveStage().apply(context);
            return context;
        } finally {
            data.close();
        }
    }
}
