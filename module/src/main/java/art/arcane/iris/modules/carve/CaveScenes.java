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
import com.volmit.tooling.BlockParts;
import com.volmit.tooling.EditableSceneView;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;
import com.volmit.tooling.SceneCreation;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneFile;
import com.volmit.world.PackWorld;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The studio's Caves view: every cave of the pack's {@code caves/} folder carved over 128 by 128 blocks under a flat
 * surface, the carved space drawn as blocks so its shape shows. Each variant is the same cave elsewhere in the world.
 * Each cave's file can be edited as nodes, and the studio redraws the cave as it changes.
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
    public Optional<GeneratedObject> scene(PackWorld world, String key, int variant) {
        return preview(world, key, variant).map(CavePreview::scene);
    }

    @Override
    public boolean hasParts() {
        return true;
    }

    @Override
    public Optional<BlockParts> parts(PackWorld world, String key, int variant) {
        return preview(world, key, variant).map(CavePreview::parts);
    }

    @Override
    public Optional<SceneFile> fileOf(PackWorld world, String key) {
        ResourceId id = world.resources().idOf(key);
        return world.resources().ids(CaveResources.CAVES).contains(id)
                ? Optional.of(new SceneFile(CaveResources.CAVES, id))
                : Optional.empty();
    }

    // A file the pack could not read yet draws no parts, so its nodes are shown without part ids.
    @Override
    public NodeGraph graph(PackWorld world, String key, JsonText text) {
        Cave read = Optional.ofNullable(CaveResources.cave(world.resources(), key)).orElseGet(Cave::new);
        return CaveGraph.of(key.substring(key.lastIndexOf(FOLDER_SEPARATOR) + 1), text, read);
    }

    @Override
    public Optional<SceneCreation> creation() {
        return Optional.of(CREATION);
    }

    private static Optional<CavePreview> preview(PackWorld world, String key, int variant) {
        if (variant < 0 || variant >= CavePreview.VARIANTS) {
            return Optional.empty();
        }
        ResourceId id = world.resources().idOf(key);
        Cave cave = CaveResources.cave(world.resources(), id);
        if (cave == null) {
            return Optional.empty();
        }
        return Optional.of(world.state(Previews.class, ignored -> new Previews()).of(world, id, cave, variant));
    }

    private static SceneEntry entryOf(ResourceId id) {
        String path = id.path();
        int lastSeparator = path.lastIndexOf(FOLDER_SEPARATOR);
        Optional<String> folder = lastSeparator < 0 ? Optional.empty() : Optional.of(path.substring(0, lastSeparator));
        return new SceneEntry(path, path.substring(lastSeparator + 1), folder, CavePreview.VARIANTS);
    }

    /**
     * The previews the studio asked for last. It asks for a scene and then its parts, so both come from one carve. A
     * reload reads the cave into a new object, which keys a new preview.
     */
    private static final class Previews {
        private static final int KEPT = 8;

        private final Map<Place, Built> recent = new LinkedHashMap<>(KEPT, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Place, Built> eldest) {
                return size() > KEPT;
            }
        };

        CavePreview of(PackWorld world, ResourceId id, Cave cave, int variant) {
            Place place = new Place(id, variant);
            synchronized (recent) {
                Built known = recent.get(place);
                if (known != null && known.from == cave) {
                    return known.preview;
                }
            }
            CavePreview preview = CavePreview.of(world, id, cave, variant);
            synchronized (recent) {
                recent.put(place, new Built(cave, preview));
            }
            return preview;
        }
    }

    private record Place(ResourceId cave, int variant) {
    }

    private record Built(Cave from, CavePreview preview) {
    }
}
