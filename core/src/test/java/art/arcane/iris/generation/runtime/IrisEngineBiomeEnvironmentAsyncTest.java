package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisEngineBiomeEnvironmentAsyncTest {
    @Test
    public void uncachedBiomeWaitsAsynchronouslyAndUsesPreparedVolumeWithWorldHeight() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<SavedBiomeRuntime.ReadSession> pending = new CompletableFuture<>();
        SavedBiomeRuntime.ReadSession session = mock(SavedBiomeRuntime.ReadSession.class);
        BiomeEnvironment environment = environment(7L);
        when(fixture.biomes.readChunkAsync(-2, 2)).thenReturn(pending);
        when(session.resolve(-17, -59, 33, false)).thenReturn(Optional.of(environment));

        CompletableFuture<BiomeEnvironment> reading = fixture.engine.getBiomeEnvironmentAsync(-17, 5, 33);

        assertFalse(reading.isDone());
        verify(fixture.engine, never()).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
        pending.complete(session);
        assertSame(environment, reading.get(5L, TimeUnit.SECONDS));
        verify(session).resolve(-17, -59, 33, false);
        verify(fixture.biomes, never()).resolve(anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void permanentSavedBiomeFailureCompletesTheFutureExceptionally() throws Exception {
        Fixture fixture = new Fixture();
        SavedBiomeUnavailableException failure = new SavedBiomeUnavailableException("Saved pack is missing", false);
        when(fixture.biomes.readChunkAsync(0, 0)).thenReturn(CompletableFuture.failedFuture(failure));

        CompletableFuture<BiomeEnvironment> reading = fixture.engine.getBiomeEnvironmentAsync(0, 64, 0);

        assertSame(failure, assertThrows(CompletionException.class, reading::join).getCause());
        verify(fixture.engine, never()).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void preparedEmptyRecordFallsBackWithoutReenteringTheSavedQueryCache() throws Exception {
        Fixture fixture = new Fixture();
        SavedBiomeRuntime.ReadSession session = mock(SavedBiomeRuntime.ReadSession.class);
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        BiomeEnvironment expected = environment(0L);
        when(fixture.biomes.readChunkAsync(0, 0)).thenReturn(CompletableFuture.completedFuture(session));
        when(session.resolve(3, 7, 4, false)).thenReturn(Optional.empty());
        when(fixture.router.openReadScope(3, 4)).thenReturn(scope);
        when(fixture.engine.getBiome(3, 71, 4)).thenReturn(expected.biome());
        when(fixture.engine.getRegion(3, 71, 4)).thenReturn(expected.region());
        when(fixture.engine.getDimension()).thenReturn(expected.dimension());
        when(fixture.engine.getData()).thenReturn(expected.data());

        assertEquals(expected, fixture.engine.getBiomeEnvironmentAsync(3, 71, 4).get(5L, TimeUnit.SECONDS));

        verify(scope).close();
        verify(fixture.engine, never()).getBiomeEnvironment(anyInt(), anyInt(), anyInt());
        verify(fixture.biomes, never()).resolve(anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void scopedLookupUsesCurrentRuntimeAndDoesNotReadSavedBiomes() throws Exception {
        Fixture fixture = new Fixture();
        BiomeEnvironment expected = environment(4L);
        when(fixture.engine.hasGenerationRuntimeScope()).thenReturn(true);
        when(fixture.engine.getBiomeEnvironment(1, 2, 3)).thenReturn(expected);

        CompletableFuture<BiomeEnvironment> reading = fixture.engine.getBiomeEnvironmentAsync(1, 2, 3);

        assertTrue(reading.isDone());
        assertSame(expected, reading.join());
        verifyNoInteractions(fixture.biomes);
    }

    @Test
    public void immediateLookupFailureIsReturnedInTheFuture() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("Generation closed");
        when(fixture.engine.hasGenerationRuntimeScope()).thenReturn(true);
        when(fixture.engine.getBiomeEnvironment(1, 2, 3)).thenThrow(failure);

        CompletableFuture<BiomeEnvironment> reading = fixture.engine.getBiomeEnvironmentAsync(1, 2, 3);

        assertSame(failure, assertThrows(CompletionException.class, reading::join).getCause());
        verifyNoInteractions(fixture.biomes);
    }

    private static BiomeEnvironment environment(long activationId) {
        return new BiomeEnvironment(activationId, mock(IrisBiome.class), mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
    }

    private static final class Fixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        private final SavedBiomeRuntime biomes = mock(SavedBiomeRuntime.class);

        private Fixture() throws Exception {
            Field field = IrisEngine.class.getDeclaredField("generationHistoryRuntimeRouter");
            field.setAccessible(true);
            field.set(engine, router);
            when(router.biomes()).thenReturn(biomes);
            when(engine.getWorld()).thenReturn(IrisWorld.builder().name("test").minHeight(-64).maxHeight(320).build());
            doCallRealMethod().when(engine).getBiomeEnvironmentAsync(anyInt(), anyInt(), anyInt());
        }
    }
}
