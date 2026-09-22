package art.arcane.iris.platform.generation;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.studio.StudioMode;
import art.arcane.iris.world.IrisWorld;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorEntryMulticoreTest {
    @Test
    public void ordinaryEntryUsesMulticoreUntilEntryCompletes() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.generate();
            fixture.generator.completeInitialEntry();
            fixture.generate();

            assertEquals(List.of(true, false), fixture.multicore);
        }
    }

    @Test
    public void forceMulticoreStillAppliesAfterOrdinaryEntry() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.generator.completeInitialEntry();
            fixture.settings.getPerformance().getEngineSVC().setForceMulticoreWrite(true);
            fixture.generate();
            fixture.settings.getPerformance().getEngineSVC().setForceMulticoreWrite(false);
            fixture.generate();

            assertEquals(List.of(true, false), fixture.multicore);
        }
    }

    @Test
    public void pregenerationStillUsesMulticoreAfterOrdinaryEntry() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.generator.completeInitialEntry();
            when(fixture.hooks.isPregeneratorActive(fixture.engine)).thenReturn(true);
            fixture.generate();
            when(fixture.hooks.isPregeneratorActive(fixture.engine)).thenReturn(false);
            fixture.generate();

            assertEquals(List.of(true, false), fixture.multicore);
        }
    }

    @Test
    public void studioBootstrapUsesMulticoreUntilItsOwnLifecycleEnds() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.generate();
            fixture.generator.completeInitialEntry();
            fixture.generate();
            fixture.generator.endStudioEntryBootstrap();
            fixture.generate();

            assertEquals(List.of(true, true, false), fixture.multicore);
        }
    }

    private static void field(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class);
        private final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        private final IrisSettings settings = new IrisSettings();
        private final WorldInfo world = mock(WorldInfo.class);
        private final ChunkGenerator.ChunkData chunk = mock(ChunkGenerator.ChunkData.class);
        private final ArrayList<Boolean> multicore = new ArrayList<>();
        private final MockedStatic<IrisSettings> settingsAccess = mockStatic(IrisSettings.class);
        private final MockedStatic<INMS> nativeAccess = mockStatic(INMS.class);

        private Fixture(boolean studio) throws Exception {
            settingsAccess.when(IrisSettings::get).thenReturn(settings);
            INMSBinding binding = mock(INMSBinding.class);
            nativeAccess.when(INMS::get).thenReturn(binding);
            when(binding.applyChunkDataBlocks(any(), any())).thenReturn(true);
            when(chunk.getMaxHeight()).thenReturn(16);
            when(engine.getPlatformHooks()).thenReturn(hooks);
            when(engine.acquireGenerationLease("bukkit_terrain_stage")).thenReturn(GenerationSessionLease.noop());
            doCallRealMethod().when(engine).shouldGenerateMulticore();
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.getLoadKey()).thenReturn("entry-multicore-test");
            when(dimension.getStudioMode()).thenReturn(StudioMode.NORMAL);
            when(engine.getDimension()).thenReturn(dimension);
            EngineTarget target = mock(EngineTarget.class);
            when(target.getDimension()).thenReturn(dimension);
            doReturn(target).when(generator).getTarget();
            field(generator, "world", mock(IrisWorld.class));
            field(generator, "validatedDimension", dimension);
            field(generator, "validatedWorldContract", new IrisDimensionRuntimeContract("iris:test", 0, 16, 16));
            field(generator, "startupReady", CompletableFuture.completedFuture(null));
            field(generator, "startupContentReady", CompletableFuture.completedFuture(null));
            field(generator, "setup", new AtomicBoolean(true));
            field(generator, "studio", studio);
            field(generator, "studioEntryBootstrapActive", new AtomicBoolean(studio));
            generator.setLastMode(StudioMode.NORMAL);
            if (!studio) {
                generator.beginInitialEntry(false);
            }
            generator.setEngine(engine);
            doAnswer(invocation -> {
                assertSame(engine, IrisContext.require().getEngine());
                multicore.add(invocation.getArgument(4, Boolean.class));
                return null;
            }).when(engine).generate(anyInt(), anyInt(), any(), any(), anyBoolean());
        }

        private void generate() {
            generator.generateNoise(world, new Random(1L), -2, 3, chunk);
            assertNull(IrisContext.get());
        }

        @Override
        public void close() {
            nativeAccess.close();
            settingsAccess.close();
        }
    }
}
