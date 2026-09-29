package art.arcane.iris;

import art.arcane.iris.world.IrisStartupAdmissionListener;
import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.world.IrisWorldGeneratorResolver;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.net.InetAddress;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
    }

    @Test
    public void enableFailureWithIrisWorldsLocksTheRuntimeInsteadOfDisablingIris() throws Exception {
        IrisStartupValidation.begin();
        IllegalStateException failure = new IllegalStateException("service graph exploded");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Iris> iris = mockStatic(Iris.class)) {
            assertTrue(FailedEnableLock.engage(failure, true));

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
            assertFalse(FailedEnableLock.engage(new IllegalStateException("service graph exploded"), false));
        }

        String denial = IrisStartupValidation.denialReason().orElseThrow();
        assertFalse(denial, denial.contains("service graph exploded"));
    }

    @Test
    public void noticeSaysIrisStaysLockedAndWhatThatBlocks() {
        String notice = String.join("\n", FailedEnableLock.notice(
                new IllegalStateException("outer", new IllegalArgumentException("inner"))));

        assertTrue(notice, notice.contains("Iris failed to enable"));
        assertTrue(notice, notice.contains("outer"));
        assertTrue(notice, notice.contains("inner"));
        assertTrue(notice, notice.contains("vanilla"));
        assertTrue(notice, notice.contains("startup stops"));
        assertTrue(notice, notice.contains("logins"));
    }

    @Test
    public void irisWorldsAreManagedStorageOrAnyBukkitBinding() throws Exception {
        File levelRoot = temporaryFolder.newFolder("level", "world");
        File bukkit = new File(temporaryFolder.getRoot(), "bukkit.yml");

        assertFalse(FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("worlds.world.generator", "Iris:overworld");
        yaml.save(bukkit);
        assertTrue("an Iris overworld lives in a vanilla slot", FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));

        assertTrue(bukkit.delete());
        assertTrue(new File(levelRoot, "dimensions/iris/moon").mkdirs());
        assertTrue(FailedEnableLock.irisWorldsPresent(levelRoot, bukkit));
    }
}
