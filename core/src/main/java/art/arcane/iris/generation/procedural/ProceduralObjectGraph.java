package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.procedural.part.PartBlob;
import art.arcane.iris.generation.procedural.part.PartBranches;
import art.arcane.iris.generation.procedural.part.PartCluster;
import art.arcane.iris.generation.procedural.part.PartOutline;
import art.arcane.iris.generation.procedural.part.PartStem;
import art.arcane.iris.generation.procedural.part.ProceduralDecoration;
import art.arcane.iris.generation.procedural.part.ProceduralMaterial;
import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.generation.procedural.part.ProceduralPart;
import art.arcane.iris.studio.scene.GraphNode;
import art.arcane.iris.studio.scene.GraphNode.Addition;
import art.arcane.iris.studio.scene.GraphNode.Edits;
import art.arcane.iris.studio.scene.GraphNode.Link;
import art.arcane.iris.studio.scene.GraphNode.NodeSettings;
import art.arcane.iris.studio.scene.JsonPath;
import art.arcane.iris.studio.scene.SceneBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A procedural object's file as nodes: the object with its parts growing from it, each part with the parts it holds,
 * and its materials and decorations beside it. A part, material or decoration written as the name of a snippet is
 * edited in the snippet's file, so it is shown without settings and can't be changed here.
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

    private final PartOutline outline;

    private ProceduralObjectGraph(PartOutline outline) {
        this.outline = outline;
    }

    /**
     * @param read what the pack reads from {@code file}, so its parts line up with the file's; parts it could not read
     *             get no part
     */
    static List<GraphNode> of(String name, JsonObject file, ProceduralObject read) {
        return new ProceduralObjectGraph(PartOutline.of(read)).graph(name, file, read);
    }

    private List<GraphNode> graph(String name, JsonObject file, ProceduralObject read) {
        JsonPath parts = JsonPath.ROOT.member(PARTS);
        GraphNode object = new GraphNode(NodeKind.OBJECT.label, name, JsonPath.ROOT, SceneBlocks.NO_PART, List.of(),
                List.of(new NodeSettings(NodeKind.OBJECT.label, JsonPath.ROOT, ProceduralObject.class, OBJECT_NODES)),
                parts(parts, file.get(PARTS), read.parts(), read.defaultRole()),
                new Edits(Optional.of(parts), false, objectAdditions(parts)));
        List<GraphNode> roots = new ArrayList<>();
        roots.add(object);
        roots.addAll(listed(MATERIALS, file.get(MATERIALS), NodeKind.MATERIAL, ProceduralMaterial.class));
        roots.addAll(listed(DECORATIONS, file.get(DECORATIONS), NodeKind.DECORATION, ProceduralDecoration.class));
        return roots;
    }

    // The pack reads the file's arrays element for element, so the definition's lists line up with them.
    private List<Link> parts(JsonPath arrayPath, JsonElement array, List<ProceduralPart> read, String parentRole) {
        if (!(array instanceof JsonArray elements)) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
            ProceduralPart part = index < read.size() ? read.get(index) : null;
            links.add(new Link(GROWS, part(arrayPath.element(index), elements.get(index), part, parentRole, true)));
        }
        return links;
    }

    private GraphNode part(JsonPath path, JsonElement json, ProceduralPart read, String parentRole, boolean movable) {
        int drawnPart = read == null ? SceneBlocks.NO_PART : outline.idOf(read);
        if (!(json instanceof JsonObject object)) {
            return sharedNode(NodeKind.SHARED_PART, path, json, drawnPart, List.of(parentRole));
        }
        String role = stringAt(object, ROLE).filter(written -> !written.isBlank()).orElse(parentRole);
        List<NodeSettings> settings = new ArrayList<>();
        settings.add(new NodeSettings(NodeKind.PART.label, path, ProceduralPart.class, PART_NODES));
        List<Link> children = new ArrayList<>();
        NodeKind kind = NodeKind.GROUP;
        for (Shape shape : Shape.values()) {
            if (!(object.get(shape.key) instanceof JsonObject shapeObject)) {
                continue;
            }
            kind = kind == NodeKind.GROUP || shape.copiesAPart() ? shape.kind : kind;
            JsonPath shapePath = path.member(shape.key);
            settings.add(new NodeSettings(shape.kind.label, shapePath, shape.settings, shape.copiesAPart() ? COPY_NODES : NO_NODES));
            JsonElement copied = shapeObject.get(COPIED_PART);
            if (shape.copiesAPart() && copied != null) {
                children.add(new Link(EACH_COPY, part(shapePath.member(COPIED_PART), copied, copiedPartOf(read, shape), role, false)));
            }
        }
        JsonPath childList = path.member(CHILDREN);
        children.addAll(parts(childList, object.get(CHILDREN), read == null ? List.of() : read.children(), role));
        return new GraphNode(kind.label, kind.label, path, drawnPart, List.of(role), settings, children,
                new Edits(Optional.of(childList), movable, partAdditions(childList)));
    }

    private static GraphNode sharedNode(NodeKind kind, JsonPath path, JsonElement json, int drawnPart, List<String> roles) {
        String title = json instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : String.valueOf(json);
        return new GraphNode(kind.label, title, path, drawnPart, roles, List.of(), List.of(), Edits.NONE);
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

    private static List<GraphNode> listed(String key, JsonElement array, NodeKind kind, Class<?> settings) {
        if (!(array instanceof JsonArray elements)) {
            return List.of();
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
            JsonPath path = JsonPath.ROOT.member(key).element(index);
            if (!(elements.get(index) instanceof JsonObject element)) {
                nodes.add(sharedNode(kind.shared, path, elements.get(index), SceneBlocks.NO_PART, List.of()));
                continue;
            }
            nodes.add(new GraphNode(kind.label, kind.label, path, SceneBlocks.NO_PART, rolesOf(kind, element),
                    List.of(new NodeSettings(kind.label, path, settings, NO_NODES)), List.of(), Edits.NONE));
        }
        return nodes;
    }

    private static List<String> rolesOf(NodeKind kind, JsonObject element) {
        if (kind == NodeKind.MATERIAL) {
            return List.of(stringAt(element, ROLE).orElse(DEFAULT_MATERIAL_ROLE));
        }
        if (!(element.get(DECORATED_ROLES) instanceof JsonArray roles)) {
            return List.of();
        }
        List<String> decorated = new ArrayList<>();
        for (JsonElement role : roles) {
            if (role instanceof JsonPrimitive primitive && primitive.isString()) {
                decorated.add(primitive.getAsString());
            }
        }
        return decorated;
    }

    private static Optional<String> stringAt(JsonObject object, String key) {
        return object.get(key) instanceof JsonPrimitive primitive && primitive.isString() ? Optional.of(primitive.getAsString()) : Optional.empty();
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
        additions.add(new Addition("Material", JsonPath.ROOT.member(MATERIALS), "{\"role\": \"main\", \"block\": \"minecraft:stone\"}"));
        additions.add(new Addition("Decoration", JsonPath.ROOT.member(DECORATIONS), "{\"block\": \"minecraft:vine\"}"));
        return additions;
    }

    private enum NodeKind {
        OBJECT("Object"),
        PART("Part"),
        STEM("Stem"),
        BLOB("Blob"),
        BRANCHES("Branches"),
        CLUSTER("Cluster"),
        GROUP("Group"),
        SHARED_PART("Shared part"),
        SHARED_MATERIAL("Shared material"),
        SHARED_DECORATION("Shared decoration"),
        MATERIAL("Material", SHARED_MATERIAL),
        DECORATION("Decoration", SHARED_DECORATION);

        private final String label;
        /** What an element of this kind is called when it is written as a snippet's name. */
        private final NodeKind shared;

        NodeKind(String label) {
            this(label, null);
        }

        NodeKind(String label, NodeKind shared) {
            this.label = label;
            this.shared = shared;
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
