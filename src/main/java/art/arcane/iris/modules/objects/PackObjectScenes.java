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

package art.arcane.iris.modules.objects;

import com.volmit.object.GeneratedObject;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneView;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/** The studio's Objects view: every object in the pack, one scene each, listed under the folders of its file. */
public final class PackObjectScenes implements SceneView {
    private static final ResourceId ID = ResourceId.parse("iris:objects");
    private static final char FOLDER_SEPARATOR = '/';

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String label() {
        return "Objects";
    }

    @Override
    public String description() {
        return "Every object in the pack in 3D, with the blocks it is made of";
    }

    @Override
    public List<SceneEntry> entries(PackWorld world) {
        return world.resources().ids(PackResources.OBJECTS).stream()
                .map(id -> entryOf(id.path()))
                .toList();
    }

    @Override
    public Optional<GeneratedObject> scene(PackWorld world, String key, int variant) {
        if (variant != 0) {
            return Optional.empty();
        }
        PackResources resources = world.resources();
        return resources.objects().object(resources.idOf(key)).map(PlaceableObject::geometry);
    }

    private static SceneEntry entryOf(String key) {
        int lastSeparator = key.lastIndexOf(FOLDER_SEPARATOR);
        String folder = lastSeparator < 0 ? "" : key.substring(0, lastSeparator);
        return new SceneEntry(key, key.substring(lastSeparator + 1), folder, 1);
    }
}
