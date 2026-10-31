package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.object.WideObjectStructures;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RoomStructures {
    static final String FOLDER = "subterrain/";
    private static final String ANCHOR = "subterrain/anchor";
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final String UNDERGROUND_STEP = "underground_structures";
    private static final int GROUND_LEVEL_DELTA = 1;
    private static final int CHUNK_WIDTH = 16;
    private static final int MAX_SPACING = 4096;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final VanillaBlockState JIGSAW = VanillaBlockState.parse("minecraft:jigsaw[orientation=north_up]");
    private static final VanillaBlockState CAVE_AIR = VanillaBlockState.parse("minecraft:cave_air");
    private static final VanillaBlockState WATER = VanillaBlockState.parse("minecraft:water");
    private static final VanillaBlockState LAVA = VanillaBlockState.parse("minecraft:lava");

    private final String namespace;
    private final DataVersion target;
    private final WorldgenSyntax syntax;
    private final StructureTemplateEncoder encoder = new StructureTemplateEncoder();

    RoomStructures(String namespace, DataVersion target) {
        this.namespace = namespace;
        this.target = target;
        syntax = WorldgenSyntax.forTarget(target);
    }

    Compiled compile(IrisSubterrainFeature feature, BakedFeature baked, String path, String biomes) {
        String id = namespace + ":" + path;
        List<BinaryResource> templates = new ArrayList<>(2 * baked.rooms().size());
        JsonArray elements = new JsonArray(baked.rooms().size());
        for (int index = 0; index < baked.rooms().size(); index++) {
            BakedRoom room = baked.rooms().get(index);
            String core = path + "/" + index + "_core";
            templates.add(template(core, anchored(room)));
            JsonObject element = single(core, new JsonArray());
            if (!room.shell().blocks().isEmpty()) {
                String shell = path + "/" + index + "_shell";
                templates.add(template(shell, room.shell()));
                JsonArray parts = new JsonArray(2);
                parts.add(element);
                parts.add(single(shell, guard()));
                element = new JsonObject();
                element.addProperty("element_type", "minecraft:list_pool_element");
                element.add("elements", parts);
                element.addProperty("projection", "rigid");
            }
            JsonObject weighted = new JsonObject();
            weighted.add("element", element);
            weighted.addProperty("weight", 1);
            elements.add(weighted);
        }
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", EMPTY_POOL);
        pool.add("elements", elements);
        JsonObject structure = WideObjectStructures.jigsaw(new WideObjectStructures.SinglePiece(biomes, id, Math.max(1, baked.reach()),
                startHeight(baked), Optional.empty(), Optional.of(namespace + ":" + ANCHOR), UNDERGROUND_STEP));
        List<JsonResource> json = new ArrayList<>(3);
        json.add(new JsonResource("data/" + namespace + "/worldgen/template_pool/" + path + ".json", pool));
        json.add(new JsonResource("data/" + namespace + "/worldgen/structure/" + path + ".json", structure));
        json.add(new JsonResource("data/" + namespace + "/worldgen/structure_set/" + path + ".json",
                WideObjectStructures.randomSpread(id, spacing(feature), salt(feature.getId()))));
        return new Compiled(json, templates);
    }

    private static int salt(String featureId) {
        long hash = FNV_OFFSET;
        for (int index = 0; index < featureId.length(); index++) {
            hash = (hash ^ featureId.charAt(index)) * FNV_PRIME;
        }
        return (int) (hash ^ (hash >>> 32)) & Integer.MAX_VALUE;
    }

    private static WideObjectStructures.Spacing spacing(IrisSubterrainFeature feature) {
        int spacing = Math.max(1, Math.min(MAX_SPACING, (int) Math.round(feature.getSpacing() / (double) CHUNK_WIDTH)));
        return new WideObjectStructures.Spacing(spacing, spacing / 2, Math.min(1.0, feature.getProbability()));
    }

    private TemplateVolume anchored(BakedRoom room) {
        List<TemplateBlock> blocks = new ArrayList<>(room.core().blocks().size() + 1);
        blocks.addAll(room.core().blocks());
        CompoundTag jigsaw = new CompoundTag();
        jigsaw.putString("id", "minecraft:jigsaw");
        jigsaw.putString("name", namespace + ":" + ANCHOR);
        jigsaw.putString("target", EMPTY_POOL);
        jigsaw.putString("pool", EMPTY_POOL);
        jigsaw.putString("final_state", room.anchorState().canonical());
        jigsaw.putString("joint", "rollable");
        jigsaw.putInt("selection_priority", 0);
        jigsaw.putInt("placement_priority", 0);
        blocks.add(new TemplateBlock(room.anchorX(), room.anchorY(), room.anchorZ(), JIGSAW, jigsaw));
        return new TemplateVolume(room.core().sizeX(), room.core().sizeY(), room.core().sizeZ(), blocks);
    }

    private BinaryResource template(String path, TemplateVolume volume) {
        try {
            return new BinaryResource("data/" + namespace + "/structure/" + path + ".nbt", encoder.encode(volume, target));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not encode subterrain template " + namespace + ":" + path, exception);
        }
    }

    private JsonObject single(String path, JsonArray processors) {
        JsonObject list = new JsonObject();
        list.add("processors", processors);
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", namespace + ":" + path);
        element.add("processors", list);
        element.addProperty("projection", "rigid");
        return element;
    }

    private JsonArray guard() {
        JsonArray rules = new JsonArray(3);
        rules.add(rule(location("minecraft:tag_match", "tag", "minecraft:air"), CAVE_AIR));
        rules.add(rule(location("minecraft:block_match", "block", WATER.name()), WATER));
        rules.add(rule(location("minecraft:block_match", "block", LAVA.name()), LAVA));
        JsonObject processor = new JsonObject();
        processor.addProperty("processor_type", "minecraft:rule");
        processor.add("rules", rules);
        JsonArray processors = new JsonArray(1);
        processors.add(processor);
        return processors;
    }

    private JsonObject rule(JsonObject location, VanillaBlockState output) {
        JsonObject input = new JsonObject();
        input.addProperty("predicate_type", "minecraft:always_true");
        JsonObject rule = new JsonObject();
        rule.add("input_predicate", input);
        rule.add("location_predicate", location);
        rule.add("output_state", syntax.blockState(output));
        return rule;
    }

    private static JsonObject location(String type, String key, String value) {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("predicate_type", type);
        predicate.addProperty(key, value);
        return predicate;
    }

    private static JsonObject startHeight(BakedFeature baked) {
        JsonObject height = new JsonObject();
        height.addProperty("type", "minecraft:uniform");
        height.add("min_inclusive", absolute(baked.lowestAnchorY() + GROUND_LEVEL_DELTA));
        height.add("max_inclusive", absolute(baked.highestAnchorY() + GROUND_LEVEL_DELTA));
        return height;
    }

    private static JsonObject absolute(int y) {
        JsonObject anchor = new JsonObject();
        anchor.addProperty("absolute", y);
        return anchor;
    }

    record Compiled(List<JsonResource> json, List<BinaryResource> templates) {
        Compiled {
            json = List.copyOf(json);
            templates = List.copyOf(templates);
        }
    }
}
