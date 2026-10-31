package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

public final class WideObjectStructures {
    public static final int MAX_DISTANCE = 128;
    private static final int MAX_POOL_WEIGHT = 150;
    private static final int MAX_SPACING = 4096;
    private static final int MIN_START_Y = -2032;
    private static final int MAX_START_Y = 2031;
    private static final String TAG_FOLDER = "has_structure/object/";
    private static final String SURFACE_HEIGHTMAP = "WORLD_SURFACE_WG";
    private static final String SURFACE_STEP = "surface_structures";

    private final String namespace;

    public WideObjectStructures(String namespace) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "wide object namespace");
    }

    public static Spacing spacing(double perChunk) {
        if (!(perChunk > 0)) {
            throw new IllegalArgumentException("Wide object rate must be positive, got " + perChunk);
        }
        if (perChunk >= 1) {
            return new Spacing(1, 0, 1);
        }
        int spacing = Math.min(MAX_SPACING, Math.max(1, (int) Math.floor(Math.sqrt(1 / perChunk))));
        double frequency = Math.min(1, perChunk * spacing * spacing);
        return new Spacing(spacing, spacing / 2, frequency);
    }

    public static JsonObject jigsaw(SinglePiece piece) {
        JsonObject structure = new JsonObject();
        structure.addProperty("type", "minecraft:jigsaw");
        structure.addProperty("biomes", piece.biomes());
        structure.addProperty("max_distance_from_center", piece.maxDistance());
        piece.heightmap().ifPresent(heightmap -> structure.addProperty("project_start_to_heightmap", heightmap));
        structure.addProperty("size", 1);
        structure.add("spawn_overrides", new JsonObject());
        structure.add("start_height", piece.startHeight().deepCopy());
        piece.startJigsawName().ifPresent(jigsawName -> structure.addProperty("start_jigsaw_name", jigsawName));
        structure.addProperty("start_pool", piece.startPool());
        structure.addProperty("step", piece.step());
        structure.addProperty("terrain_adaptation", "none");
        structure.addProperty("use_expansion_hack", false);
        return structure;
    }

    public static JsonObject randomSpread(String structureId, Spacing spacing, int salt) {
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        if (spacing.frequency() < 1) {
            placement.addProperty("frequency", (float) spacing.frequency());
        }
        placement.addProperty("salt", salt);
        placement.addProperty("separation", spacing.separation());
        placement.addProperty("spacing", spacing.spacing());
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", structureId);
        entry.addProperty("weight", 1);
        JsonArray structures = new JsonArray(1);
        structures.add(entry);
        JsonObject set = new JsonObject();
        set.add("placement", placement);
        set.add("structures", structures);
        return set;
    }

    public Compiled compile(String name, List<TemplateChoice> templates, double perChunk, int startY, JsonObject processors) {
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("Wide object structure '" + name + "' has no templates");
        }
        JsonObject pool = pool(templates, processors);
        String hash = ContentIds.hash(pool.toString() + ";rate=" + perChunk + ";y=" + startY);
        String path = "object/" + name + "_" + hash;
        String id = namespace + ":" + path;
        int footprint = 1;
        for (TemplateChoice template : templates) {
            footprint = Math.max(footprint, template.variant().volume().footprint());
        }
        List<JsonResource> resources = new ArrayList<>(3);
        resources.add(new JsonResource("data/" + namespace + "/worldgen/template_pool/" + path + ".json", pool));
        resources.add(new JsonResource("data/" + namespace + "/worldgen/structure/" + path + ".json",
                structure(path, id, footprint, startY)));
        resources.add(new JsonResource("data/" + namespace + "/worldgen/structure_set/" + path + ".json",
                structureSet(id, perChunk, hash)));
        return new Compiled(id, resources);
    }

    public JsonResource biomeTag(String structureId, Collection<String> biomeIds) {
        String path = structureId.substring(namespace.length() + 1);
        JsonArray values = new JsonArray();
        for (String biome : new TreeSet<>(biomeIds)) {
            values.add(biome);
        }
        JsonObject tag = new JsonObject();
        tag.add("values", values);
        return new JsonResource("data/" + namespace + "/tags/worldgen/biome/" + tagPath(path) + ".json", tag);
    }

    private JsonObject pool(List<TemplateChoice> templates, JsonObject processors) {
        long largest = 1;
        for (TemplateChoice template : templates) {
            largest = Math.max(largest, template.weight());
        }
        JsonArray elements = new JsonArray(templates.size());
        for (TemplateChoice template : templates) {
            JsonObject element = new JsonObject();
            element.addProperty("element_type", "minecraft:single_pool_element");
            element.addProperty("location", template.variant().id());
            element.add("processors", processors.deepCopy());
            element.addProperty("projection", "rigid");
            JsonObject weighted = new JsonObject();
            weighted.add("element", element);
            weighted.addProperty("weight", Math.max(1, Math.round(template.weight() * (double) MAX_POOL_WEIGHT / largest)));
            elements.add(weighted);
        }
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");
        pool.add("elements", elements);
        return pool;
    }

    private JsonObject structure(String path, String id, int footprint, int startY) {
        JsonObject start = new JsonObject();
        start.addProperty("absolute", Math.max(MIN_START_Y, Math.min(MAX_START_Y, startY)));
        return jigsaw(new SinglePiece("#" + namespace + ":" + tagPath(path), id, Math.min(MAX_DISTANCE, footprint), start,
                Optional.of(SURFACE_HEIGHTMAP), Optional.empty(), SURFACE_STEP));
    }

    private static JsonObject structureSet(String id, double perChunk, String hash) {
        return randomSpread(id, spacing(perChunk), (int) (ContentIds.seed(hash) & Integer.MAX_VALUE));
    }

    private static String tagPath(String structurePath) {
        return TAG_FOLDER + structurePath.substring("object/".length());
    }

    public record Spacing(int spacing, int separation, double frequency) {
        public Spacing {
            if (spacing < 1 || spacing > MAX_SPACING || separation < 0 || separation >= spacing) {
                throw new IllegalArgumentException("Random spread needs 0 <= separation < spacing <= " + MAX_SPACING
                        + ", got spacing " + spacing + " and separation " + separation);
            }
            if (!(frequency > 0) || frequency > 1) {
                throw new IllegalArgumentException("Random spread frequency must be within (0, 1], got " + frequency);
            }
        }
    }

    public record SinglePiece(String biomes, String startPool, int maxDistance, JsonObject startHeight, Optional<String> heightmap,
                              Optional<String> startJigsawName, String step) {
        public SinglePiece {
            Objects.requireNonNull(biomes, "Structure biomes");
            Objects.requireNonNull(startPool, "Structure start pool");
            Objects.requireNonNull(startHeight, "Structure start height");
            Objects.requireNonNull(heightmap, "Structure heightmap");
            Objects.requireNonNull(startJigsawName, "Structure start jigsaw name");
            Objects.requireNonNull(step, "Structure generation step");
            if (maxDistance < 1 || maxDistance > MAX_DISTANCE) {
                throw new IllegalArgumentException("Structure max distance must be within [1, " + MAX_DISTANCE + "], got " + maxDistance);
            }
        }
    }

    public record Compiled(String structureId, List<JsonResource> resources) {
        public Compiled {
            resources = List.copyOf(resources);
        }
    }
}
