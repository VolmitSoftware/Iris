package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeStage;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaveStageTest {
    private static final Field SURFACE = new Field.Const(120.0);

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void noVanillaCarversEmitted() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-open"), null), Map.of("cave-open", CaveTestPacks.constantProfile(0.1)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            CompileStage vanillaBiomes = new FixedStage("biomes", context -> {
                JsonObject body = JsonParser.parseString("{\"carvers\":[\"minecraft:cave\",\"minecraft:canyon\"],\"features\":[]}").getAsJsonObject();
                context.setBiomes(List.of(new BiomeDef(CaveTestPacks.SURFACE_BIOME, body), new BiomeDef("cave-open", body)));
                context.setHeight(SURFACE);
                context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(new CaveTestPacks.CodeRange("cave-open", -1.0, 1.0))));
            });
            PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(FakeCaveStyles.volumetric()).build();

            ExportIr ir = new PackCompiler(List.of(new DimensionStage(), vanillaBiomes, new CaveStage())).compile(context, WorldgenExportProgress.NONE);

            for (BiomeDef biome : ir.biomes()) {
                assertEquals(new JsonArray(), biome.json().get("carvers"));
            }
            for (JsonResource resource : ir.jsonResources()) {
                assertFalse(resource.path(), resource.path().contains("carver"));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void defaultPipelineRunsCavesAfterTerrain() throws Exception {
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

            PackCompiler.create().compile(ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(new ExportTestStyles()).build(), progress);

            assertTrue(stages.toString(), stages.indexOf("terrain") >= 0);
            assertTrue(stages.toString(), stages.indexOf("terrain") < stages.indexOf("caves"));
        } finally {
            data.close();
        }
    }

    @Test
    public void biomeStageCaveCodesSelectCaveProfiles() throws Exception {
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), CaveTestPacks.dimension(8),
                CaveTestPacks.region(List.of("cave-open"), null), Map.of("cave-open", CaveTestPacks.constantProfile(0.1)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.planar());
            context.setHeight(SURFACE);
            new BiomeStage().apply(context);

            new CaveStage().apply(context);

            assertNotEquals(new Field.Const(1.0e6), context.caves());
        } finally {
            data.close();
        }
    }

    @Test
    public void disabledCarvingKeepsDefaultSlots() throws Exception {
        JsonObject dimension = CaveTestPacks.dimension(20);
        dimension.addProperty("carvingEnabled", false);
        File pack = CaveTestPacks.pack(temporaryFolder.newFolder("pack"), dimension,
                CaveTestPacks.region(List.of("cave-open"), null), Map.of("cave-open", CaveTestPacks.constantProfile(5.0).setAllowLava(true)));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = CaveTestPacks.context(data, FakeCaveStyles.volumetric());
            context.setHeight(SURFACE);
            context.setBiomeCodes(CaveTestPacks.codes(CaveTestPacks.xGradient(), List.of(new CaveTestPacks.CodeRange("cave-open", -1.0, 1.0))));

            new CaveStage().apply(context);

            assertEquals(new Field.Const(1.0e6), context.caves());
            assertEquals(AquiferDef.dry(SURFACE), context.aquifer());
            assertTrue(context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }

    private record FixedStage(String name, Consumer<PackCompileContext> action) implements CompileStage {
        @Override
        public void apply(PackCompileContext context) {
            action.accept(context);
        }
    }
}
