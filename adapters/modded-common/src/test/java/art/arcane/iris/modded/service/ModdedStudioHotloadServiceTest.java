package art.arcane.iris.modded.service;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedStudioHotloadServiceTest {
    @Test
    public void detectsDatapackImportsAcrossLoadedDimensions() {
        IrisDimension empty = new IrisDimension();
        IrisDimension imported = new IrisDimension();
        imported.setDatapackImports(new KList<String>().qadd("https://modrinth.com/datapack/example"));

        assertTrue(ModdedStudioHotloadService.hasDatapackImports(List.of(empty, imported)));
    }

    @Test
    public void rejectsMissingOrEmptyDatapackImports() {
        IrisDimension empty = new IrisDimension();

        assertFalse(ModdedStudioHotloadService.hasDatapackImports(null));
        assertFalse(ModdedStudioHotloadService.hasDatapackImports(List.of(empty)));
    }

    @Test
    public void reportsTheCapturedServerThread() throws InterruptedException {
        ModdedScheduler scheduler = new ModdedScheduler();
        scheduler.reset();
        try {
            ModdedStudioHotloadService service = new ModdedStudioHotloadService();
            assertTrue(service.isMainThread());
            AtomicBoolean offThread = new AtomicBoolean(true);
            Thread worker = new Thread(() -> offThread.set(service.isMainThread()));
            worker.start();
            worker.join();
            assertFalse(offThread.get());
        } finally {
            scheduler.shutdown();
        }
    }
}
