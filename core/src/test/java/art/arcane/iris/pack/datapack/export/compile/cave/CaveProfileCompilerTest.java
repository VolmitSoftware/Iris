package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CaveProfileCompilerTest {
    private static final double GAIN = 3.0;
    private static final double TOLERANCE = 1.0e-4;
    private static final int FLOOR = CaveTestPacks.MIN_Y + 8;
    private static final int CEILING = CaveTestPacks.MIN_Y + 240;
    private static final Field SLOPED_SURFACE = new Field.Coord(Axis.X, Field.Tiling.CLAMP, -200, 200, 60, 140);

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void gainScalesTheFieldWithoutMovingItsCaves() throws Exception {
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
                .setSurfaceBreakThresholdBoost(0.1)
                .setModules(new KList<>(List.of(CaveTestPacks.module(NoiseStyle.SIMPLEX, 0.08, 0.1, true, new IrisRange(20, 120)))));
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-gain"), null), Map.of("cave-gain", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.volumetric());
            context.setHeight(SLOPED_SURFACE);
            CaveInputs inputs = CaveInputs.of(context);
            CaveProfileCompiler compiler = new CaveProfileCompiler(context.styles(), inputs);
            CaveSource source = new CaveSource("biomes/cave-gain", profile, inputs.minY(), inputs.maxY());

            Field unit = compiler.compile(source, 1.0);
            Field scaled = compiler.compile(source, GAIN);

            int caves = 0;
            for (int x = -240; x <= 240; x += 40) {
                for (int y = CaveTestPacks.MIN_Y; y < CaveTestPacks.MAX_Y; y += 5) {
                    for (int z = -90; z <= 90; z += 60) {
                        double expected = CaveTestPacks.sample(unit, context.noises(), x, y, z);
                        double actual = CaveTestPacks.sample(scaled, context.noises(), x, y, z);
                        if (y < FLOOR || y > CEILING) {
                            assertEquals(x + "," + y + "," + z, CaveProfileCompiler.SOLID, expected, 0.0);
                            assertEquals(x + "," + y + "," + z, CaveProfileCompiler.SOLID, actual, 0.0);
                            continue;
                        }
                        assertEquals(x + "," + y + "," + z, GAIN * expected, actual, TOLERANCE * Math.max(1.0, Math.abs(actual)));
                        if (expected < 0.0) {
                            caves++;
                        }
                    }
                }
            }
            assertTrue(String.valueOf(caves), caves > 0);
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(source, 0.0));
        } finally {
            data.close();
        }
    }
}
