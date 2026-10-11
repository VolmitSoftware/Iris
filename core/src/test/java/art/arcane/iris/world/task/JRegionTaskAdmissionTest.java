package art.arcane.iris.world.task;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

public class JRegionTaskAdmissionTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void clearPlatform() {
        IrisPlatforms.unbind();
    }

    @After
    public void cleanup() {
        IrisPlatforms.unbind();
    }

    @Test
    public void missingPlatformRejectsRegionTasks() {
        World world = mock(World.class);
        AtomicBoolean ran = new AtomicBoolean();

        assertFalse(J.runRegion(world, 0, 0, () -> ran.set(true)));
        assertFalse(J.runRegion(world, 0, 0, () -> ran.set(true), 1));
        assertFalse(ran.get());
    }

    @Test
    public void disabledBukkitPluginRejectsRegionTasks() {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.platformName()).thenReturn("bukkit");
        IrisPlatforms.bind(platform);
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(false);
        World world = mock(World.class);
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<BukkitPlatform> bukkit = mockStatic(BukkitPlatform.class);
             MockedStatic<Bukkit> server = mockStatic(Bukkit.class)) {
            bukkit.when(BukkitPlatform::hasPlugin).thenReturn(true);
            bukkit.when(BukkitPlatform::plugin).thenReturn(plugin);

            assertFalse(J.runRegion(world, 0, 0, () -> ran.set(true)));
            assertFalse(J.runRegion(world, 0, 0, () -> ran.set(true), 1));
            CompletableFuture<Void> future = J.runRegionFuture(world, 0, 0, () -> ran.set(true));

            assertTrue(future.isCompletedExceptionally());
            assertFalse(ran.get());
        }
    }

    @Test
    public void ownedFoliaCleanupStillRunsWithoutAScheduler() {
        World world = mock(World.class);
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<J> scheduling = mockStatic(J.class, CALLS_REAL_METHODS)) {
            scheduling.when(J::isFolia).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);

            assertTrue(J.runRegion(world, 0, 0, () -> ran.set(true)));
            assertTrue(ran.get());
            ran.set(false);
            assertTrue(J.runRegion(world, 0, 0, () -> ran.set(true), 0));
            assertTrue(ran.get());
            ran.set(false);
            CompletableFuture<Void> future = J.runRegionFuture(world, 0, 0, () -> ran.set(true));
            assertFalse(future.isCompletedExceptionally());
            assertTrue(ran.get());
            ran.set(false);
            assertFalse(J.runRegion(world, 0, 0, () -> ran.set(true), 1));
            assertFalse(ran.get());
        }
    }
}
