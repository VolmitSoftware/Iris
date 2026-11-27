package art.arcane.iris.pack.datapack;

import art.arcane.iris.world.WorldSlotKey;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which worlds use a pack the startup datapack is compiled from. A pack copied into a world's folder
 * ({@code <world>/iris/pack} or {@code <world>/iris/generation/epochs/<epoch>/pack}) belongs to that world, configured
 * or not. A pack installed in Iris's own folder is used by every bound world that selects one of its dimensions and
 * has no copy of its own.
 */
final class PackUse {
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final String WORLD_IRIS_FOLDER = "iris";
    private static final String WORLD_PACK_FOLDER = "pack";
    private static final String GENERATION_FOLDER = "generation";
    private static final String EPOCHS_FOLDER = "epochs";

    private final Set<WorldSlotKey> worldsWithOwnPack;
    private final List<IrisGeneratorBinding> bindings;

    PackUse(Collection<File> packRoots, List<IrisGeneratorBinding> bindings) {
        this.worldsWithOwnPack = packRoots.stream()
                .map(root -> worldFolderOwning(root.toPath()))
                .flatMap(Optional::stream)
                .map(PackUse::slotKeyOf)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        this.bindings = List.copyOf(bindings);
    }

    /** The names of the worlds that use the pack at {@code packRoot}, which defines {@code dimensions}. */
    List<String> worldsUsing(File packRoot, Collection<String> dimensions) {
        Optional<Path> ownerFolder = worldFolderOwning(packRoot.toPath());
        if (ownerFolder.isPresent()) {
            return List.of(nameOfWorld(ownerFolder.get()));
        }
        return bindings.stream()
                .filter(binding -> dimensions.contains(binding.dimension()))
                .filter(binding -> !worldsWithOwnPack.contains(binding.worldKey()))
                .map(IrisGeneratorBinding::configuredWorldName)
                .toList();
    }

    private String nameOfWorld(Path worldFolder) {
        Optional<WorldSlotKey> worldKey = slotKeyOf(worldFolder);
        if (worldKey.isEmpty()) {
            return nameOf(worldFolder);
        }
        return bindings.stream()
                .filter(binding -> binding.worldKey().equals(worldKey.get()))
                .map(IrisGeneratorBinding::configuredWorldName)
                .findFirst()
                .orElse(worldKey.get().toString());
    }

    /** The folder of the world whose copy the pack at {@code packRoot} is, or empty for a pack installed outside any world. */
    static Optional<Path> worldFolderOwning(Path packRoot) {
        Path pack = packRoot.toAbsolutePath().normalize();
        if (!WORLD_PACK_FOLDER.equals(nameOf(pack))) {
            return Optional.empty();
        }
        Path parent = pack.getParent();
        if (WORLD_IRIS_FOLDER.equals(nameOf(parent))) {
            return Optional.ofNullable(parent.getParent());
        }
        Path epochs = parentOf(parent);
        Path generation = parentOf(epochs);
        Path iris = parentOf(generation);
        if (EPOCHS_FOLDER.equals(nameOf(epochs))
                && GENERATION_FOLDER.equals(nameOf(generation))
                && WORLD_IRIS_FOLDER.equals(nameOf(iris))) {
            return Optional.ofNullable(iris.getParent());
        }
        return Optional.empty();
    }

    /** The key of a world stored at {@code <level>/dimensions/<namespace>/<key>}, or empty for the level's own folder. */
    static Optional<WorldSlotKey> slotKeyOf(Path worldFolder) {
        for (Path namespace = parentOf(worldFolder); namespace != null; namespace = namespace.getParent()) {
            if (DIMENSIONS_FOLDER.equals(nameOf(namespace.getParent()))) {
                return slotKey(nameOf(namespace), namespace.relativize(worldFolder));
            }
        }
        return Optional.empty();
    }

    private static Optional<WorldSlotKey> slotKey(String namespace, Path world) {
        try {
            return Optional.of(new WorldSlotKey(namespace, world.toString().replace(File.separatorChar, '/')));
        } catch (IllegalArgumentException notAWorldKey) {
            return Optional.empty();
        }
    }

    private static Path parentOf(Path path) {
        return path == null ? null : path.getParent();
    }

    private static String nameOf(Path path) {
        return path == null || path.getFileName() == null ? null : path.getFileName().toString();
    }
}
