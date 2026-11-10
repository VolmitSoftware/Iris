package art.arcane.iris.core;

import art.arcane.iris.spi.IrisLogging;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class ServerTimeoutConfigurationTest {
    private static final String SPIGOT_KEY = ServerTimeoutConfiguration.SPIGOT_TIMEOUT_KEY;
    private static final String PAPER_KEY = ServerTimeoutConfiguration.PAPER_WATCHDOG_KEY;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void originalValuesSurviveRestartsAndRestoreIndependently() throws Exception {
        Path spigot = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        Path paper = configuration("paper-global.yml", "watchdog:\n  early-warning-delay: 10000\n  early-warning-every: 5000\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(spigot, timeouts());
            ServerConfigurator.increasePaperWatchdog(paper, timeouts());
            ServerConfigurator.increaseKeepAliveSpigot(spigot, timeouts());
            timeouts().restore(SPIGOT_KEY);
            assertEquals(60L, load(spigot).getLong(SPIGOT_KEY));
            assertEquals(180000L, load(paper).getLong(PAPER_KEY));
            timeouts().restore(PAPER_KEY);
            assertEquals(10000L, load(paper).getLong(PAPER_KEY));
            assertEquals(5000L, load(paper).getLong("watchdog.early-warning-every"));
            assertFalse(Files.readString(recoveryFile()).contains("spigot.yml"));
            assertFalse(Files.readString(recoveryFile()).contains("paper-global.yml"));
        }
    }

    @Test
    public void valueAtOrAboveTheMinimumIsLeftAlone() throws Exception {
        String original = "settings:\n  timeout-time: 600\n";
        Path file = configuration("spigot.yml", original);
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            assertEquals(original, Files.readString(file));
            assertFalse(Files.exists(recoveryFile()));
        }
    }

    @Test
    public void restoringAbsentSettingRemovesTheOverride() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  unrelated: retained\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            assertEquals(300L, load(file).getLong(SPIGOT_KEY));
            timeouts().restore(SPIGOT_KEY);
            assertFalse(load(file).isSet(SPIGOT_KEY));
            assertEquals("retained", load(file).getString("settings.unrelated"));
        }
    }

    @Test
    public void restorePreservesOperatorChangesAndOtherSettings() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            Files.writeString(file, "# Operator settings\nsettings:\n  timeout-time: 90\n  unrelated: retained\n");
            String changed = Files.readString(file);
            timeouts().restore(SPIGOT_KEY);
            assertEquals(changed, Files.readString(file));
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            timeouts().restore(SPIGOT_KEY);
            assertEquals(90L, load(file).getLong(SPIGOT_KEY));
            assertEquals("retained", load(file).getString("settings.unrelated"));
        }
    }

    @Test
    public void symlinkSurvivesRaiseAndRestore() throws Exception {
        Path target = configuration("shared-spigot.yml", "settings:\n  timeout-time: 60\n");
        Path link = temporaryFolder.getRoot().toPath().resolve("spigot.yml");
        Files.createSymbolicLink(link, target.getFileName());
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(link, timeouts());
            assertEquals(300L, load(target).getLong(SPIGOT_KEY));
            timeouts().restore(SPIGOT_KEY);
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(60L, load(target).getLong(SPIGOT_KEY));
        }
    }

    @Test
    public void aLaterAdjustmentRecordsTheOperatorsNewOriginal() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            Files.writeString(file, "settings:\n  timeout-time: 90\n");
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            timeouts().restore(SPIGOT_KEY);
            assertEquals(90L, load(file).getLong(SPIGOT_KEY));
        }
    }

    @Test
    public void failedRecoveryWriteLeavesServerSettingsUntouched() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        Path blocked = temporaryFolder.newFile("blocked").toPath();
        ServerTimeoutConfiguration timeouts = new ServerTimeoutConfiguration(blocked.resolve("server-timeouts.json"));
        assertThrows(IOException.class, () -> timeouts.raise(file, SPIGOT_KEY, 300L));
        assertEquals(60L, load(file).getLong(SPIGOT_KEY));
    }

    @Test
    public void failedServerWriteKeepsTheOriginalRecorded() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        try (MockedStatic<ServerConfigurator> writes = mockStatic(ServerConfigurator.class, CALLS_REAL_METHODS)) {
            writes.when(() -> ServerConfigurator.writeStringAtomic(eq(file.toRealPath()), anyString()))
                    .thenThrow(new IOException("blocked server write"));
            assertThrows(IOException.class, () -> timeouts().raise(file, SPIGOT_KEY, 300L));
        }
        assertEquals(60L, load(file).getLong(SPIGOT_KEY));
        assertTrue(Files.readString(recoveryFile()).contains("\"original\": 60"));
    }

    @Test
    public void interruptedRestoreRetriesWithoutOverwritingLaterOperatorEdits() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            try (MockedStatic<ServerConfigurator> writes = mockStatic(ServerConfigurator.class, CALLS_REAL_METHODS)) {
                writes.when(() -> ServerConfigurator.writeStringAtomic(eq(recoveryFile()), anyString()))
                        .thenThrow(new IOException("interrupted recovery write"));
                assertThrows(IOException.class, () -> timeouts().restore(SPIGOT_KEY));
            }
            assertTrue(Files.readString(recoveryFile()).contains("spigot.yml"));
            Files.writeString(file, "settings:\n  timeout-time: 120\n");
            timeouts().restore(SPIGOT_KEY);
            assertEquals(120L, load(file).getLong(SPIGOT_KEY));
            assertFalse(Files.readString(recoveryFile()).contains("spigot.yml"));
        }
    }

    @Test
    public void recordForADeletedFileIsDropped() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, timeouts());
            Files.delete(file);
            timeouts().restore(SPIGOT_KEY);
            assertFalse(Files.readString(recoveryFile()).contains("spigot.yml"));
        }
    }

    @Test
    public void malformedRecoveryDataPreventsNewConfigurationWrites() throws Exception {
        Path file = configuration("spigot.yml", "settings:\n  timeout-time: 60\n");
        Files.writeString(recoveryFile(), "{invalid");
        assertThrows(IOException.class, () -> timeouts().raise(file, SPIGOT_KEY, 300L));
        assertEquals(60L, load(file).getLong(SPIGOT_KEY));
        assertEquals("{invalid", Files.readString(recoveryFile()));
    }

    private Path configuration(String name, String content) throws IOException {
        Path file = temporaryFolder.newFile(name).toPath();
        Files.writeString(file, content);
        return file;
    }

    private ServerTimeoutConfiguration timeouts() {
        return new ServerTimeoutConfiguration(recoveryFile());
    }

    private Path recoveryFile() {
        return temporaryFolder.getRoot().toPath().resolve("server-timeouts.json");
    }

    private static YamlConfiguration load(Path file) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file.toFile());
        return configuration;
    }
}
