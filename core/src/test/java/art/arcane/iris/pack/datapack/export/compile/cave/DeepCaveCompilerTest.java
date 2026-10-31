package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DeepCaveCompilerTest {
    private static final Field SURFACE = new Field.Const(150.0);
    private static final int LAVA_HEIGHT = 2;
    private static final int LAVA_Y = CaveTestPacks.MIN_Y + LAVA_HEIGHT;
    private static final int ZONE_Y = -58;
    private static final int ABOVE_LINE_Y = -40;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void deepCavesStaySolidInFinalDensityAndOpenThroughTheMaterialPass() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.constantProfile(5.0).setAllowLava(true), LAVA_HEIGHT, DataVersion.V26_3);

        Field opening = context.deepCaves().orElseThrow();
        assertTrue(sample(context, context.caves(), ZONE_Y) > 0.0);
        assertTrue(sample(context, context.caves(), DeepCaveCompiler.LAVA_LINE - 1) > 0.0);
        assertTrue(sample(context, context.caves(), DeepCaveCompiler.LAVA_LINE) <= 0.0);
        assertTrue(sample(context, context.caves(), ABOVE_LINE_Y) <= 0.0);
        assertTrue(sample(context, context.caves(), LAVA_Y) <= 0.0);
        assertEquals(1.0, sample(context, opening, ZONE_Y), 0.0);
        assertEquals(1.0, sample(context, opening, LAVA_Y + 1), 0.0);
        assertEquals(0.0, sample(context, opening, DeepCaveCompiler.LAVA_LINE), 0.0);
        assertEquals(0.0, sample(context, opening, ABOVE_LINE_Y), 0.0);
        assertEquals(0.0, sample(context, opening, LAVA_Y), 0.0);
        assertTrue(has(context, ExportLimit.DEEP_CAVE_LAYERS));
    }

    @Test
    public void solidDeepRockStaysClosed() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.constantProfile(-5.0).setAllowLava(true), LAVA_HEIGHT, DataVersion.V26_3);

        Field opening = context.deepCaves().orElseThrow();
        assertEquals(0.0, sample(context, opening, ZONE_Y), 0.0);
        assertTrue(sample(context, context.caves(), ZONE_Y) > 0.0);
    }

    @Test
    public void lavaFreeCavesOpenDownToTheFloor() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.constantProfile(5.0), LAVA_HEIGHT, DataVersion.V26_3);

        Field opening = context.deepCaves().orElseThrow();
        assertEquals(1.0, sample(context, opening, CaveTestPacks.MIN_Y), 0.0);
        assertTrue(sample(context, context.caves(), CaveTestPacks.MIN_Y) > 0.0);
    }

    @Test
    public void lavaAboveTheLineLeavesCavesUnchanged() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.constantProfile(5.0).setAllowLava(true), 20, DataVersion.V26_3);

        assertTrue(context.deepCaves().isEmpty());
        assertSame(context.caveField().orElseThrow(), context.caves());
        assertTrue(CaveTestPacks.caveField(context.caves()) instanceof Field.Grid);
        assertFalse(has(context, ExportLimit.DEEP_CAVE_LAYERS));
    }

    @Test
    public void expensiveCavesNeverSitUnderEagerNodes() throws Exception {
        PackCompileContext context = compile(CaveTestPacks.constantProfile(5.0).setAllowLava(true), LAVA_HEIGHT, DataVersion.V26_3);

        for (Field root : List.of(context.caves(), context.deepCaves().orElseThrow())) {
            for (Field node : CaveTestPacks.nodes(root)) {
                for (Field child : CaveTestPacks.eagerChildren(node)) {
                    assertTrue(node.getClass().getSimpleName() + " evaluates an expensive child eagerly", child.cost() != CostClass.EXPENSIVE);
                }
            }
        }
    }

    private PackCompileContext compile(IrisCaveProfile profile, int lavaHeight, DataVersion target) throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(lavaHeight),
                CaveTestPacks.region(List.of("cave-deep"), null), Map.of("cave-deep", profile));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.contextBuilder(data, target).styles(FakeCaveStyles.volumetric()).build();
            new DimensionStage().apply(context);
            context.setHeight(SURFACE);
            context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(new CaveTestPacks.CodeRange("cave-deep", -1.0, 1.0))));
            new CaveStage().apply(context);
            return context;
        } finally {
            data.close();
        }
    }

    private static double sample(PackCompileContext context, Field field, int y) {
        return CaveTestPacks.sample(field, context.noises(), 40, y, 40);
    }

    private static boolean has(PackCompileContext context, ExportLimit limit) {
        for (ExportDiagnostic diagnostic : context.diagnostics()) {
            if (diagnostic.limit() == limit) {
                return true;
            }
        }
        return false;
    }
}
