package com.volmit.iris.core.nms.v26_3_R1;

import com.volmit.iris.Iris;
import com.volmit.iris.core.world.WorldStorage;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.misc.ServerProperties;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class DimensionWorldStorage implements WorldStorage {
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final String DATAPACKS_FOLDER = "datapacks";
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String OVERWORLD_KEY = "overworld";
    private static final String PREVIOUS_OVERWORLD_PREFIX = "overworld_before_";

    // Paper only keeps vanilla data when it imports a pre-26.1 world and deletes the old folder.
    private static final List<String> IRIS_ENTRIES = List.of("iris", "mantle", "lazygen.json", "turbogen.json", "DeepSearch.json");

    @Override
    public File worldFolder(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world.getWorldFolder();
        }

        // Paper keys a world created by name the same way, so this is where the server will put it.
        NamespacedKey key = new WorldCreator(worldName).key();
        return levelDirectory()
                .resolve(DIMENSIONS_FOLDER)
                .resolve(key.getNamespace())
                .resolve(key.getKey())
                .toFile();
    }

    @Override
    public KList<File> datapackFolders() {
        return new KList<File>().qadd(levelDirectory().resolve(DATAPACKS_FOLDER).toFile());
    }

    @Override
    public void makeMainWorld(String worldName) throws IOException {
        // Players, data packs, the nether and the end belong to the level, so only the overworld is swapped.
        Path world = worldFolder(worldName).toPath();
        Path overworld = levelDirectory().resolve(DIMENSIONS_FOLDER).resolve(MINECRAFT_NAMESPACE).resolve(OVERWORLD_KEY);
        if (Files.exists(overworld)) {
            Path previous = overworld.resolveSibling(PREVIOUS_OVERWORLD_PREFIX + world.getFileName());
            Files.move(overworld, previous);
            Iris.info("Moved the previous main world to " + previous);
        }

        Files.move(world, overworld);
        moveGeneratorToMainWorld(worldName);
        Iris.info(worldName + " is now the main world.");
    }

    private static void moveGeneratorToMainWorld(String worldName) throws IOException {
        YamlConfiguration bukkit = YamlConfiguration.loadConfiguration(ServerProperties.BUKKIT_YML);
        ConfigurationSection worlds = bukkit.getConfigurationSection("worlds");
        if (worlds == null) {
            return;
        }

        String generator = worlds.getString(worldName + ".generator");
        worlds.set(worldName, null);
        worlds.set(ServerProperties.LEVEL_NAME + ".generator", generator);
        bukkit.save(ServerProperties.BUKKIT_YML);
    }

    @Override
    public void adoptLegacyData(String worldName) {
        File legacyFolder = new File(Bukkit.getWorldContainer(), worldName);
        File worldFolder = worldFolder(worldName);
        if (!legacyFolder.isDirectory() || legacyFolder.getAbsoluteFile().equals(worldFolder.getAbsoluteFile())) {
            return;
        }

        for (String entry : IRIS_ENTRIES) {
            moveEntry(new File(legacyFolder, entry), new File(worldFolder, entry));
        }
    }

    private static void moveEntry(File source, File target) {
        if (!source.exists()) {
            return;
        }
        if (target.exists()) {
            Iris.warn("Not moving " + source + " because " + target + " already exists.");
            return;
        }

        try {
            Files.createDirectories(target.toPath().getParent());
            Files.move(source.toPath(), target.toPath());
            Iris.info("Moved " + source + " to " + target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to move " + source + " to " + target, e);
        }
    }

    private static Path levelDirectory() {
        return Bukkit.getServer().getLevelDirectory().toAbsolutePath().normalize();
    }
}
