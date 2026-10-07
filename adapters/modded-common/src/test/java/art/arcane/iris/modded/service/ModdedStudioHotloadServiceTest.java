package art.arcane.iris.modded.service;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedStudioHotloadServiceTest {
    @Test
    public void shutdownDrainsWorkerBeforeAllowingRestart() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        ExecutorService executor = mock(ExecutorService.class);
        setExecutor(service, executor);
        when(executor.awaitTermination(30L, TimeUnit.SECONDS)).thenReturn(false);
        when(executor.awaitTermination(5L, TimeUnit.SECONDS)).thenReturn(true);

        service.onDisable();

        InOrder order = inOrder(executor);
        order.verify(executor).shutdown();
        order.verify(executor).awaitTermination(30L, TimeUnit.SECONDS);
        order.verify(executor).shutdownNow();
        order.verify(executor).awaitTermination(5L, TimeUnit.SECONDS);
        service.onEnable();
        service.onDisable();
    }

    @Test
    public void undrainedWorkerPreventsRestart() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        ExecutorService executor = mock(ExecutorService.class);
        setExecutor(service, executor);
        when(executor.isShutdown()).thenReturn(true);
        try {
            service.onDisable();
            fail("Hotload shutdown must report a worker that did not terminate");
        } catch (IllegalStateException expected) {
            verify(executor).shutdownNow();
        }
        try {
            service.onEnable();
            fail("Hotload must not start another worker before the previous worker terminates");
        } catch (IllegalStateException expected) {
            assertFalse(executor.isTerminated());
        }
        when(executor.isTerminated()).thenReturn(true);
        service.onEnable();
        service.onDisable();
    }

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
    private static void setExecutor(ModdedStudioHotloadService service, ExecutorService executor) throws Exception {
        Field field = ModdedStudioHotloadService.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(service, executor);
    }

}
