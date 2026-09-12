package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.IrisWorldStorage;
import org.bukkit.NamespacedKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GlobalCacheSVCTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheUsesFrozenCurrentCraftBukkitDimensionRoot() throws Exception {
        File worldContainer = temporaryFolder.newFolder("server");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "underworld");
        File configuredLevelRoot = Files.createDirectory(
                worldContainer.toPath().resolve("world_iris_underworld")
        ).toFile();
        File configuredDimensionRoot = IrisWorldStorage.dimensionRoot(configuredLevelRoot, worldKey);
        Files.createDirectories(configuredDimensionRoot.toPath().resolve("iris/pack"));

        assertEquals(
                configuredDimensionRoot,
                GlobalCacheSVC.requireCacheDimensionRoot(worldContainer, levelRoot, worldKey)
        );
    }

    @Test
    public void cacheFailsClosedWhenFrozenStorageIsAmbiguous() throws Exception {
        File worldContainer = temporaryFolder.newFolder("ambiguous-server");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "overworld");
        Files.createDirectories(IrisWorldStorage.dimensionRoot(levelRoot, worldKey).toPath());
        File configuredLevelRoot = Files.createDirectory(
                worldContainer.toPath().resolve("world_iris_overworld")
        ).toFile();
        Files.createDirectories(IrisWorldStorage.dimensionRoot(configuredLevelRoot, worldKey).toPath());

        assertThrows(
                IllegalStateException.class,
                () -> GlobalCacheSVC.requireCacheDimensionRoot(worldContainer, levelRoot, worldKey)
        );
    }

    @Test
    public void cacheFailsClosedWhenFrozenStorageIsMissing() throws Exception {
        File worldContainer = temporaryFolder.newFolder("missing-server");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "overworld");

        assertThrows(
                IllegalStateException.class,
                () -> GlobalCacheSVC.requireCacheDimensionRoot(worldContainer, levelRoot, worldKey)
        );
    }
}
