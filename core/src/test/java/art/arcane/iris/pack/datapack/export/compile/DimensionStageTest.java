package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DimensionStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void dimensionDefFollowsTheIrisDimension() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new DimensionStage().apply(context);

            DimensionDef dimension = context.dimensionDef();
            assertEquals("main", dimension.name());
            assertEquals(-64, dimension.minY());
            assertEquals(384, dimension.height());
            assertEquals(256, dimension.logicalHeight());
            assertEquals(63, dimension.fluidHeight());
            assertEquals(64, dimension.seaLevel());
            assertEquals("minecraft:stone", dimension.defaultBlock());
            assertEquals("minecraft:water", dimension.defaultFluid());
            JsonObject type = dimension.dimensionType();
            assertEquals(-64, type.get("min_y").getAsInt());
            assertEquals(384, type.get("height").getAsInt());
            assertEquals(256, type.get("logical_height").getAsInt());
            assertTrue(context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void unalignedHeightsExportAlignedWithDiagnostic() throws Exception {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), """
                {"regions": ["region"], "dimensionHeight": {"min": -60, "max": 250}, "logicalHeight": 310, "fluidHeight": 40}
                """);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new DimensionStage().apply(context);

            DimensionDef dimension = context.dimensionDef();
            assertEquals(-64, dimension.minY());
            assertEquals(320, dimension.height());
            assertEquals(320, dimension.logicalHeight());
            assertEquals(40, dimension.fluidHeight());
            assertEquals(41, dimension.seaLevel());
            assertEquals(-64, dimension.dimensionType().get("min_y").getAsInt());
            assertEquals(320, dimension.dimensionType().get("height").getAsInt());
            assertEquals(1, context.diagnostics().size());
            assertSame(ExportLimit.DIMENSION_HEIGHT, context.diagnostics().getFirst().limit());
        } finally {
            data.close();
        }
    }

    @Test
    public void paletteBlocksBecomeNamespacedIds() throws Exception {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), """
                {"regions": ["region"],
                 "rockPalette": {"palette": [{"block": "DEEPSLATE[axis=y]"}, {"block": "stone"}]},
                 "fluidPalette": {"palette": [{"block": "minecraft:lava"}]}}
                """);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new DimensionStage().apply(context);

            assertEquals("minecraft:deepslate", context.dimensionDef().defaultBlock());
            assertEquals("minecraft:lava", context.dimensionDef().defaultFluid());
        } finally {
            data.close();
        }
    }

    @Test
    public void readingTheDimensionDefBeforeTheStageFails() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            assertThrows(IllegalStateException.class, context::dimensionDef);
        } finally {
            data.close();
        }
    }
}
