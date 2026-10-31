package art.arcane.iris.pack.datapack.export;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldgenExportServicesTest {
    @Test
    public void serviceDefaultsToTheDatapackExporterAndLatestInstallWins() {
        WorldgenExportService fallback = WorldgenExportServices.get();
        assertTrue(fallback instanceof WorldgenDatapackExporter);
        assertSame(fallback, WorldgenExportServices.get());

        WorldgenExportService first = new FixedService("first");
        WorldgenExportService second = new FixedService("second");
        WorldgenExportServices.install(first);
        assertSame(first, WorldgenExportServices.get());

        WorldgenExportServices.install(second);
        assertSame(second, WorldgenExportServices.get());
    }

    @Test
    public void installRejectsMissingService() {
        assertThrows(NullPointerException.class, () -> WorldgenExportServices.install(null));
    }

    private record FixedService(String message) implements WorldgenExportService {
        @Override
        public WorldgenExportResult export(WorldgenExportRequest request, WorldgenExportProgress progress) {
            return WorldgenExportResult.rejected(Path.of(message), message, List.of());
        }
    }
}
