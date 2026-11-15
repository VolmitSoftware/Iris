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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Something of a pack the studio can list and draw as blocks, such as its cave profiles. A feature implements this next
 * to its own code and names it in {@link SceneViews}; the studio shows whatever is listed there and knows nothing else
 * about the feature.
 */
public interface SceneView {
    /** Tells this view from the others; the studio also picks the view's icon by it. */
    String id();

    String label();

    String description();

    /** Null when each entry draws at its own size. */
    default SceneSizes sizes() {
        return null;
    }

    List<SceneEntry> entries(Engine engine);

    /** @throws NoSuchElementException when the pack has no entry of the requested key */
    SceneBlocks scene(Engine engine, SceneRequest request) throws IOException;

    /** Whether each entry has a settings form. */
    default boolean hasForm() {
        return false;
    }

    /** The entry's settings with the typed values applied; only asked of views with a form. */
    default SceneForm form(Engine engine, String key, Map<String, String> overrides) {
        throw new NoSuchElementException(id() + " has no settings form");
    }

    /** Whether the entries are baked from settings, so their bake can be timed. */
    default boolean isBaked() {
        return false;
    }

    /** One timed bake of the entry; only asked of baked views. */
    default SceneBakeTiming timeBake(Engine engine, String key) {
        throw new UnsupportedOperationException(id() + " is not baked");
    }

    /** Whether the scenes say which part placed each block, see {@link SceneBlocks#add(int, int, int, String, int)}. */
    default boolean tracesParts() {
        return false;
    }
}
