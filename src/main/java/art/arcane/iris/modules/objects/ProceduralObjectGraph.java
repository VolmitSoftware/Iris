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

import art.arcane.iris.engine.procedural.part.PartBlob;
import art.arcane.iris.engine.procedural.part.PartBranches;
import art.arcane.iris.engine.procedural.part.PartCluster;
import art.arcane.iris.engine.procedural.part.PartOutline;
import art.arcane.iris.engine.procedural.part.PartStem;
import art.arcane.iris.engine.procedural.part.ProceduralDecoration;
import art.arcane.iris.engine.procedural.part.ProceduralMaterial;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.engine.procedural.part.ProceduralPart;
import com.volmit.tooling.GraphNode;
import com.volmit.tooling.GraphNode.Addition;
import com.volmit.tooling.GraphNode.Link;
import com.volmit.tooling.GraphNode.NodeSettings;
import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A procedural object's file as nodes: the object, the parts growing from it, its materials and its decorations (v3e's
 * node editor). Each part node is the part of the baked object it draws, so the studio can pick one from the other.
 */
final class ProceduralObjectGraph {
    private static final String PARTS = "parts";
    private static final String CHILDREN = "children";
    private static final String COPIED_PART = "part";
    private static final String MATERIALS = "materials";
    private static final String DECORATIONS = "decorations";
    private static final String ROLE = "role";
    private static final String DECORATED_ROLES = "on";
    private static final String DEFAULT_MATERIAL_ROLE = "main";
    private static final String EACH_COPY = "each copy";
    private static final String GROWS = "";
    private static final Set<String> OBJECT_NODES = Set.of(MATERIALS, PARTS, DECORATIONS);
    private static final Set<String> PART_NODES = Set.of(Shape.STEM.key, Shape.BLOB.key, Shape.BRANCHES.key, Shape.CLUSTER.key, CHILDREN);
    private static final Set<String> COPY_NODES = Set.of(COPIED_PART);
    private static final Set<String> NO_NODES = Set.of();

    private final JsonText source;
    private final PartOutline outline;

    private ProceduralObjectGraph(JsonText source, PartOutline outline) {
        this.source = source;
        this.outline = outline;
    }

    /**
     * @param read what the pack read from the file, so its parts line up with the file's; it may be older than
     *             {@code source}, and parts past what it read get no part id
     * @throws IllegalArgumentException when the file does not hold an object
     */
    static NodeGraph of(String name, JsonText source, ProceduralObject read) {
        if (!(source.root() instanceof JsonText.Obj root)) {
            throw new IllegalArgumentException("The file does not hold an object");
        }
        return new ProceduralObjectGraph(source, PartOutline.of(read)).graph(name, root, read);
    }

    private NodeGraph graph(String name, JsonText.Obj root, ProceduralObject read) {
        JsonPath parts = JsonPath.of(PARTS);
        GraphNode object = new GraphNode("Object", name, JsonPath.ROOT, GraphNode.NO_PART, List.of(),
                List.of(new NodeSettings("Object", JsonPath.ROOT, ProceduralObject.class, OBJECT_NODES)),
                parts(parts, root.get(PARTS), read.parts(), read.defaultRole()), Optional.of(parts), false, objectAdditions(parts));
        List<GraphNode> roots = new ArrayList<>();
        roots.add(object);
        roots.addAll(listed(MATERIALS, root.get(MATERIALS), NodeKind.MATERIAL, ProceduralMaterial.class));
        roots.addAll(listed(DECORATIONS, root.get(DECORATIONS), NodeKind.DECORATION, ProceduralDecoration.class));
        return new NodeGraph(roots);
    }

    // The pack read the file's arrays element for element, so the definition's lists line up with them.
    private List<Link> parts(JsonPath arrayPath, Optional<JsonText.Value> array, List<ProceduralPart> read, String parentRole) {
        if (!(array.orElse(null) instanceof JsonText.Arr elements)) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        for (int index = 0; index < elements.elements().size(); index++) {
            ProceduralPart part = index < read.size() ? read.get(index) : null;
            links.add(new Link(GROWS, part(arrayPath.element(index), elements.elements().get(index), part, parentRole, true)));
        }
        return links;
    }

    private GraphNode part(JsonPath path, JsonText.Value json, ProceduralPart read, String parentRole, boolean movable) {
        int partId = read == null ? GraphNode.NO_PART : outline.idOf(read);
        if (!(json instanceof JsonText.Obj object)) {
            return sharedPart(path, json, partId, parentRole, movable);
        }
        String role = stringAt(object, ROLE).filter(written -> !written.isBlank()).orElse(parentRole);
        List<NodeSettings> settings = new ArrayList<>();
        settings.add(new NodeSettings("Part", path, ProceduralPart.class, PART_NODES));
        List<Link> children = new ArrayList<>();
        NodeKind kind = NodeKind.GROUP;
        for (Shape shape : Shape.values()) {
            if (!(object.get(shape.key).orElse(null) instanceof JsonText.Obj shapeObject)) {
                continue;
            }
            kind = kind == NodeKind.GROUP || shape.copiesAPart() ? shape.kind : kind;
            JsonPath shapePath = path.member(shape.key);
            settings.add(new NodeSettings(shape.kind.label, shapePath, shape.settings, shape.copiesAPart() ? COPY_NODES : NO_NODES));
            Optional<JsonText.Value> copied = shapeObject.get(COPIED_PART);
            if (shape.copiesAPart() && copied.isPresent()) {
                children.add(new Link(EACH_COPY, part(shapePath.member(COPIED_PART), copied.get(), copiedPartOf(read, shape), role, false)));
            }
        }
        JsonPath childList = path.member(CHILDREN);
        children.addAll(parts(childList, object.get(CHILDREN), read == null ? List.of() : read.children(), role));
        return new GraphNode(kind.label, kind.label, path, partId, List.of(role), settings, children, Optional.of(childList), movable,
                partAdditions(childList));
    }

