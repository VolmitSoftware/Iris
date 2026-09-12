package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects an Iris world folder that was deleted out from under a live engine.
 * <p>
 * Every persistence path resolves its target from a world folder captured when the engine bound, and the
 * writes create their own parents. After an external delete that turns a save into a rebuild: a directory
 * tree with engine data and server {@code .dat} files but no pack snapshot and no regions, which the next
 * boot reads as an owned-but-broken world. Persistence stops instead, once the folder is gone.
 * <p>
 * The verdict latches. A {@code save-all} after the delete writes the level's own {@code data/*.dat} files
 * back, which recreates the world folder, and a plain "is it a directory" check would then let Iris resume
 * writing into a tree that no longer holds the world. It also accepts a directory Iris established under the
 * folder: once Iris has written {@code iris/engine-data} there, that directory disappearing is a delete
 * whether or not the server has already put the folder back, which is what makes the next boot's storage
 * classification the same on every run instead of depending on save ordering.
 */
public final class VanishedWorldStorage {
    private static final Set<String> VANISHED = ConcurrentHashMap.newKeySet();

    private VanishedWorldStorage() {
    }

    /**
     * True when the world folder is no longer a directory, or was already found gone earlier in this JVM.
     */
    public static boolean vanished(File worldFolder) {
        return vanished(worldFolder, null);
    }

    /**
     * True when the world folder is gone, or when {@code establishedTree} - a directory Iris is known to have
     * created under it already - is gone. Reports the first time it sees each folder.
     *
     * @param establishedTree a directory Iris has already written, or null when it has written none yet
     */
    public static boolean vanished(File worldFolder, File establishedTree) {
        if (worldFolder == null) {
            return false;
        }
        Path path = worldFolder.toPath().toAbsolutePath().normalize();
        if (VANISHED.contains(path.toString())) {
            return true;
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return report(path);
        }
        if (establishedTree == null) {
            return false;
        }
        Path tree = establishedTree.toPath().toAbsolutePath().normalize();
        if (Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return report(path);
    }

    /**
     * Visible for tests: the report set is per JVM, not per server boot.
     */
    public static void reset() {
        VANISHED.clear();
    }

    private static boolean report(Path path) {
        if (VANISHED.add(path.toString())) {
            IrisLogging.error("Iris world storage is gone at %s; that world is no longer being written.", path);
            IrisLogging.error("Unload or remove the world, or restore the folder and restart the server.");
        }
        return true;
    }
}
