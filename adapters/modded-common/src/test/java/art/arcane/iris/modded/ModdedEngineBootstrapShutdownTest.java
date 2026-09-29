package art.arcane.iris.modded;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeServerSpawn;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedEngineBootstrapShutdownTest {
    private IrisSettings previousSettings;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Before
    public void openRuntime() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        reopenBurstPools();
    }

    @After
    public void restoreRuntime() {
        reopenBurstPools();
        IrisModdedCommands.openDownloadAdmission();
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void serverStoppingKeepsEnginesAndWorkersOpenUntilServerStopped() throws Exception {
        NativeWorld level = level();
        Engine engine = mock(Engine.class);
        ModdedWorldEngines.installReplacement(level, engine);
        Callable<Thread> worker = Thread::currentThread;

        ModdedEngineBootstrap.stop();

        verify(engine, never()).close();
        assertTrue(ModdedWorldEngines.activeEngines().contains(engine));
        assertNotSame(Thread.currentThread(), MultiBurst.burst.submit(worker).get(10L, TimeUnit.SECONDS));

        ModdedEngineBootstrap.stopped();

        verify(engine).close();
        assertFalse(ModdedWorldEngines.activeEngines().contains(engine));
        assertSame(Thread.currentThread(), MultiBurst.burst.submit(worker).get(10L, TimeUnit.SECONDS));
    }

    @Test
    public void nextServerStartClosesTheEnginesOfAServerThatNeverReachedStopped() {
        NativeWorld level = level();
        Engine engine = mock(Engine.class);
        ModdedWorldEngines.installReplacement(level, engine);
        ModdedEngineBootstrap.stop();

        try (MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedConstruction<NativeServerSpawn> spawn = mockConstruction(NativeServerSpawn.class)) {
            ModdedEngineBootstrap.serverAboutToStart(mock(NativeModdedServer.class));
        }

        verify(engine).close();
        assertFalse(ModdedWorldEngines.activeEngines().contains(engine));
    }

    private static NativeWorld level() {
        ServerLevel serverLevel = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(serverLevel.getChunkSource()).thenReturn(chunks);
        when(chunks.getGenerator()).thenReturn(mock(ChunkGenerator.class));
        NativeWorld level = mock(NativeWorld.class);
        when(level.nativeHandle()).thenReturn(serverLevel);
        when(level.name()).thenReturn("example:shutdown");
        return level;
    }

    private static void reopenBurstPools() {
        MultiBurst.burst.reopen();
        MultiBurst.hydrology.reopen();
        MultiBurst.ioBurst.reopen();
    }
}
