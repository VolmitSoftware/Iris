package art.arcane.iris.world.pregen;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.hydrology.engine.HydrologyPregeneration;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.task.J;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class MedievalPregenMethodTest {
    private static final int CONCURRENT_PREPARES = 4;

    @Test
    public void hydrologyPrefetchStartsOnceTheEngineExists() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        ChunkGenerator generator = mock(ChunkGenerator.class, withSettings().extraInterfaces(PlatformChunkGenerator.class));
        PlatformChunkGenerator platform = (PlatformChunkGenerator) generator;
        when(world.getGenerator()).thenReturn(generator);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        HydrologyPregeneration epoch = mock(HydrologyPregeneration.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.openPregeneration()).thenReturn(epoch);
        when(platform.getEngine()).thenReturn(null, engine, null);
        List<long[]> early = List.of(new long[]{0L, 0L});
        List<long[]> ready = List.of(new long[]{1L, 0L});
        IrisSettings previous = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        ExecutorService announcers = Executors.newFixedThreadPool(CONCURRENT_PREPARES);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(J::isFolia).thenReturn(true);
            MedievalPregenMethod method = new MedievalPregenMethod(world);
            method.onRegionBounds(-1, -1, 3, 1);
            method.onRegionsAhead(early);
            verifyNoInteractions(runtime);

            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> prepares = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_PREPARES; i++) {
                prepares.add(announcers.submit(() -> {
                    assertTrue(start.await(5L, TimeUnit.SECONDS));
                    method.onRegionsAhead(ready);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> prepare : prepares) {
                prepare.get(5L, TimeUnit.SECONDS);
            }

            verify(runtime, times(1)).openPregeneration();
            verify(epoch, times(1)).bound(-1, -1, 3, 1);
            verify(epoch, times(CONCURRENT_PREPARES)).prepare(ready);
            verify(epoch, times(CONCURRENT_PREPARES)).prepare(anyList());
            verify(epoch, never()).close();

            method.close();
            verify(epoch).close();
        } finally {
            IrisSettings.settings = previous;
            announcers.shutdownNow();
            assertTrue(announcers.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }
}
