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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.procedural.ProceduralObjectCatalog;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneView;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The pack's object files in 3D, filed by their folders. The object loader also lists the procedural objects it bakes,
 * which the procedural parts view shows instead.
 */
final class PackObjectsSceneView implements SceneView {
    static final String ID = "iris:objects";
    private static final char FOLDER_SEPARATOR = '/';
    private static final int VARIANTS_OF_A_FILE = 1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Objects";
    }

    @Override
    public String description() {
        return "Every object file in the pack, in 3D";
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        return Arrays.stream(engine.getData().getObjectLoader().getPossibleKeys())
                .filter(key -> !ProceduralObjectCatalog.isProceduralKey(key))
                .sorted()
                .map(PackObjectsSceneView::entryOf)
                .toList();
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        String key = request.key();
        if (ProceduralObjectCatalog.isProceduralKey(key)) {
            throw new NoSuchElementException("No object file " + key);
        }
        IrisObject object = engine.getData().getObjectLoader().load(key);
        if (object == null) {
            throw new NoSuchElementException("No object file " + key);
        }
        return SceneBlocks.of(object);
    }

    private static SceneEntry entryOf(String key) {
        int lastSeparator = key.lastIndexOf(FOLDER_SEPARATOR);
        String folder = lastSeparator < 0 ? "" : key.substring(0, lastSeparator);
        return new SceneEntry(key, key.substring(lastSeparator + 1), folder, VARIANTS_OF_A_FILE);
    }
}
