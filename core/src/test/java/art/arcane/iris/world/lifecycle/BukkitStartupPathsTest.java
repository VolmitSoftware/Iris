package art.arcane.iris.world.lifecycle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BukkitStartupPathsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultsResolveAgainstServerWorkingDirectory() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(serverRoot, new String[0]);

        assertEquals(serverRoot, paths.serverRoot());
        assertEquals(serverRoot.resolve("server.properties"), paths.serverProperties());
        assertEquals(serverRoot.resolve("bukkit.yml"), paths.bukkitConfiguration());
        assertEquals(serverRoot, paths.worldContainer());
        assertEquals("world", paths.levelName());
        assertEquals(serverRoot.resolve("world"), paths.levelRoot());
    }

    @Test
    public void shortSeparatedArgumentsOverrideRelativeDefaults() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path configurationRoot = Files.createDirectories(serverRoot.resolve("short-config"));
        Path properties = configurationRoot.resolve("custom.properties");
        Path bukkit = configurationRoot.resolve("custom-bukkit.yml");
        Files.writeString(properties, "level-name=property-world\n", StandardCharsets.UTF_8);
        Files.writeString(
                bukkit,
                "settings:\n  world-container: relative-worlds\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "-c", "short-config/custom.properties",
                        "-b", "short-config/custom-bukkit.yml",
                        "-w", "argument-world"
                }
        );

        assertEquals(properties, paths.serverProperties());
        assertEquals(bukkit, paths.bukkitConfiguration());
        assertEquals(serverRoot.resolve("relative-worlds"), paths.worldContainer());
        assertEquals("argument-world", paths.levelName());
        assertEquals(serverRoot.resolve("relative-worlds/argument-world"), paths.levelRoot());
    }

    @Test
    public void longSeparatedArgumentsUseCustomPropertiesAndWorldContainer() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path configurationRoot = Files.createDirectories(serverRoot.resolve("long-config"));
        Path properties = configurationRoot.resolve("server.properties");
        Path bukkit = configurationRoot.resolve("bukkit.yml");
        Files.writeString(properties, "level-name=ignored-property-world\n", StandardCharsets.UTF_8);
        Files.writeString(
                bukkit,
                "settings:\n  world-container: long-worlds\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "--config", "long-config/server.properties",
                        "--bukkit-settings", "long-config/bukkit.yml",
                        "--world", "long-world"
                }
        );

        assertEquals(properties, paths.serverProperties());
        assertEquals(bukkit, paths.bukkitConfiguration());
        assertEquals(serverRoot.resolve("long-worlds"), paths.worldContainer());
        assertEquals("long-world", paths.levelName());
        assertEquals(serverRoot.resolve("long-worlds/long-world"), paths.levelRoot());
    }

    @Test
    public void equalsArgumentsResolveRelativePathsAndLastWorldOverride() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path configurationRoot = Files.createDirectories(serverRoot.resolve("equals-config"));
        Path properties = configurationRoot.resolve("server.properties");
        Path bukkit = configurationRoot.resolve("bukkit.yml");
        Files.writeString(properties, "level-name=property-world\n", StandardCharsets.UTF_8);
        Files.writeString(
                bukkit,
                "settings:\n  world-container: equals-worlds\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "--config=equals-config/server.properties",
                        "--bukkit-settings=equals-config/bukkit.yml",
                        "--world=first-world",
                        "-w=last-world"
                }
        );

        assertEquals(properties, paths.serverProperties());
        assertEquals(bukkit, paths.bukkitConfiguration());
        assertEquals(serverRoot.resolve("equals-worlds"), paths.worldContainer());
        assertEquals("last-world", paths.levelName());
        assertEquals(serverRoot.resolve("equals-worlds/last-world"), paths.levelRoot());
    }

    @Test
    public void absoluteConfigurationContainerAndLevelPathsRemainAbsolute() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path configurationRoot = Files.createDirectories(serverRoot.resolve("absolute-config"));
        Path worldContainer = Files.createDirectories(serverRoot.resolve("absolute-worlds"));
        Path absoluteLevel = serverRoot.resolve("absolute-level");
        Path properties = configurationRoot.resolve("server.properties");
        Path bukkit = configurationRoot.resolve("bukkit.yml");
        Files.writeString(properties, "level-name=property-world\n", StandardCharsets.UTF_8);
        Files.writeString(
                bukkit,
                "settings:\n  world-container: '" + worldContainer + "'\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths propertiesPaths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "--config=" + properties,
                        "--bukkit-settings=" + bukkit
                }
        );
        BukkitStartupPaths argumentPaths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "-c", properties.toString(),
                        "-b", bukkit.toString(),
                        "-w", absoluteLevel.toString()
                }
        );

        assertEquals(properties, propertiesPaths.serverProperties());
        assertEquals(bukkit, propertiesPaths.bukkitConfiguration());
        assertEquals(worldContainer, propertiesPaths.worldContainer());
        assertEquals(worldContainer.resolve("property-world"), propertiesPaths.levelRoot());
        assertEquals(absoluteLevel.toString(), argumentPaths.levelName());
        assertEquals(absoluteLevel, argumentPaths.levelRoot());
    }

    @Test
    public void worldContainerArgumentsOverrideBukkitConfiguration() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path bukkit = serverRoot.resolve("bukkit.yml");
        Files.writeString(
                bukkit,
                "settings:\n  world-container: ignored-worlds\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths shortPaths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{"-W", "short-worlds", "--world=short-level"}
        );
        BukkitStartupPaths longPaths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{"--world-dir=first-worlds", "--universe", "second-worlds"}
        );
        BukkitStartupPaths explicitPaths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{"--world-container=explicit-worlds"}
        );

        assertEquals(serverRoot.resolve("short-worlds"), shortPaths.worldContainer());
        assertEquals(serverRoot.resolve("short-worlds/short-level"), shortPaths.levelRoot());
        assertEquals(serverRoot.resolve("second-worlds"), longPaths.worldContainer());
        assertEquals(serverRoot.resolve("second-worlds/world"), longPaths.levelRoot());
        assertEquals(serverRoot.resolve("explicit-worlds"), explicitPaths.worldContainer());
        assertEquals(serverRoot.resolve("explicit-worlds/world"), explicitPaths.levelRoot());
    }

    @Test
    public void compactShortArgumentsMatchServerOptionParsing() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();
        Path configurationRoot = Files.createDirectories(serverRoot.resolve("compact-config"));
        Path properties = configurationRoot.resolve("server.properties");
        Path bukkit = configurationRoot.resolve("bukkit.yml");
        Files.writeString(properties, "level-name=ignored-world\n", StandardCharsets.UTF_8);
        Files.writeString(
                bukkit,
                "settings:\n  world-container: ignored-worlds\n",
                StandardCharsets.UTF_8
        );

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{
                        "-ccompact-config/server.properties",
                        "-bcompact-config/bukkit.yml",
                        "-Wcompact-worlds",
                        "-wcompact-level"
                }
        );

        assertEquals(properties, paths.serverProperties());
        assertEquals(bukkit, paths.bukkitConfiguration());
        assertEquals(serverRoot.resolve("compact-worlds"), paths.worldContainer());
        assertEquals("compact-level", paths.levelName());
        assertEquals(serverRoot.resolve("compact-worlds/compact-level"), paths.levelRoot());
    }

    @Test
    public void applicationArgumentsExcludeJvmAndLauncherOptions() {
        assertArrayEquals(
                new String[]{"-bconfig/bukkit.yml", "-Wworlds", "-wlevel", "-cserver.properties"},
                BukkitStartupPaths.applicationArguments(new String[]{
                        "-Xmx4G",
                        "-jar",
                        "paper.jar",
                        "-bconfig/bukkit.yml",
                        "-Wworlds",
                        "-wlevel",
                        "-cserver.properties"
                })
        );
        assertArrayEquals(
                new String[]{"-bconfig/bukkit.yml"},
                BukkitStartupPaths.applicationArguments(new String[]{
                        "-Xmx4G",
                        "-cp",
                        "paper.jar",
                        "org.bukkit.craftbukkit.Main",
                        "-bconfig/bukkit.yml"
                })
        );
    }

    @Test
    public void endOfOptionsStopsStartupOptionParsing() throws Exception {
        Path serverRoot = temporaryFolder.getRoot().toPath().toRealPath();

        BukkitStartupPaths paths = BukkitStartupPaths.resolve(
                serverRoot,
                new String[]{"--", "-bcustom-bukkit.yml", "-Wcustom-worlds", "-wcustom-level"}
        );

        assertEquals(serverRoot.resolve("bukkit.yml"), paths.bukkitConfiguration());
        assertEquals(serverRoot, paths.worldContainer());
        assertEquals("world", paths.levelName());
        assertEquals(serverRoot.resolve("world"), paths.levelRoot());
    }
}
