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

package art.arcane.iris.modules.carve;

import com.volmit.object.GeneratedObject;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.EditableSceneView;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;
import com.volmit.tooling.SceneCreation;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneFile;
import com.volmit.tooling.SceneRequest;
import com.volmit.tooling.SceneSizes;
import com.volmit.world.PackWorld;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The studio's Caves view: every cave of the pack's {@code caves/} folder carved over a square of the world under a
 * flat surface, the carved space drawn as blocks so its shape shows. Each variant is the same cave elsewhere in the
 * world. A cave's settings are shown beside it, and the studio redraws the cave as they change.
 */
public final class CaveScenes implements EditableSceneView {
    private static final ResourceId ID = ResourceId.parse("iris:carve/caves");
    private static final char FOLDER_SEPARATOR = '/';
    /** Other files name a cave by its bare key, in a {@code caves} list. */
    private static final String REFERENCE_PREFIX = "";
    private static final String STARTER = """
            {
                "tunnel": {"width": {"min": 4, "max": 7}, "stretch": 96}
            }
            """;
    private static final SceneCreation CREATION = new SceneCreation(CaveResources.CAVES, STARTER, REFERENCE_PREFIX);

    @Override
    public ResourceId id() {
        return ID;
    }

    @Override
    public String label() {
        return "Caves";
    }

    @Override
    public String description() {
        return "Each cave carved under a flat surface at Y " + CavePreview.SURFACE_WORLD_Y + ", tunnels blue, caverns orange";
    }

    @Override
    public List<SceneEntry> entries(PackWorld world) {
        return world.resources().ids(CaveResources.CAVES).stream().map(CaveScenes::entryOf).toList();
    }

    @Override
    public Optional<SceneSizes> sizes() {
        return Optional.of(CavePreview.SIZES);
    }

    @Override
    public Optional<GeneratedObject> scene(PackWorld world, SceneRequest request) {
        int size = request.size().orElse(CavePreview.SIZES.opening());
        if (request.variant() < 0 || request.variant() >= CavePreview.VARIANTS || !CavePreview.SIZES.offers(size)) {
            return Optional.empty();
        }
        ResourceId id = world.resources().idOf(request.key());
        Cave cave = CaveResources.cave(world.resources(), id);
        if (cave == null) {
            return Optional.empty();
        }
        return Optional.of(world.state(Previews.class, ignored -> new Previews()).of(world, new Place(id, request.variant(), size), cave));
    }

    @Override
    public Optional<SceneFile> fileOf(PackWorld world, String key) {
        ResourceId id = world.resources().idOf(key);
        return world.resources().ids(CaveResources.CAVES).contains(id)
                ? Optional.of(new SceneFile(CaveResources.CAVES, id))
                : Optional.empty();
    }

    @Override
    public NodeGraph graph(PackWorld world, String key, JsonText text) {
        return CaveGraph.of(key.substring(key.lastIndexOf(FOLDER_SEPARATOR) + 1), text);
    }

    @Override
    public boolean showsNodes() {
        return false;
    }

    @Override
    public Optional<SceneCreation> creation() {
        return Optional.of(CREATION);
    }

    private static SceneEntry entryOf(ResourceId id) {
        String path = id.path();
        int lastSeparator = path.lastIndexOf(FOLDER_SEPARATOR);
        Optional<String> folder = lastSeparator < 0 ? Optional.empty() : Optional.of(path.substring(0, lastSeparator));
        return new SceneEntry(path, path.substring(lastSeparator + 1), folder, CavePreview.VARIANTS);
    }

    /**
     * The scenes the studio asked for last, so stepping back to a size or variant is instant. A reload reads the cave
     * into a new object, which builds its scenes anew.
     */
    private static final class Previews {
        private static final int KEPT = 8;

        private final Map<Place, Built> recent = new LinkedHashMap<>(KEPT, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Place, Built> eldest) {
                return size() > KEPT;
            }
        };

        GeneratedObject of(PackWorld world, Place place, Cave cave) {
            synchronized (recent) {
                Built known = recent.get(place);
                if (known != null && known.from == cave) {
                    return known.scene;
                }
            }
            GeneratedObject scene = CavePreview.of(world, place.cave(), cave, place.variant(), place.size());
            synchronized (recent) {
                recent.put(place, new Built(cave, scene));
            }
            return scene;
        }
    }

    private record Place(ResourceId cave, int variant, int size) {
    }

    private record Built(Cave from, GeneratedObject scene) {
    }
}
