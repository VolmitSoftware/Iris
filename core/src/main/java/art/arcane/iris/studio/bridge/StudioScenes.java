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

import art.arcane.iris.studio.scene.EditableSceneView;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneSizes;
import art.arcane.iris.studio.scene.SceneView;
import art.arcane.iris.studio.scene.SceneViews;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.NoSuchElementException;

/** The scene views the studio lists: every feature's, then the studio's own. */
final class StudioScenes {
    private static final List<SceneView> STUDIO_VIEWS = List.of(new PackObjectsSceneView(), new ProceduralObjectsSceneView());

    private final List<SceneView> views = SceneViews.withStudioViews(STUDIO_VIEWS);

    List<SceneViewInfo> infos() {
        return views.stream().map(SceneViewInfo::of).toList();
    }

    /** @throws NoSuchElementException when no view has {@code id} */
    SceneView view(String id) {
        return views.stream().filter(view -> view.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No scene view " + id));
    }

    /** @throws NoSuchElementException when no view has {@code id}, or its entries can't be edited */
    EditableSceneView editableView(String id) {
        if (view(id) instanceof EditableSceneView editable) {
            return editable;
        }
        throw new NoSuchElementException("The entries of " + id + " can't be edited");
    }

    /** The part of each block of {@code scene}, in the order it encodes them, as little endian 32-bit integers. */
    static byte[] encodeParts(SceneBlocks scene) {
        int[] parts = scene.parts();
        ByteBuffer buffer = ByteBuffer.allocate(parts.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(parts);
        return buffer.array();
    }

    /**
     * A view as the studio lists it: {@code editable} when its entries are edited as nodes, {@code creatable} when they
     * can also be created and deleted, {@code baked} when their bake can be timed, {@code parts} when a block can be
     * traced to the part that placed it, and {@code form} when each entry has a settings form.
     */
    record SceneViewInfo(String id, String label, String description, boolean editable, boolean creatable, boolean baked,
                         boolean parts, SceneSizes sizes, boolean form) {
        static SceneViewInfo of(SceneView view) {
            boolean editable = view instanceof EditableSceneView;
            boolean creatable = view instanceof EditableSceneView editableView && editableView.creation().isPresent();
            return new SceneViewInfo(view.id(), view.label(), view.description(), editable, creatable, view.isBaked(),
                    view.tracesParts(), view.sizes(), view.hasForm());
        }
    }
}
