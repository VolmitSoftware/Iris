package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RiverStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void stageCarvesRiversIntoTheTerrainHeight() throws Exception {
        IrisData data = IrisData.openRuntime(pack(true));
        try {
            DataVersion target = DataVersion.V26_3;
            PackCompileContext context = terrainContext(data, target);
            Field natural = context.height();

            new RiverStage().apply(context);

            Field.Shared carved = (Field.Shared) context.height();
            assertEquals(RiverCompiler.CARVED_HEIGHT, carved.name());
            assertSame(natural, ((Field.Binary) carved.expr()).left());
            assertSame(natural, context.naturalHeight());
            assertFalse(carved.axes().contains(Axis.Y));
            List<String> violations = TerrainTestSupport.unguardedExpensiveBranches(carved);
            assertTrue(target + " " + violations, violations.isEmpty());
            List<ExportLimit> limits = context.diagnostics().stream().map(ExportDiagnostic::limit).toList();
            assertTrue(limits.toString(), limits.contains(ExportLimit.RIVER_DRAINAGE));
            assertTrue(limits.toString(), limits.contains(ExportLimit.ELEVATED_WATER));
        } finally {
            data.close();
        }
    }

    @Test
    public void aquiferSurfaceCarvesTheClimateHeight() throws Exception {
        IrisData data = IrisData.openRuntime(pack(true));
        try {
            PackCompileContext context = terrainContext(data, DataVersion.V26_3);
            Field estimate = context.surfaceEstimate();
            assertSame(context.climateHeight(), estimate);
            assertEquals(ClimateHeightCompiler.CLIMATE_HEIGHT, ((Field.Shared) estimate).name());

            new RiverStage().apply(context);

            assertSame(estimate, context.climateHeight());

            Field.Shared surface = (Field.Shared) context.surfaceEstimate();
            assertEquals(RiverCompiler.CARVED_HEIGHT + "/estimate", surface.name());
            assertSame(estimate, ((Field.Binary) surface.expr()).left());
            assertTrue(TerrainTestSupport.contains(surface, estimate));
            assertFalse(TerrainTestSupport.contains(surface, context.naturalHeight()));
            for (Field node : TerrainTestSupport.nodes(surface)) {
                assertFalse("aquifer surface interpolates " + node, node instanceof Field.Grid grid && grid.cellXZ() != 4);
            }
            for (int x = -900; x <= 900; x += 97) {
                double carved = TerrainTestSupport.sample(context.height(), context.noises(), DataVersion.V26_3, x, 0, x / 2);
                double natural = TerrainTestSupport.sample(context.naturalHeight(), context.noises(), DataVersion.V26_3, x, 0, x / 2);
                double cut = TerrainTestSupport.sample(surface, context.noises(), DataVersion.V26_3, x, 0, x / 2)
                        - TerrainTestSupport.sample(estimate, context.noises(), DataVersion.V26_3, x, 0, x / 2);
                assertTrue("estimate cut " + cut, cut <= 1.0e-3);
                assertTrue("carved cut " + (carved - natural), carved - natural <= 1.0e-3);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void stageKeepsTheHeightWithoutHydrology() throws Exception {
        IrisData data = IrisData.openRuntime(pack(false));
        try {
            PackCompileContext context = terrainContext(data, DataVersion.V26_3);
            Field natural = context.height();
            int noises = context.noises().definitions().size();

            new RiverStage().apply(context);

            assertSame(natural, context.height());
            assertEquals(noises, context.noises().definitions().size());
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultCompilerRunsRiversAfterTerrain() throws Exception {
        IrisData data = IrisData.openRuntime(pack(true));
        try {
            List<String> stages = new ArrayList<>();
            WorldgenExportProgress progress = new WorldgenExportProgress() {
                @Override
                public void stage(String stage) {
                    stages.add(stage);
                }

                @Override
                public void resources(int done, int total) {
                }
            };

            PackCompiler.create().compile(context(data, DataVersion.V26_3), progress);

            assertEquals(stages.indexOf("terrain") + 1, stages.indexOf("rivers"));
        } finally {
            data.close();
        }
    }

    private File pack(boolean hydrology) throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 40), TerrainTestPacks.link("hills", 20, 90));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        String water = hydrology ? ",\"hydrology\":{\"enabled\":true,\"density\":0.5}" : "";
        CompatTestPlatform.write(pack, "dimensions/" + TerrainTestPacks.DIMENSION + ".json", "{\"regions\":[\"region\"]" + water + "}");
        return pack;
    }

    private static PackCompileContext terrainContext(IrisData data, DataVersion target) {
        PackCompileContext context = context(data, target);
        new DimensionStage().apply(context);
        context.setBiomeCodes(TerrainTestSupport.twoBiomeCodes(context.noises(), 0.0));
        new TerrainStage().apply(context);
        return context;
    }

    private static PackCompileContext context(IrisData data, DataVersion target) {
        return PackCompileContext.builder()
                .data(data)
                .dimension(data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false))
                .capabilities(ExportCapabilities.forTarget(target))
                .namespace(WorldgenExportRequest.DEFAULT_NAMESPACE)
                .mode(WorldgenExportMode.PRESET)
                .styles(TerrainTestSupport.noiseStyles())
                .build();
    }
}
