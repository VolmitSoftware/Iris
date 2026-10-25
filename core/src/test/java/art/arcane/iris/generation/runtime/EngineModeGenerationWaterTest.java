package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EngineModeGenerationWaterTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisSettings previousSettings;

    @Before
    public void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test(timeout = 120_000L)
    public void serverThreadGenerationWithHydrologyFailsBeforePaintingAnything() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            fixture.hydrology.hold();
            fixture.mainThread.set(true);
            Painter painter = new Painter(fixture);
            Hunk<NativeBlockState> blocks = blocks();

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> painter.mode.generate(32, -16, blocks, biomes(), false, 7L));

            assertTrue(failure.getMessage(), failure.getMessage().contains("Chunk 2,-1 cannot be generated"));
            assertEquals(0, painter.painted.get());
            verifyNoInteractions(blocks);
        }
    }

    @Test(timeout = 120_000L)
    public void workerGenerationWithHydrologyWaitsForTheRastersAndPaints() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            Painter painter = new Painter(fixture);

            assertThrows(PaintedException.class, () -> painter.mode.generate(32, -16, blocks(), biomes(), false, 7L));

            assertEquals(1, painter.painted.get());
            for (int slot = 0; slot < 9; slot++) {
                assertTrue(!fixture.hydrology.runtime.rasterSnapshot(1 + slot % 3, -2 + slot / 3).provisional());
            }
        }
    }

    @Test(timeout = 120_000L)
    public void serverThreadGenerationWithHydrologyOffStillPaints() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            doReturn(fixture.complex(FakeHydrology.disabled())).when(fixture.engine).getComplex();
            fixture.mainThread.set(true);
            Painter painter = new Painter(fixture);

            assertThrows(PaintedException.class, () -> painter.mode.generate(32, -16, blocks(), biomes(), false, 7L));

            assertEquals(1, painter.painted.get());
        }
    }

    @Test(timeout = 120_000L)
    public void matterGenerationFailsClosedOnTheServerThreadAndWaitsForTheRastersOnWorkers() throws Exception {
        try (HydrologyFixture fixture = fixture()) {
            EngineMantle mantle = mock(EngineMantle.class);
            IrisEngine engine = matterEngine(fixture.complex, mantle);
            ChunkContext context = mock(ChunkContext.class);
            when(context.getComplex()).thenReturn(fixture.complex);
            fixture.hydrology.hold();
            fixture.mainThread.set(true);

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> engine.generateMatter(2, -1, false, context));

            assertTrue(failure.getMessage(), failure.getMessage().contains("Chunk 2,-1 cannot be generated"));
            verifyNoInteractions(mantle);
            fixture.hydrology.release();
            fixture.mainThread.set(false);
            engine.generateMatter(2, -1, false, context);
            verify(mantle).generateMatter(2, -1, false, context);
            for (int slot = 0; slot < 9; slot++) {
                assertTrue(!fixture.hydrology.runtime.rasterSnapshot(1 + slot % 3, -2 + slot / 3).provisional());
            }
        }
    }

    private static IrisEngine matterEngine(IrisComplex complex, EngineMantle mantle) throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        doNothing().when(engine).awaitGenerationCacheWarm();
        doReturn(mock(GenerationHistoryRuntimeRouter.CoordinateScope.class)).when(engine).openGenerationHistoryCoordinateScope(anyInt(), anyInt());
        doReturn(mock(GenerationSessionLease.class)).when(engine).acquireGenerationLease(anyString());
        doReturn(complex).when(engine).getComplex();
        doReturn(mantle).when(engine).getMantle();
        doCallRealMethod().when(engine).generateMatter(anyInt(), anyInt(), anyBoolean(), any());
        return engine;
    }

    private HydrologyFixture fixture() throws Exception {
        HydrologyFixture fixture = new HydrologyFixture(HydrologyFixture.pack(temporaryFolder.newFolder().toPath(), null, null));
        when(fixture.hooks.shouldDisableChunkContextCache(fixture.engine)).thenReturn(true);
        return fixture;
    }

    @SuppressWarnings("unchecked")
    private static Hunk<NativeBlockState> blocks() {
        return mock(Hunk.class);
    }

    @SuppressWarnings("unchecked")
    private static Hunk<NativeBiome> biomes() {
        return mock(Hunk.class);
    }

    private static final class Painter {
        private final AtomicInteger painted = new AtomicInteger();
        private final EngineMode mode = mock(EngineMode.class, CALLS_REAL_METHODS);

        private Painter(HydrologyFixture fixture) {
            EngineStage stage = (x, z, blocks, biomes, multicore, context) -> {
                painted.incrementAndGet();
                throw new PaintedException();
            };
            doReturn(fixture.engine).when(mode).getEngine();
            doReturn(new KList<>(stage)).when(mode).getTerrainStages();
            doReturn(new KList<EngineStage>()).when(mode).getStages();
        }
    }

    private static final class PaintedException extends RuntimeException {
    }
}
