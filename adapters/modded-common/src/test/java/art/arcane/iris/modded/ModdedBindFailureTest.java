package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.mantle.MantleClosedException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * A level whose Iris generator cannot bind must crash its own creation, never fall through to another generator,
 * and every later chunk call must fail fast on the recorded failure instead of re-running the whole bind
 * (history verification included) on every worker thread.
 */
public class ModdedBindFailureTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void aRecordedBindFailureIsReportedOnceAndRethrownWithoutBindingAgain() throws Exception {
        IrisModdedChunkGenerator generator = generator("overworld:overworld");
        NativeWorld level = level(generator, "minecraft:overworld");
        field("boundLevel").set(generator, level);
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.generateStructures()).thenReturn(false);

        try (MockedStatic<NativeModdedServer> servers = mockStatic(NativeModdedServer.class);
             MockedStatic<ModdedWorldEngines> engines = mockStatic(ModdedWorldEngines.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            servers.when(() -> NativeModdedServer.forWorld(level)).thenReturn(server);
            engines.when(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any()))
                    .thenThrow(new IllegalStateException("broken world binding"));

            IllegalStateException recorded = assertThrows(IllegalStateException.class, () -> generator.bindLevel(level));
            IllegalStateException second = assertThrows(IllegalStateException.class, generator::engine);
            IllegalStateException third = assertThrows(IllegalStateException.class, () -> generator.bindLevel(level));

            assertSame(recorded, second.getCause());
            assertSame(recorded, third.getCause());
            engines.verify(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any()), times(1));
            log.verify(() -> ModdedIrisLog.error(argThat((String line) -> line != null && line.startsWith("Cause: ")
                    && line.contains("broken world binding"))), times(1));
        }
    }

    /**
     * A bind that fails because the runtime is stopping (level unload, quit to title, server stop) says nothing about
     * the world: it fails the chunk that asked, but the next bind tries again and no refusal is announced.
     */
    @Test
    public void aShutdownFailureDuringBindIsNeitherRememberedNorAnnouncedAsARefusal() throws Exception {
        IrisModdedChunkGenerator generator = generator("overworld:overworld");
        NativeWorld level = level(generator, "minecraft:overworld");
        field("boundLevel").set(generator, level);
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.generateStructures()).thenReturn(true);
        MantleClosedException closed = new MantleClosedException("Tectonic Plate is closed!");

        try (MockedStatic<NativeModdedServer> servers = mockStatic(NativeModdedServer.class);
             MockedStatic<ModdedWorldEngines> engines = mockStatic(ModdedWorldEngines.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            servers.when(() -> NativeModdedServer.forWorld(level)).thenReturn(server);
            engines.when(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any())).thenThrow(closed);

            assertSame(closed, assertThrows(MantleClosedException.class, generator::engine));
            assertSame(closed, assertThrows(MantleClosedException.class, generator::engine));

            engines.verify(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any()), times(2));
            log.verify(() -> ModdedIrisLog.error(anyString()), never());
        }
    }

    /**
     * Unloading a level resets its binding, so a refusal recorded in one load never carries into the next load of the
     * same generator (a runtime dimension removal that rolls back, or a level loaded again in the same JVM).
     */
    @Test
    public void aRecordedRefusalDoesNotOutliveTheUnloadOfItsLevel() throws Exception {
        IrisModdedChunkGenerator generator = generator("overworld:overworld");
        NativeWorld level = level(generator, "minecraft:overworld");
        doNothing().when((NativeModdedChunkGenerator<?, ?, ?>) field("nativeGenerator").get(generator)).clearCaches();
        field("importedFeatures").set(generator, mock(ModdedImportedFeatureStage.class));
        field("announced").set(generator, new AtomicBoolean());
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.generateStructures()).thenReturn(false);

        try (MockedStatic<NativeModdedServer> servers = mockStatic(NativeModdedServer.class);
             MockedStatic<ModdedWorldEngines> engines = mockStatic(ModdedWorldEngines.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            servers.when(() -> NativeModdedServer.forWorld(level)).thenReturn(server);
            engines.when(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any()))
                    .thenAnswer(invocation -> { throw new IllegalStateException("broken world binding"); });

            IllegalStateException first = assertThrows(IllegalStateException.class, () -> generator.bindLevel(level));
            generator.unbindEngine(level);
            IllegalStateException second = assertThrows(IllegalStateException.class, () -> generator.bindLevel(level));

            assertNotSame(first, second.getCause());
            engines.verify(() -> ModdedWorldEngines.get(eq(level), any(), any(), anyLong(), any()), times(2));
            engines.verify(() -> ModdedWorldEngines.evictOrThrow(level));
        }
    }

    @Test
    public void bindFailureNoticeNamesTheLevelTheCauseAndThatNothingGenerates() {
        String notice = String.join("\n", IrisModdedChunkGenerator.bindFailureNotice(
                "minecraft:overworld",
                "overworld:overworld",
                new IllegalStateException("Iris generation history is unusable for 'minecraft:overworld'",
                        new IOException("Historical generated registry definition changed"))));

        assertTrue(notice, notice.contains("minecraft:overworld"));
        assertTrue(notice, notice.contains("overworld:overworld"));
        assertTrue(notice, notice.contains("generation history is unusable"));
        assertTrue(notice, notice.contains("registry definition changed"));
        assertTrue(notice, notice.contains("no chunks"));
    }

    private static IrisModdedChunkGenerator generator(String dimensionKey) throws ReflectiveOperationException {
        IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class, CALLS_REAL_METHODS);
        field("nativeGenerator").set(generator, mock(NativeModdedChunkGenerator.class, CALLS_REAL_METHODS));
        field("engineBinding").set(generator, new ModdedEngineBinding<Engine>(1L, TimeUnit.SECONDS));
        field("dimensionKey").set(generator, dimensionKey);
        return generator;
    }

    private static NativeWorld level(IrisModdedChunkGenerator generator, String name) throws ReflectiveOperationException {
        NativeWorld level = mock(NativeWorld.class);
        ServerLevel handle = mock(ServerLevel.class);
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(level.nativeHandle()).thenReturn(handle);
        when(level.name()).thenReturn(name);
        when(handle.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getGenerator()).thenReturn((ChunkGenerator) field("nativeGenerator").get(generator));
        return level;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = IrisModdedChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
