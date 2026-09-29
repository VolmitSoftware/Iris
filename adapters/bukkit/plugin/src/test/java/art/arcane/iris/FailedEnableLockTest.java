package art.arcane.iris;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.IrisStartupAdmissionListener;
import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.world.IrisWorldGeneratorResolver;
import art.arcane.iris.world.PendingWorldReplacementManager;
import art.arcane.iris.world.task.J;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Paper disables a plugin whose onEnable throws, and CraftServer then builds every world bukkit.yml points at a
 * disabled generator plugin with the vanilla generator. A failed enable on a server with Iris worlds therefore has
 * to leave Iris enabled and locked instead.
 */
public class FailedEnableLockTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void disableValidation() {
        IrisStartupValidation.disable();
        IrisPlatforms.unbind();
    }

    @Test
    public void enableFailureWithIrisWorldsLocksTheRuntimeInsteadOfDisablingIris() throws Exception {
        IrisStartupValidation.begin();
        IllegalStateException failure = new IllegalStateException("service graph exploded");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            assertTrue(FailedEnableLock.engage(failure, true, false));

            String denial = IrisStartupValidation.denialReason().orElseThrow();
            assertTrue(denial, denial.contains("service graph exploded"));

            ChunkGenerator generator = new IrisWorldGeneratorResolver(null)
                    .resolveDefaultWorldGenerator("world", "overworld");
            assertNotNull("a null generator is the vanilla generator", generator);
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> generator.getDefaultBiomeProvider(mock(WorldInfo.class)));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("service graph exploded"));
            bukkit.verify(Bukkit::shutdown, never());
        }

        AsyncPlayerPreLoginEvent login = new AsyncPlayerPreLoginEvent(
                "LockedTest", InetAddress.getLoopbackAddress(), UUID.randomUUID(), false, mock(PlayerProfile.class));
        new IrisStartupAdmissionListener().onAsyncPlayerPreLogin(login);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, login.getLoginResult());
    }

    @Test
    public void enableFailureWithoutIrisWorldsLeavesIrisToBeDisabled() {
        IrisStartupValidation.begin();

        try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            assertFalse(FailedEnableLock.engage(new IllegalStateException("service graph exploded"), false, false));
        }

        String denial = IrisStartupValidation.denialReason().orElseThrow();
        assertFalse(denial, denial.contains("service graph exploded"));
    }

    @Test
    public void noticeSaysIrisStaysLockedAndWhatThatBlocks() {
        String notice = String.join("\n", FailedEnableLock.notice(
                new IllegalStateException("outer", new IllegalArgumentException("inner")), false));

        assertTrue(notice, notice.contains("Iris failed to enable"));
        assertTrue(notice, notice.contains("outer"));
        assertTrue(notice, notice.contains("inner"));
        assertTrue(notice, notice.contains("vanilla"));
        assertTrue(notice, notice.contains("startup stops"));
        assertTrue(notice, notice.contains("logins"));
    }

    /**
     * A hotload enables Iris on a running server whose startup worlds already exist, so nothing stops.
     */
    @Test
    public void noticeAfterStartupWorldsExistSaysTheServerKeepsRunning() {
        String notice = String.join("\n", FailedEnableLock.notice(new IllegalStateException("outer"), true));

        assertFalse(notice, notice.contains("startup stops"));
        assertTrue(notice, notice.contains("keeps running"));
    }

    /**
     * IrisPlatforms.bind refuses a second platform, which is a real way for enable to throw. It has to throw inside
     * the guarded region, and the throw must not reach Paper.
     */
    @Test
    public void onEnableKeepsIrisEnabledAndLockedWhenEnableThrowsOnAServerWithIrisWorlds() throws Exception {
        IrisPlatforms.bind(mock(IrisPlatform.class));
        Iris plugin = plugin();

        try (EnableFixture fixture = new EnableFixture(true)) {
            plugin.onEnable();

            String denial = IrisStartupValidation.denialReason().orElseThrow();
            assertTrue(denial, denial.contains("already bound"));
            ChunkGenerator generator = plugin.getDefaultWorldGenerator("world", "overworld");
            assertNotNull("a null generator is the vanilla generator", generator);
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    () -> generator.getDefaultBiomeProvider(mock(WorldInfo.class)));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("already bound"));
            fixture.bukkit.verify(Bukkit::shutdown, never());
        }
    }

    @Test
    public void onEnableStillThrowsToPaperWhenTheServerHasNoIrisWorlds() throws Exception {
        IrisPlatforms.bind(mock(IrisPlatform.class));
        Iris plugin = plugin();

        try (EnableFixture fixture = new EnableFixture(false)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, plugin::onEnable);
            assertTrue(failure.getMessage(), failure.getMessage().contains("already bound"));
        }
    }

    @Test
    public void anUnsupportedServerWithIrisWorldsLocksInsteadOfDisablingIris() throws Exception {
        Iris plugin = plugin();
        set(plugin, "pendingWorldReplacements", mock(PendingWorldReplacementManager.class));

        try (EnableFixture fixture = new EnableFixture(true);
             MockedStatic<INMS> nms = mockStatic(INMS.class);
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            nms.when(INMS::isBound).thenReturn(false);
            nms.when(INMS::bindFailure).thenReturn(new IllegalStateException("no NMS binding for 26.9"));

            plugin.onEnable();

            String denial = IrisStartupValidation.denialReason().orElseThrow();
            assertTrue(denial, denial.contains("no NMS binding for 26.9"));
            scheduler.verify(() -> J.s(any(Runnable.class), anyInt()), never());
        }
    }

    /**
     * Deferring to the server stop quiesces through the NMS binding and waits for generators a failed enable never
     * made, so a failed enable tears down at once even while the server stops.
     */
    @Test
    public void aFailedEnableNeverDefersItsTeardownToTheServerStop() {
        assertFalse(FailedEnableLock.defersTeardownToServerStop(true, true));
        assertFalse(FailedEnableLock.defersTeardownToServerStop(true, false));
        assertTrue(FailedEnableLock.defersTeardownToServerStop(false, true));
        assertFalse(FailedEnableLock.defersTeardownToServerStop(false, false));
    }

    @Test
    public void irisWorldsAreManagedStorageOrABukkitBindingForAWorldThatLoads() throws Exception {
        File levelRoot = temporaryFolder.newFolder("level", "world");
        File bukkit = new File(temporaryFolder.getRoot(), "bukkit.yml");

        assertFalse(FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.removed_iris_world.generator", "Iris:overworld");
        yaml.save(bukkit);
        assertFalse("a leftover entry for a world that never loads", FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));

        yaml.set("worlds.world.generator", "Iris:overworld");
        yaml.save(bukkit);
        assertTrue("an Iris overworld lives in a vanilla slot", FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));

        assertTrue(bukkit.delete());
        assertTrue(new File(levelRoot, "dimensions/iris/moon").mkdirs());
        assertTrue(FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));
    }

    /**
     * Paper builds plugins through its class loader, so the plugin under test is the real class with its fields
     * set by hand: the resolver getDefaultWorldGenerator delegates to, and whatever enable touches before failing.
     */
    private static Iris plugin() throws Exception {
        Iris plugin = mock(Iris.class, CALLS_REAL_METHODS);
        set(plugin, "generatorResolver", new IrisWorldGeneratorResolver(plugin));
        return plugin;
    }

    private static void set(Iris plugin, String name, Object value) throws Exception {
        Field field = Iris.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }

    /**
     * irisWorldsPresent reads the real server.properties, which a test has none of, so the answer is set here.
     */
    private static final class EnableFixture implements AutoCloseable {
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<Iris> iris = mockStatic(Iris.class);
        private final MockedStatic<FailedEnableLock> lock = mockStatic(FailedEnableLock.class, CALLS_REAL_METHODS);

        private EnableFixture(boolean irisWorlds) {
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            lock.when(FailedEnableLock::irisWorldsPresent).thenReturn(irisWorlds);
        }

        @Override
        public void close() {
            lock.close();
            iris.close();
            bukkit.close();
        }
    }
}
