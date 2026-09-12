package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.WorldRemovalPathPolicy;
import org.bukkit.NamespacedKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WorldRemovalPathPolicyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesExactIrisDimensionDirectory() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("world").toPath();

        WorldRemovalPathPolicy.Target target = WorldRemovalPathPolicy.resolve("Iris World", "world", levelRoot);

        assertEquals("iris:iris_world", target.worldKey().toString());
        assertEquals("iris_world", target.logicalName());
        assertEquals(
                levelRoot.resolve("dimensions/iris/iris_world").toAbsolutePath().normalize(),
                target.worldDirectory()
        );
        assertEquals(target.worldDirectory(), target.storageDirectory());
    }

    @Test
    public void resolvesCurrentCraftBukkitConfiguredDimensionDirectory() throws Exception {
        Path worldContainer = temporaryFolder.newFolder("configured-removal-server").toPath();
        Path levelRoot = Files.createDirectory(worldContainer.resolve("world"));
        Path dimensionRoot = Files.createDirectories(
                worldContainer.resolve("world_iris_moon/dimensions/iris/moon")
        );

        WorldRemovalPathPolicy.Target target = WorldRemovalPathPolicy.resolve("moon", "world", levelRoot);

        assertEquals(dimensionRoot.toAbsolutePath().normalize(), target.worldDirectory());
        assertEquals(worldContainer.resolve("world_iris_moon").toAbsolutePath().normalize(),
                target.storageDirectory());
        WorldRemovalPathPolicy.validateStoragePath(levelRoot, target.worldKey(), dimensionRoot);
        WorldRemovalPathPolicy.validateStorageRoot(
                levelRoot,
                target.worldKey(),
                worldContainer.resolve("world_iris_moon")
        );
    }

    @Test
    public void rejectsConfiguredMainAndMinecraftNamespace() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("main-protection").toPath();

        WorldRemovalPathPolicy.Rejection mainFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.resolve("production", "production", levelRoot)
        );
        WorldRemovalPathPolicy.Rejection namespaceFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.resolve("minecraft:the_nether", "production", levelRoot)
        );

        assertEquals(WorldRemovalPathPolicy.RejectionReason.CONFIGURED_MAIN_WORLD, mainFailure.reason());
        assertEquals(WorldRemovalPathPolicy.RejectionReason.MINECRAFT_NAMESPACE, namespaceFailure.reason());
    }

    @Test
    public void rejectsTraversalAndOutsideCandidate() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("path-protection").toPath();
        NamespacedKey worldKey = NamespacedKey.fromString("iris:safe");

        WorldRemovalPathPolicy.Rejection traversalFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.resolve("../world", "production", levelRoot)
        );
        WorldRemovalPathPolicy.Rejection outsideFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.validateStoragePath(
                        levelRoot,
                        worldKey,
                        levelRoot.resolve("safe")
                )
        );

        assertEquals(WorldRemovalPathPolicy.RejectionReason.INVALID_IDENTIFIER, traversalFailure.reason());
        assertEquals(WorldRemovalPathPolicy.RejectionReason.OUTSIDE_STORAGE_ROOT, outsideFailure.reason());
    }

    @Test
    public void rejectsSymbolicLinkTargetAndParent() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("symlink-protection").toPath();
        Path dimensionsRoot = Files.createDirectories(levelRoot.resolve("dimensions"));
        Path realNamespace = temporaryFolder.newFolder("real-namespace").toPath();
        Path namespaceLink = dimensionsRoot.resolve("iris");
        Files.createSymbolicLink(namespaceLink, realNamespace);

        WorldRemovalPathPolicy.Rejection parentFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.resolve("linked", "production", levelRoot)
        );
        assertEquals(WorldRemovalPathPolicy.RejectionReason.SYMBOLIC_LINK, parentFailure.reason());

        Files.delete(namespaceLink);
        Path namespace = Files.createDirectories(dimensionsRoot.resolve("iris"));
        Path realTarget = temporaryFolder.newFolder("real-target").toPath();
        Files.createSymbolicLink(namespace.resolve("linked"), realTarget);

        WorldRemovalPathPolicy.Rejection targetFailure = assertThrows(
                WorldRemovalPathPolicy.Rejection.class,
                () -> WorldRemovalPathPolicy.resolve("linked", "production", levelRoot)
        );
        assertEquals(WorldRemovalPathPolicy.RejectionReason.SYMBOLIC_LINK, targetFailure.reason());
    }

    /**
     * "iris worlds", Multiverse and the storage folder each print a different name for the same world, so
     * all three forms have to select it. Storage presence must not change which world is selected either;
     * an orphan is exactly the world an admin needs to remove.
     */
    @Test
    public void everyPrintedFormOfAWorldNameSelectsTheSameTarget() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("name-forms", "world").toPath();

        for (boolean storagePresent : new boolean[]{false, true}) {
            if (storagePresent) {
                Files.createDirectories(levelRoot.resolve("dimensions/iris/orphan1"));
            }
            WorldRemovalPathPolicy.Target bare =
                    WorldRemovalPathPolicy.resolve("orphan1", "world", levelRoot);
            WorldRemovalPathPolicy.Target startup =
                    WorldRemovalPathPolicy.resolve("world_iris_orphan1", "world", levelRoot);
            WorldRemovalPathPolicy.Target namespaced =
                    WorldRemovalPathPolicy.resolve("iris:orphan1", "world", levelRoot);

            assertEquals("iris:orphan1", bare.worldKey().toString());
            assertEquals(bare.worldKey(), startup.worldKey());
            assertEquals(bare.worldKey(), namespaced.worldKey());
            assertEquals(bare.worldDirectory(), startup.worldDirectory());
            assertEquals(bare.worldDirectory(), namespaced.worldDirectory());
            assertEquals(bare.storageDirectory(), startup.storageDirectory());
            assertEquals(bare.storageDirectory(), namespaced.storageDirectory());
        }
    }
}
