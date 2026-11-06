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

import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.ProceduralVariants;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import com.volmit.object.GeneratedObject;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.EditableSceneView;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneFile;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * The studio's Procedural view: every procedural object of the pack with each of its variants, baked as the world
 * bakes them, so what the studio shows is what generation places. Each object's file can be edited as nodes.
 */
public final class ProceduralObjectScenes implements EditableSceneView {
    private static final ResourceId ID = ResourceId.parse("iris:objects/procedural");
    private static final char FOLDER_SEPARATOR = '/';

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String label() {
        return "Procedural";
    }

    @Override
    public String description() {
        return "Trees, rock formations and more that Iris generates from settings, every variant in 3D";
    }

    @Override
    public List<SceneEntry> entries(PackWorld world) {
        ProceduralObjectCatalog catalog = ProceduralObjectCatalog.of(world);
        return world.resources().ids(ProceduralObjectCatalog.DEFINITIONS).stream()
                .flatMap(id -> catalog.variants(ProceduralObjectCatalog.KEY_PREFIX + id.path()).stream())
                .map(ProceduralObjectScenes::entryOf)
                .toList();
    }

    @Override
    public Optional<GeneratedObject> scene(PackWorld world, String key, int variant) {
        return ProceduralObjectCatalog.of(world).variants(ProceduralObjectCatalog.KEY_PREFIX + key)
                .flatMap(variants -> variants.variant(variant))
                .map(PlaceableObject::geometry);
    }

    @Override
    public Optional<SceneFile> fileOf(PackWorld world, String key) {
        ResourceId id = world.resources().idOf(key);
        return world.resources().ids(ProceduralObjectCatalog.DEFINITIONS).contains(id)
                ? Optional.of(new SceneFile(ProceduralObjectCatalog.DEFINITIONS, id))
                : Optional.empty();
    }

    // A file the pack could not read has no parts baked from it, so its nodes are drawn without part ids.
    @Override
    public NodeGraph graph(PackWorld world, String key, JsonText text) {
        ProceduralObject read = world.resources().find(ProceduralObjectCatalog.DEFINITIONS, world.resources().idOf(key))
                .orElseGet(ProceduralObject::new);
        return ProceduralObjectGraph.of(key.substring(key.lastIndexOf(FOLDER_SEPARATOR) + 1), text, read);
    }

    private static SceneEntry entryOf(ProceduralVariants variants) {
        String path = variants.path();
        int lastSeparator = path.lastIndexOf(FOLDER_SEPARATOR);
        Optional<String> folder = lastSeparator < 0 ? Optional.empty() : Optional.of(path.substring(0, lastSeparator));
        return new SceneEntry(path, path.substring(lastSeparator + 1), folder, variants.variantCount());
    }
}
