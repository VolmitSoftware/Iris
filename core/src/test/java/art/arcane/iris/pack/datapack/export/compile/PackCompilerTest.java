package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PackCompilerTest {
    private static final Field Y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -64, 320, -64.5, 319.5);

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void stagesRunInOrderAndReportProgress() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            List<String> applied = new ArrayList<>();
            RecordingProgress progress = new RecordingProgress();
            PackCompiler compiler = new PackCompiler(List.of(new DimensionStage(), new NamedStage("first", applied), new NamedStage("second", applied)));

            compiler.compile(ExportTestPacks.context(data, DataVersion.V26_3), progress);

            assertEquals(List.of("first", "second"), applied);
            assertEquals(List.of("dimension", "first", "second", "router"), progress.stages);
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultStagesRunInPipelineOrder() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            RecordingProgress progress = new RecordingProgress();

            ExportIr ir = PackCompiler.create().compile(ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(new ExportTestStyles()).build(), progress);

            assertEquals(List.of("dimension", "image-maps", "biomes", "terrain", "rivers", "caves", "surface", "decoration",
                    "structures", "subterrain", "objects", "router"), progress.stages);
            assertEquals(3, ir.biomes().size());
            assertEquals(3, ir.climate().entries().size());
            assertFalse(ir.noises().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void materialDefaultsToRockWithoutSurfaceStage() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr ir = new PackCompiler(List.of(new DimensionStage())).compile(ExportTestPacks.context(data, DataVersion.V26_3), WorldgenExportProgress.NONE);

            assertEquals(MaterialProgram.rock("minecraft:stone"), ir.material());
        } finally {
            data.close();
        }
    }

    @Test
    public void unfilledSlotsUseTheDefaults() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            ExportIr ir = new PackCompiler(List.of(new DimensionStage(), new ImageMapStage()))
                    .compile(ExportTestPacks.context(data, DataVersion.V26_3), WorldgenExportProgress.NONE);

            Field height = new Field.Const(63.0);
            assertEquals("iris_export", ir.namespace());
            assertSame(DataVersion.V26_3, ir.target());
            assertSame(WorldgenExportMode.PRESET, ir.mode());
            assertTrue(ir.noises().isEmpty());
            assertEquals(finalDensity(height, new Field.Const(0.0), new Field.Const(1.0e6)), ir.router().finalDensity());
            assertEquals(new Field.Grid(height, 16, 1), ir.router().chunkSurfaceLevel());
            assertEquals(AquiferDef.dry(height), ir.router().aquifer());
            assertEquals(1, ir.biomes().size());
            BiomeDef biome = ir.biomes().getFirst();
            assertEquals("forest_land", biome.id());
            assertTrue(biome.json().toString(), biome.json().has("effects"));
            assertEquals(List.of(new ClimateLayout.ClimateEntry("iris_export:forest_land", ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0)),
                    ir.climate().entries());
            assertEquals(new Field.Const(0.0), ir.router().temperature());
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultBiomeCodesMapTheIdToItsLoadKey() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new DimensionStage().apply(context);

            assertEquals(Map.of("iris_export:forest_land", "Forest Land"), context.biomeCodes().loadKeyByBiomeId());
            assertSame(context.biomes(), context.biomes());
        } finally {
            data.close();
        }
    }

    @Test
    public void filledSlotsReplaceTheDefaults() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            Field height = new Field.Noise("iris_export:height", 1.0, 0.0, null, null, null);
            Field volumetric = new Field.Noise("iris_export:terrain3d", 1.0, 1.0, null, null, null);
            Field caves = new Field.Noise("iris_export:caves", 1.0, 1.0, null, null, null);
            CompileStage terrain = new CompileStage() {
                @Override
                public String name() {
                    return "terrain";
                }

                @Override
                public void apply(PackCompileContext context) {
                    context.setHeight(height);
                    context.setVolumetric(volumetric);
                    context.setCaves(caves);
                    context.setMaterial(MaterialProgram.rock("minecraft:deepslate"));
                }
            };

            ExportIr ir = new PackCompiler(List.of(new DimensionStage(), terrain))
                    .compile(ExportTestPacks.context(data, DataVersion.V26_3), WorldgenExportProgress.NONE);

            assertEquals(finalDensity(height, volumetric, caves), ir.router().finalDensity());
            assertEquals(AquiferDef.dry(height), ir.router().aquifer());
            assertEquals(MaterialProgram.rock("minecraft:deepslate"), ir.material());
        } finally {
            data.close();
        }
    }

    @Test
    public void heightSlotRejectsThreeDimensionalFields() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            Field volume = new Field.Noise("iris_export:terrain3d", 1.0, 1.0, null, null, null);

            assertThrows(IllegalStateException.class, () -> context.setHeight(volume));
        } finally {
            data.close();
        }
    }

    @Test
    public void compilerCopiesResourcesAndAppliesFeatures() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            JsonResource feature = new JsonResource("data/iris_export/worldgen/feature/tree.json", JsonParser.parseString("{\"type\":\"minecraft:tree\"}"));
            BinaryResource template = new BinaryResource("data/iris_export/structure/tree.nbt", new byte[]{10, 0, 0});
            CompileStage decoration = new CompileStage() {
                @Override
                public String name() {
                    return "decoration";
                }

                @Override
                public void apply(PackCompileContext context) {
                    context.jsonResources().add(feature);
                    context.binaryResources().add(template);
                    context.features().add("iris_export:forest_land", GenerationStep.VEGETAL_DECORATION, "iris_export:tree");
                }
            };

            ExportIr ir = new PackCompiler(List.of(new DimensionStage(), decoration))
                    .compile(ExportTestPacks.context(data, DataVersion.V26_3), WorldgenExportProgress.NONE);

            assertEquals(List.of(feature), ir.jsonResources());
            assertEquals(List.of(template), ir.binaryResources());
            JsonArray steps = ir.biomes().getFirst().json().getAsJsonArray("features");
            assertEquals(GenerationStep.values().length, steps.size());
            assertEquals(JsonParser.parseString("[\"iris_export:tree\"]"), steps.get(GenerationStep.VEGETAL_DECORATION.ordinal()));
            assertEquals(new JsonArray(), steps.get(GenerationStep.RAW_GENERATION.ordinal()));
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultStyleCompilerThrowsUntilInstalled() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            StyleCompiler styles = context.styles();

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> styles.compile(new IrisGeneratorStyle(), 0L, context.noises(), context.capabilities()));

            assertEquals("No style compiler installed", error.getMessage());
            assertTrue(context.objects());
        } finally {
            data.close();
        }
    }

    @Test
    public void builderInstallsStyleCompilerAndObjectsFlag() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            Field compiled = new Field.Const(2.0);
            StyleCompiler styles = (style, seedSalt, registry, capabilities) -> compiled;

            PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3)
                    .styles(styles)
                    .objects(false)
                    .build();

            assertSame(styles, context.styles());
            assertSame(compiled, context.styles().compile(new IrisGeneratorStyle(), 1L, context.noises(), context.capabilities()));
            assertFalse(context.objects());
        } finally {
            data.close();
        }
    }

    private static Field finalDensity(Field height, Field volumetric, Field caves) {
        Field ramp = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(1.0), new Field.Binary(Field.BinaryOp.SUB, height, Y));
        Field terrain = new Field.Binary(Field.BinaryOp.ADD, ramp, new Field.Grid(volumetric, 4, 8));
        return new Field.Binary(Field.BinaryOp.MIN, terrain, caves);
    }

    private record NamedStage(String name, List<String> applied) implements CompileStage {
        @Override
        public void apply(PackCompileContext context) {
            applied.add(name);
        }
    }

    private static final class RecordingProgress implements WorldgenExportProgress {
        private final List<String> stages = new ArrayList<>();

        @Override
        public void stage(String stage) {
            stages.add(stage);
        }

        @Override
        public void resources(int done, int total) {
        }
    }
}
