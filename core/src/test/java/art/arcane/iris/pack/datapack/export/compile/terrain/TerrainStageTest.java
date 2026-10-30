package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeStage;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
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
import static org.junit.Assert.assertTrue;

public class TerrainStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void stageFillsTheHeightAndVolumetricSlots() throws Exception {
        IrisData data = IrisData.openRuntime(featurePack());
        try {
            PackCompileContext context = context(data, DataVersion.V26_3);
            new DimensionStage().apply(context);
            context.setBiomeCodes(TerrainTestSupport.twoBiomeCodes(context.noises(), 0.0));

            new TerrainStage().apply(context);

            assertEquals(HeightCompiler.HEIGHT, ((Field.Shared) context.height()).name());
            assertFalse(context.height().axes().contains(Axis.Y));
            assertTrue(context.volumetric().axes().contains(Axis.Y));
            assertFalse(context.noises().definitions().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void expensiveChildrenOnlyBehindMuxOrGate() throws Exception {
        IrisData data = IrisData.openRuntime(featurePack());
        try {
            DataVersion target = DataVersion.V26_3;
            PackCompileContext context = context(data, target);
            new DimensionStage().apply(context);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(context.noises(), 0.0);
            context.setBiomeCodes(codes);

            new TerrainStage().apply(context);

            List<String> violations = new ArrayList<>(TerrainTestSupport.unguardedExpensiveBranches(context.height()));
            violations.addAll(TerrainTestSupport.unguardedExpensiveBranches(context.volumetric()));
            assertTrue(target + " " + violations, violations.isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultCompilerRunsTerrainBetweenBiomesAndRivers() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
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

            ExportIr ir = PackCompiler.create().compile(ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(new ExportTestStyles()).build(), progress);

            assertEquals(stages.toString(), stages.indexOf("biomes") + 1, stages.indexOf("terrain"));
            assertEquals(stages.toString(), stages.indexOf("terrain") + 1, stages.indexOf("rivers"));
            assertEquals(stages.toString(), stages.indexOf("rivers") + 1, stages.indexOf("caves"));
            assertEquals(new Field.Grid(new Field.Shared(HeightCompiler.HEIGHT, new Field.Const(63.0)), 16, 1), ir.router().chunkSurfaceLevel());
        } finally {
            data.close();
        }
    }

    @Test
    public void terrainRunsOnBaseCodesAndBiomeCodesRebindToTheClimateHeight() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(new ExportTestStyles()).build();
            new DimensionStage().apply(context);
            new BiomeStage().apply(context);
            BiomeCodes base = context.biomeCodes();

            new TerrainStage().apply(context);

            BiomeCodes bound = context.biomeCodes();
            assertEquals(base, context.baseBiomeCodes());
            assertEquals(ClimateHeightCompiler.CLIMATE_HEIGHT, ((Field.Shared) context.climateHeight()).name());
            assertTrue(TerrainTestSupport.contains(bound.surfaceCode(), context.climateHeight()));
            assertFalse(TerrainTestSupport.contains(bound.surfaceCode(), context.height()));
            assertFalse(TerrainTestSupport.contains(BandTreeCompiler.selector(base), context.height()));
        } finally {
            data.close();
        }
    }

    @Test
    public void stageCompileIsDeterministic() throws Exception {
        IrisData data = IrisData.openRuntime(featurePack());
        try {
            List<Field> heights = new ArrayList<>();
            List<Field> volumes = new ArrayList<>();
            List<Object> noises = new ArrayList<>();
            for (int run = 0; run < 2; run++) {
                PackCompileContext context = context(data, DataVersion.V26_3);
                new DimensionStage().apply(context);
                context.setBiomeCodes(TerrainTestSupport.twoBiomeCodes(context.noises(), 0.0));
                CompileStage stage = new TerrainStage();
                stage.apply(context);
                heights.add(context.height());
                volumes.add(context.volumetric());
                noises.add(context.noises().definitions());
            }

            assertEquals(heights.getFirst(), heights.getLast());
            assertEquals(volumes.getFirst(), volumes.getLast());
            assertEquals(noises.getFirst(), noises.getLast());
        } finally {
            data.close();
        }
    }

    private File featurePack() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"), "", "");
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        TerrainTestPacks.generator(pack, "ranges", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_12", 200));
        TerrainTestPacks.generator(pack, "terraces", """
                {"interpolator":{"function":"BILINEAR_STARCAST_9","horizontalScale":26},"cliffHeightMin":20,"cliffHeightMax":80,
                 "surfaceDetail":0.5,"composite":[{"style":{"style":"SIMPLEX"},"seed":3,"bezier":true},{"style":{"style":"PERLIN"},"seed":4,"exponent":1.5}]}""");
        TerrainTestPacks.biome(pack, "a", "{\"name\":\"A\",\"generators\":[" + TerrainTestPacks.link("hills", 20, 140) + ","
                + TerrainTestPacks.link("ranges", 0, 60) + "],\"terrain3D\":" + TerrainTestPacks.TERRAIN3D + "}");
        TerrainTestPacks.biome(pack, "b", "{\"name\":\"B\",\"generators\":[" + TerrainTestPacks.link("terraces", 5, 70) + "],"
                + "\"terrain3D\":{\"amplitude\":12,\"horizontalScale\":32,\"verticalScale\":12},"
                + "\"floatingChildBiomes\":[{\"biome\":\"a\",\"topShapeMode\":\"BIOME\"},{\"topShapeMode\":\"NOISE\",\"rarity\":3}]}");
        return pack;
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
