package com.volmit.iris.util.misc;

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
        String[] args = ProcessHandle.current()
                .info()
                .arguments()
                .orElse(new String[0]);

        String propertiesPath = "server.properties";
        String bukkitYml = "bukkit.yml";
        String levelName = null;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "-c", "--config" -> propertiesPath = args[i + 1];
                case "-b", "--bukkit-settings" -> bukkitYml = args[i + 1];
                case "-w", "--level-name", "--world" -> levelName = args[i + 1];
            }
        }

        SERVER_PROPERTIES = new File(propertiesPath);
        BUKKIT_YML = new File(bukkitYml);
        try (FileInputStream in = new FileInputStream(SERVER_PROPERTIES)){
            DATA.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (levelName != null) LEVEL_NAME = levelName;
        else LEVEL_NAME = DATA.getProperty("level-name", "world");
    }
}
