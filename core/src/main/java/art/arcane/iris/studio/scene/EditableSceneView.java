/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.scene;

import art.arcane.iris.generation.runtime.Engine;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A scene view whose entries are each written in one file of the pack, which the studio shows as a graph of nodes and
 * rewrites as they are edited. The studio reads and writes the file; the view only tells it which file an entry is and
 * how that file reads as nodes.
 */
public interface EditableSceneView extends SceneView {
    /** The file entry {@code key} is written in; empty when it has none of its own. */
    Optional<File> fileOf(Engine engine, String key);

    /**
     * The file of entry {@code key} as trees of nodes, each drawn growing from its root, such as an object with its
     * parts, and its materials beside it. The pack may not have read this text yet, so a node whose part the scene does
     * not show yet has {@link SceneBlocks#NO_PART}.
     *
     * @throws IllegalArgumentException when {@code file} does not hold what this view edits
     */
    List<GraphNode> graph(Engine engine, String key, JsonObject file);

    /** How the studio creates and removes entries of this view; empty when it may not. */
    default Optional<Creation> creation() {
        return Optional.empty();
    }

    /**
     * How the studio creates and removes entries: each is a JSON file in {@code folder} below the pack, a new one
     * starts as {@code starter}, and other files of the pack name an entry {@code key} as {@code referencePrefix + key},
     * which is how the studio finds what still uses an entry before it is removed.
     */
    record Creation(String folder, String starter, String referencePrefix) {
        public Creation {
            Objects.requireNonNull(folder, "folder");
            Objects.requireNonNull(starter, "starter");
            Objects.requireNonNull(referencePrefix, "referencePrefix");
        }
    }
}
