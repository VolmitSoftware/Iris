package art.arcane.iris.probe;

import net.kyori.adventure.key.Key;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.spigotmc.SpigotConfig;
import org.spigotmc.SpigotWorldConfig;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

final class HeadlessStructureConfiguration {
    private static final int CONFIG_VERSION = 13;
    private static final Field CONFIG_FILE = nativeField("CONFIG_FILE");
    private static final Field VERSION = nativeField("version");

    private HeadlessStructureConfiguration() {
    }

    static SpigotWorldConfig load(Options options) throws IOException {
        if (Bukkit.getServer() != null) {
            throw new IllegalStateException("Headless structure configuration cannot initialize inside a Bukkit server");
        }
        Files.createDirectories(options.stagingDirectory());
        Path staged = Files.createTempFile(options.stagingDirectory(), "spigot-structures-", ".yml");
        try {
            Files.copy(options.source(), staged, StandardCopyOption.REPLACE_EXISTING);
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(staged.toFile());
            configuration.options().copyDefaults(true);
            int version = configuration.getInt("config-version", CONFIG_VERSION);
            String worldName = options.levelKey().identifier().toString();
            prepareEnvironment(configuration, options.legacyWorldName(), worldName, version);
            configuration.set("config-version", CONFIG_VERSION);
            return initialize(options, staged, configuration);
        } catch (InvalidConfigurationException | ReflectiveOperationException | RuntimeException failure) {
            throw new IOException("Could not initialize native structure configuration from " + options.source(), failure);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void prepareEnvironment(YamlConfiguration configuration, String legacyWorldName,
                                           String worldName, int version) {
        if (version <= 12) {
            ConfigurationSection legacy = configuration.getConfigurationSection("world-settings." + legacyWorldName);
            if (legacy != null) {
                configuration.set("world-settings." + legacyWorldName, null);
                configuration.set("world-settings." + worldName, legacy);
            }
        }
        String worldPath = "world-settings." + worldName + ".";
        configuration.set(worldPath + "verbose", false);
        configuration.set(worldPath + "view-distance", 3);
        configuration.set(worldPath + "simulation-distance", 3);
    }

    private static SpigotWorldConfig initialize(Options options, Path staged, YamlConfiguration configuration)
            throws ReflectiveOperationException {
        synchronized (SpigotConfig.class) {
            YamlConfiguration previousConfiguration = SpigotConfig.config;
            File previousFile = (File) CONFIG_FILE.get(null);
            int previousVersion = VERSION.getInt(null);
            try {
                SpigotConfig.config = configuration;
                CONFIG_FILE.set(null, staged.toFile());
                VERSION.setInt(null, CONFIG_VERSION);
                return new SpigotWorldConfig(options.legacyWorldName(), Key.key(options.levelKey().identifier().toString()));
            } finally {
                SpigotConfig.config = previousConfiguration;
                CONFIG_FILE.set(null, previousFile);
                VERSION.setInt(null, previousVersion);
            }
        }
    }

    private static Field nativeField(String name) {
        try {
            Field field = SpigotConfig.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    record Options(Path source, String legacyWorldName, ResourceKey<Level> levelKey, Path stagingDirectory) {
        Options {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(legacyWorldName, "legacyWorldName");
            Objects.requireNonNull(levelKey, "levelKey");
            Objects.requireNonNull(stagingDirectory, "stagingDirectory");
        }
    }
}
