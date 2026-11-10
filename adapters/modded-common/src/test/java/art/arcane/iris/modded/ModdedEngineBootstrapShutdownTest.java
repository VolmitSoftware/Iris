package art.arcane.iris.modded;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.util.common.parallel.MultiBurst;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class ModdedEngineBootstrapShutdownTest {
    private static final long WORKER_TIMEOUT_SECONDS = 10L;
    private static final Callable<Thread> RUNNING_THREAD = Thread::currentThread;

    private IrisSettings previousSettings;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
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
    public void serverStoppingKeepsTheGenerationPoolsOpenUntilServerStopped() throws Exception {
        ModdedEngineBootstrap.stop();

        assertNotSame(Thread.currentThread(), MultiBurst.burst.submit(RUNNING_THREAD).get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        ModdedEngineBootstrap.stopped();

        assertSame(Thread.currentThread(), MultiBurst.burst.submit(RUNNING_THREAD).get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void nextServerStartClosesWhatAServerThatNeverReachedStoppedLeftOpen() throws Exception {
        ModdedEngineBootstrap.stop();

        try (MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class)) {
            ModdedEngineBootstrap.serverAboutToStart(mock(MinecraftServer.class));
        }

        assertSame(Thread.currentThread(), MultiBurst.burst.submit(RUNNING_THREAD).get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void serverStartLeavesTheGenerationPoolsAloneWhenTheServerStoppedNormally() throws Exception {
        ModdedEngineBootstrap.stop();
        ModdedEngineBootstrap.stopped();
        MultiBurst.burst.reopen();

        try (MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class)) {
            ModdedEngineBootstrap.serverAboutToStart(mock(MinecraftServer.class));
        }

        assertNotSame(Thread.currentThread(), MultiBurst.burst.submit(RUNNING_THREAD).get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static void reopenBurstPools() {
        MultiBurst.burst.reopen();
        MultiBurst.ioBurst.reopen();
    }
}
