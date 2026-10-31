package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ImageMapStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void eachAffectedResourceReportsOneDiagnostic() throws Exception {
        File pack = ExportTestPacks.withImageMaps(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new ImageMapStage().apply(context);

            List<String> resources = new ArrayList<>();
            for (ExportDiagnostic diagnostic : context.diagnostics()) {
                assertSame(ExportLimit.IMAGE_MAP, diagnostic.limit());
                assertSame(ExportDiagnostic.Severity.WARNING, diagnostic.severity());
                resources.add(diagnostic.resource());
            }
            assertEquals(List.of("dimensions/main", "biomes/mapped"), resources);
        } finally {
            data.close();
        }
    }

    @Test
    public void dimensionImageMapLayerIsDropped() throws Exception {
        File pack = ExportTestPacks.withImageMaps(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            assertEquals(1, context.dimension().getImageMaps().size());

            new ImageMapStage().apply(context);

            assertTrue(context.dimension().getImageMaps().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void packWithoutImageMapsReportsNothing() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = ExportTestPacks.context(data, DataVersion.V26_3);
            new ImageMapStage().apply(context);

            assertTrue(context.diagnostics().toString(), context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }
}
