package art.arcane.iris.pack.datapack;

import art.arcane.iris.pack.datapack.ServerConfigurator.ServerTimeoutConfiguration;
import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class ServerConfiguratorTimeoutTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void spigotLiveConfigurationKeepsTheRaisedTimeoutWhenTheServerSavesItAgain() throws Exception {
        Path file = temporaryFolder.newFile("custom-spigot.yml").toPath();
        Files.writeString(file, "settings:\n  timeout-time: 60\n  sample-setting: retained\n");
        YamlConfiguration live = load(file);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            logging.when(() -> IrisLogging.warn(anyString())).thenAnswer(invocation -> {
                assertEquals(300L, load(file).getLong("settings.timeout-time"));
                return null;
            });

            ServerConfigurator.increaseKeepAliveSpigot(file, live, timeouts());

            assertEquals(300L, live.getLong("settings.timeout-time"));
            live.set("world-settings.default.sample-world-setting", true);
            live.save(file.toFile());
            YamlConfiguration persisted = load(file);
            assertEquals(300L, persisted.getLong("settings.timeout-time"));
            assertEquals("retained", persisted.getString("settings.sample-setting"));
            logging.verify(() -> IrisLogging.warn("Updated " + file + " settings.timeout-time: 60 -> 300"
                    + " seconds. Takes effect after a server restart."));
        }
    }

    @Test
    public void higherSpigotTimeoutIsLeftUntouched() throws Exception {
        Path file = temporaryFolder.newFile("larger-spigot.yml").toPath();
        String original = "# Keep this configuration\nsettings:\n  timeout-time: 600\n";
        Files.writeString(file, original);
        YamlConfiguration live = load(file);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(file, live, timeouts());

            assertEquals(600L, live.getLong("settings.timeout-time"));
            assertEquals(original, Files.readString(file));
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void sharedSpigotConfigurationSymlinkIsPreservedWhileItsTargetIsUpdated() throws Exception {
        Path directory = temporaryFolder.newFolder("shared-settings").toPath();
        Path target = directory.resolve("shared-spigot.yml");
        Path link = directory.resolve("spigot.yml");
        Files.writeString(target, "settings:\n  timeout-time: 60\n  sample-setting: retained\n");
        Path relativeTarget = target.getFileName();
        Files.createSymbolicLink(link, relativeTarget);
        YamlConfiguration live = load(link);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increaseKeepAliveSpigot(link, live, timeouts());

            assertTrue(Files.isSymbolicLink(link));
            assertEquals(relativeTarget, Files.readSymbolicLink(link));
            assertEquals(300L, load(target).getLong("settings.timeout-time"));
            assertEquals("retained", load(target).getString("settings.sample-setting"));
            assertEquals(300L, live.getLong("settings.timeout-time"));
        }
    }

    @Test
    public void paperWatchdogPersistsToTheSpecifiedFileAndPreservesOtherSettings() throws Exception {
        Path directory = temporaryFolder.newFolder("custom-paper-directory").toPath();
        Path file = directory.resolve("paper-global.yml");
        Files.writeString(file, "watchdog:\n  early-warning-delay: 10000\n  early-warning-every: 5000\n"
                + "misc:\n  sample-setting: retained\n");

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increasePaperWatchdog(file, timeouts());

            YamlConfiguration persisted = load(file);
            assertEquals(180000L, persisted.getLong("watchdog.early-warning-delay"));
            assertEquals(5000L, persisted.getLong("watchdog.early-warning-every"));
            assertEquals("retained", persisted.getString("misc.sample-setting"));
            logging.verify(() -> IrisLogging.warn("Updated " + file + " watchdog.early-warning-delay: 10000 -> 180000"
                    + " milliseconds. Takes effect after a server restart."));
        }
    }

    @Test
    public void higherPaperWatchdogDelayIsLeftUntouched() throws Exception {
        Path file = temporaryFolder.newFile("larger-paper-global.yml").toPath();
        String original = "# Keep this configuration\nwatchdog:\n  early-warning-delay: 240000\n";
        Files.writeString(file, original);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.increasePaperWatchdog(file, timeouts());

            assertEquals(original, Files.readString(file));
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void malformedPaperYamlIsPreservedAndDoesNotReportAnUpdate() throws Exception {
        Path file = temporaryFolder.newFile("invalid-paper-global.yml").toPath();
        String malformed = "watchdog:\n  early-warning-delay: [invalid\n";
        Files.writeString(file, malformed);

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertThrows(InvalidConfigurationException.class, () -> ServerConfigurator.increasePaperWatchdog(file, timeouts()));

            assertEquals(malformed, Files.readString(file));
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void failedSpigotWriteRestoresTheLiveValueAndDoesNotReportSuccess() throws Exception {
        Path blockedParent = temporaryFolder.newFile("blocked-parent").toPath();
        Files.writeString(blockedParent, "retained");
        YamlConfiguration live = new YamlConfiguration();
        live.set("settings.timeout-time", 60L);
        live.set("settings.sample-setting", "retained");

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertThrows(IOException.class, () -> ServerConfigurator.increaseKeepAliveSpigot(
                    blockedParent.resolve("spigot.yml"), live, timeouts()));

            assertEquals(60L, live.getLong("settings.timeout-time"));
            assertEquals("retained", live.getString("settings.sample-setting"));
            assertEquals("retained", Files.readString(blockedParent));
            logging.verifyNoInteractions();
        }
    }

    @Test
    public void actualServerOptionsResolveCustomConfigurationPaths() throws Exception {
        Path directory = temporaryFolder.newFolder("custom-settings").toPath();
        File spigot = directory.resolve("nested/../server-spigot.yml").toFile();
        File paper = directory.resolve("nested/../paper").toFile();
        Server server = mock(Server.class, withSettings().extraInterfaces(ConsoleAccess.class));
        when(((ConsoleAccess) server).getServer()).thenReturn(new ConfigurationConsole(
                new ConfigurationOptions(Map.of("spigot-settings", spigot, "paper-settings-directory", paper))));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);

            assertEquals(spigot.toPath().toAbsolutePath().normalize(), ServerConfigurator.serverConfigurationOption("spigot-settings"));
            assertEquals(paper.toPath().toAbsolutePath().normalize(), ServerConfigurator.serverConfigurationOption("paper-settings-directory"));
            assertThrows(IOException.class, () -> ServerConfigurator.serverConfigurationOption("missing-option"));
        }
    }

    private ServerTimeoutConfiguration timeouts() {
        return new ServerTimeoutConfiguration(temporaryFolder.getRoot().toPath().resolve("server-timeouts.json"));
    }

    private static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file.toFile());
        return configuration;
    }

    public interface ConsoleAccess {
        ConfigurationConsole getServer();
    }

    public static final class ConfigurationConsole {
        public final ConfigurationOptions options;

        public ConfigurationConsole(ConfigurationOptions options) {
            this.options = options;
        }
    }

    public static final class ConfigurationOptions {
        private final Map<String, File> paths;

        public ConfigurationOptions(Map<String, File> paths) {
            this.paths = paths;
        }

        public File valueOf(String name) {
            return paths.get(name);
        }
    }
}
