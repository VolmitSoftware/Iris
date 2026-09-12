package art.arcane.iris.platform.bootstrap;

import art.arcane.iris.world.lifecycle.BukkitStartupPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerProperties {
    public static final Properties DATA = new Properties();
    public static final File SERVER_PROPERTIES;
    public static final File BUKKIT_YML;

    public static final String LEVEL_NAME;

    static {
        BukkitStartupPaths startupPaths;
        try {
            startupPaths = BukkitStartupPaths.resolveCurrent();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        SERVER_PROPERTIES = startupPaths.serverProperties().toFile();
        BUKKIT_YML = startupPaths.bukkitConfiguration().toFile();
        try (FileInputStream in = new FileInputStream(SERVER_PROPERTIES)) {
            DATA.load(in);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        LEVEL_NAME = startupPaths.levelName();
    }
}
