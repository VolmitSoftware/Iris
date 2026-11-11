/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core;

import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.IrisGeneratorBinding;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which worlds use a pack the startup datapack is compiled from. A pack copied into a world's folder
 * ({@code <level>/dimensions/<namespace>/<world>/iris/pack}) belongs to that world, configured or not. A pack installed
 * in Iris's own folder is used by every configured world that selects one of its dimensions and has no copy of its own.
 */
public final class PackUse {
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final String WORLD_IRIS_FOLDER = "iris";
    private static final String WORLD_PACK_FOLDER = "pack";

    private final Set<WorldSlotKey> worldsWithOwnPack;
    private final List<IrisGeneratorBinding> bindings;

    PackUse(Collection<File> packRoots, List<IrisGeneratorBinding> bindings) {
        this.worldsWithOwnPack = packRoots.stream()
                .map(root -> worldOwning(root.toPath()))
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        this.bindings = List.copyOf(bindings);
    }

    /** The names of the worlds that use the pack at {@code packRoot}, which defines {@code dimensions}. */
    List<String> worldsUsing(File packRoot, Collection<String> dimensions) {
        Optional<WorldSlotKey> owner = worldOwning(packRoot.toPath());
        if (owner.isPresent()) {
            return List.of(nameOf(owner.get()));
        }
        return bindings.stream()
                .filter(binding -> dimensions.contains(binding.dimension()))
                .filter(binding -> !worldsWithOwnPack.contains(binding.worldKey()))
                .map(IrisGeneratorBinding::configuredWorldName)
                .toList();
    }

    private String nameOf(WorldSlotKey world) {
        return bindings.stream()
                .filter(binding -> binding.worldKey().equals(world))
                .map(IrisGeneratorBinding::configuredWorldName)
                .findFirst()
                .orElse(world.toString());
    }

    /**
     * The folder of the world that {@code packRoot} was copied into ({@code <world>/iris/pack}), wherever that world lies;
     * empty for a pack installed outside any world.
     */
    public static Optional<Path> worldFolderHolding(Path packRoot) {
        Path irisFolder = packRoot.toAbsolutePath().normalize().getParent();
        if (!WORLD_PACK_FOLDER.equals(nameOf(packRoot)) || irisFolder == null || !WORLD_IRIS_FOLDER.equals(nameOf(irisFolder))) {
            return Optional.empty();
        }
        return Optional.ofNullable(irisFolder.getParent());
    }

    /** The world whose folder holds the pack at {@code packRoot}, or empty for a pack installed outside any world. */
    static Optional<WorldSlotKey> worldOwning(Path packRoot) {
        Optional<Path> holdingWorld = worldFolderHolding(packRoot);
        if (holdingWorld.isEmpty()) {
            return Optional.empty();
        }
        Path worldFolder = holdingWorld.get();
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
