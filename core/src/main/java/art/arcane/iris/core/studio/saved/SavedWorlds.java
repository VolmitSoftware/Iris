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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.core.IrisWorldStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The worlds saved under the server's level folder, each a dimension folder holding region files, and the ones the
 * studio opened. A world is named by its folder relative to the level folder, so the studio can only open those.
 */
public final class SavedWorlds {
    private static final String REGION_FOLDER = "region";
    private static final String DIMENSIONS_FOLDER = "dimensions";
    // Before 26.1 the nether and the end lived in these folders of the level folder.
    private static final Map<String, String> LEGACY_DIMENSIONS = Map.of("DIM-1", "the_nether", "DIM1", "the_end");
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static volatile SavedWorlds shared;

    private final Path levelRoot;
    private final SavedGround ground;
    private final CustomBlocks customBlocks;
    private final Map<String, SavedWorld> opened = new ConcurrentHashMap<>();

    SavedWorlds(Path levelRoot, SavedGround ground, CustomBlocks customBlocks) {
        this.levelRoot = levelRoot;
        this.ground = ground;
        this.customBlocks = customBlocks;
    }

    /** The server's saved worlds; their regions stay in memory between requests. */
    public static SavedWorlds shared() {
        SavedWorlds current = shared;
        if (current == null) {
            synchronized (SavedWorlds.class) {
                if (shared == null) {
                    CustomBlocks customBlocks = CustomBlocks.platform();
                    shared = new SavedWorlds(IrisWorldStorage.levelRoot().toPath(), SavedGround.platform(customBlocks), customBlocks);
                }
                current = shared;
            }
        }
        return current;
    }

    /** Every saved world, vanilla's first, then by namespace and name. */
    public List<Info> list() {
        List<Info> worlds = new ArrayList<>();
        if (SavedWorld.holdsRegionFiles(levelRoot.resolve(REGION_FOLDER))) {
            worlds.add(new Info(".", "overworld", VANILLA_NAMESPACE));
        }
        LEGACY_DIMENSIONS.forEach((folder, name) -> {
            if (SavedWorld.holdsRegionFiles(levelRoot.resolve(folder).resolve(REGION_FOLDER))) {
                worlds.add(new Info(folder, name, VANILLA_NAMESPACE));
            }
        });
        worlds.addAll(dimensionFolders());
        worlds.sort(Comparator.comparing((Info info) -> !info.namespace().equals(VANILLA_NAMESPACE))
                .thenComparing(Info::namespace)
                .thenComparing(Info::name));
        return worlds;
    }

    /**
     * The world {@link #list} names {@code id}.
     *
     * @throws NoSuchElementException where it names none
     */
    public SavedWorld open(String id) {
        if (list().stream().noneMatch(info -> info.id().equals(id))) {
            throw new NoSuchElementException("No saved world " + id);
        }
        return opened.computeIfAbsent(id, known -> new SavedWorld(levelRoot.resolve(known).resolve(REGION_FOLDER).normalize(), ground, customBlocks));
    }

    private List<Info> dimensionFolders() {
        Path dimensions = levelRoot.resolve(DIMENSIONS_FOLDER);
        if (!Files.isDirectory(dimensions)) {
            return List.of();
        }
        try (Stream<Path> namespaces = Files.list(dimensions)) {
            return namespaces.filter(Files::isDirectory).flatMap(this::dimensionsIn).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private Stream<Info> dimensionsIn(Path namespace) {
        try (Stream<Path> folders = Files.list(namespace)) {
            return folders.filter(folder -> SavedWorld.holdsRegionFiles(folder.resolve(REGION_FOLDER)))
                    .map(folder -> new Info(levelRoot.relativize(folder).toString().replace('\\', '/'),
                            folder.getFileName().toString(), namespace.getFileName().toString()))
                    .toList()
                    .stream();
        } catch (IOException unreadable) {
            return Stream.empty();
        }
    }

    /**
     * @param id        the world's folder relative to the level folder, which {@link #open} takes
     * @param namespace who added the dimension, such as {@code minecraft} or {@code iris}
     */
    public record Info(String id, String name, String namespace) {
    }
}
