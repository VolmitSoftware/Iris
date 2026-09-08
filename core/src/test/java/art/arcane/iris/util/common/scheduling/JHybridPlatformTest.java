package art.arcane.iris.util.common.scheduling;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JHybridPlatformTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void clearPlatform() {
        IrisPlatforms.unbind();
    }

    @After
    public void cleanup() {
        J.cancelTrackedRepeatingTasks();
        IrisPlatforms.unbind();
    }

    @Test
    public void boundBukkitIdentitySelectsBukkitScheduler() {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);

        assertTrue(J.usesBukkitScheduler());
    }

    @Test
    public void hybridHostRepeaterUsesBoundModdedScheduler() {
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(platform.platformName()).thenReturn("neoforge");
        when(platform.scheduler()).thenReturn(scheduler);
        IrisPlatforms.bind(platform);

        assertFalse(J.usesBukkitScheduler());
        int taskId = J.ar(() -> {
        }, 1);

        assertNotEquals(-1, taskId);
        verify(scheduler).laterGlobal(any(Runnable.class), eq(1));
    }
}
