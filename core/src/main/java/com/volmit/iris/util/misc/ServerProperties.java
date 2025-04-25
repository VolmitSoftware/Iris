package com.volmit.iris.util.misc;

import com.volmit.iris.core.nms.INMSBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class ServerProperties {
    public static final Properties DATA = new Properties();
    public static File SERVER_PROPERTIES = new File("server.properties");
    public static File BUKKIT_YML = new File("bukkit.yml");
    public static File SPIGOT_YML = new File("spigot.yml");
    public static File PAPER_DIR = new File("config");
    public static String LEVEL_NAME = "world";

    public enum FILES {
        BUKKIT_YML,
        SPIGOT_YML,
        SERVER_PROPERTIES,
        PAPER_DIR,
        WORLD_NAME
    }

    public static void init(INMSBinding inmsBinding) {
        Map<FILES, Object> fileLocations = inmsBinding.getFileLocations();
        if (fileLocations == null) return;
        SERVER_PROPERTIES = (File) fileLocations.get(FILES.SERVER_PROPERTIES);
        BUKKIT_YML = (File) fileLocations.get(FILES.BUKKIT_YML);
        SPIGOT_YML = (File) fileLocations.get(FILES.SPIGOT_YML);
        PAPER_DIR = (File) fileLocations.get(FILES.PAPER_DIR);
        String levelName = (String) fileLocations.get(FILES.WORLD_NAME);

        try (FileInputStream input = new FileInputStream(SERVER_PROPERTIES)) {
            DATA.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (levelName != null) LEVEL_NAME = levelName;
        else LEVEL_NAME = DATA.getProperty("level-name", "world");
    }
}