    // A part written as the name of a snippet file, which is edited there.
    private GraphNode sharedPart(JsonPath path, JsonText.Value json, int partId, String parentRole, boolean movable) {
        String title = json instanceof JsonText.Literal literal && literal.string().isPresent() ? literal.string().get() : source.textOf(json);
        return new GraphNode(NodeKind.SHARED_PART.label, title, path, partId, List.of(parentRole), List.of(), List.of(), Optional.empty(),
                movable, List.of());
    }

    private static ProceduralPart copiedPartOf(ProceduralPart part, Shape shape) {
        if (part == null) {
            return null;
        }
        if (shape == Shape.BRANCHES) {
            return part.branches() == null ? null : part.branches().part();
        }
        return part.cluster() == null ? null : part.cluster().part();
    }

    private static List<GraphNode> listed(String key, Optional<JsonText.Value> array, NodeKind kind, Class<?> settings) {
        if (!(array.orElse(null) instanceof JsonText.Arr elements)) {
            return List.of();
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (int index = 0; index < elements.elements().size(); index++) {
            if (!(elements.elements().get(index) instanceof JsonText.Obj element)) {
                continue;
            }
            JsonPath path = JsonPath.of(key, index);
            nodes.add(new GraphNode(kind.label, kind.label, path, GraphNode.NO_PART, rolesOf(kind, element),
                    List.of(new NodeSettings(kind.label, path, settings, NO_NODES)), List.of(), Optional.empty(), false, List.of()));
        }
        return nodes;
    }

    private static List<String> rolesOf(NodeKind kind, JsonText.Obj element) {
        if (kind == NodeKind.MATERIAL) {
            return List.of(stringAt(element, ROLE).orElse(DEFAULT_MATERIAL_ROLE));
        }
        if (!(element.get(DECORATED_ROLES).orElse(null) instanceof JsonText.Arr roles)) {
            return List.of();
        }
        return roles.elements().stream()
                .flatMap(role -> role instanceof JsonText.Literal literal ? literal.string().stream() : Stream.empty())
                .toList();
    }

    private static Optional<String> stringAt(JsonText.Obj object, String key) {
        return object.get(key).flatMap(value -> value instanceof JsonText.Literal literal ? literal.string() : Optional.empty());
    }

    /** What a new part starts as, so it bakes into something visible at once. */
    private static List<Addition> partAdditions(JsonPath list) {
        return List.of(
                new Addition("Stem", list, "{\"stem\": {\"length\": 6}}"),
                new Addition("Blob", list, "{\"blob\": {\"radius\": 2}}"),
                new Addition("Branches", list, "{\"branches\": {\"count\": 4, \"part\": {\"stem\": {\"length\": 4}}}}"),
                new Addition("Cluster", list, "{\"cluster\": {\"count\": 5, \"part\": {\"stem\": {\"length\": 3}}}}"),
                new Addition("Group", list, "{}"));
    }

    private static List<Addition> objectAdditions(JsonPath parts) {
        List<Addition> additions = new ArrayList<>(partAdditions(parts));
        additions.add(new Addition("Material", JsonPath.of(MATERIALS), "{\"role\": \"main\", \"block\": \"minecraft:stone\"}"));
        additions.add(new Addition("Decoration", JsonPath.of(DECORATIONS), "{\"block\": \"minecraft:vine\"}"));
        return additions;
    }

    private enum NodeKind {
        STEM("Stem"),
        BLOB("Blob"),
        BRANCHES("Branches"),
        CLUSTER("Cluster"),
        GROUP("Group"),
        SHARED_PART("Shared part"),
        MATERIAL("Material"),
        DECORATION("Decoration");

        private final String label;

        NodeKind(String label) {
            this.label = label;
        }
    }

    /** The shapes a part can take, each written as a member of the part holding its settings. */
    private enum Shape {
        STEM("stem", NodeKind.STEM, PartStem.class),
        BLOB("blob", NodeKind.BLOB, PartBlob.class),
        BRANCHES("branches", NodeKind.BRANCHES, PartBranches.class),
        CLUSTER("cluster", NodeKind.CLUSTER, PartCluster.class);

        private final String key;
        private final NodeKind kind;
        private final Class<?> settings;

        Shape(String key, NodeKind kind, Class<?> settings) {
            this.key = key;
            this.kind = kind;
            this.settings = settings;
        }

        boolean copiesAPart() {
            return this == BRANCHES || this == CLUSTER;
        }
    }
}
