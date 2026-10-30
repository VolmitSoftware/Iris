package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.compile.decoration.Placements;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.structure.object.IrisObjectLimit;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlacementCompiler {
    public static final int MAX_FEATURE_WIDTH = 32;
    private static final int TILE_WIDTH = 17;
    private static final int WINDOW_REACH = 16;
    private static final int CAVE_SCAN_STEPS = 32;
    private static final int CAVE_FLOOR_MARGIN = 8;
    private static final int MIN_ANCHOR = -2032;
    private static final int MAX_ANCHOR = 2031;
    private static final long WEIGHT_CEILING = 1_000_000L;
    private static final List<String> QUARTER_NAMES = List.of("none", "counterclockwise_90", "180", "clockwise_90");
    private static final List<String> FLUIDS = List.of("minecraft:water", "minecraft:flowing_water", "minecraft:lava", "minecraft:flowing_lava");
    private static final Set<ObjectPlaceMode> STILT_MODES = Set.of(ObjectPlaceMode.STILT, ObjectPlaceMode.FAST_STILT,
            ObjectPlaceMode.MIN_STILT, ObjectPlaceMode.FAST_MIN_STILT, ObjectPlaceMode.CENTER_STILT,
            ObjectPlaceMode.ERODE_STILT, ObjectPlaceMode.ORGANIC_STILT);
    private static final Set<ObjectPlaceMode> VACUUM_MODES = Set.of(ObjectPlaceMode.VACUUM, ObjectPlaceMode.VACUUM_HIGH,
            ObjectPlaceMode.VACUUM_FAST, ObjectPlaceMode.VACUUM_ORGANIC, ObjectPlaceMode.VACUUM_WAVY);

    private final String namespace;
    private final FeatureJson json;
    private final DimensionDef dimension;
    private final WideObjectStructures wide;

    public PlacementCompiler(String namespace, ExportCapabilities capabilities, DimensionDef dimension) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "placement namespace");
        json = new FeatureJson(capabilities);
        this.dimension = dimension;
        wide = new WideObjectStructures(namespace);
    }

    public static boolean stilted(ObjectPlaceMode mode) {
        return STILT_MODES.contains(mode);
    }

    public CompiledPlacement compile(PlacementInput input, List<ExportDiagnostic> diagnostics) {
        IrisObjectPlacement placement = input.placement();
        GenerationStep step = input.tree() ? GenerationStep.VEGETAL_DECORATION : GenerationStep.SURFACE_STRUCTURES;
        int reach = Math.max(Math.abs(placement.getTranslate().getX()), Math.abs(placement.getTranslate().getZ()));
        List<ObjectTemplates> narrow = new ArrayList<>();
        List<ObjectTemplates> wideObjects = new ArrayList<>();
        for (ObjectTemplates object : input.objects()) {
            if (object.choices().isEmpty()) {
                continue;
            }
            if (object.footprint() + 2 * reach <= MAX_FEATURE_WIDTH) {
                narrow.add(object);
            } else {
                wideObjects.add(object);
            }
        }
        int total = narrow.size() + wideObjects.size();
        List<String> placed = new ArrayList<>(1);
        List<String> structures = new ArrayList<>(1);
        List<JsonResource> resources = new ArrayList<>();
        if (total == 0) {
            return new CompiledPlacement(step, placed, structures, resources);
        }
        double chance = Math.max(0, Math.min(1, placement.getChance()));
        int density = Math.max(1, placement.getDensity());
        if (VACUUM_MODES.contains(placement.getMode())) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.OBJECT_VACUUM, input.name(),
                    "Vacuum terrain shaping is not exported; the object places at its center height"));
        }
        if (!narrow.isEmpty()) {
            compileNarrow(input, narrow, chance, density, (double) narrow.size() / total, resources, placed);
        }
        if (!wideObjects.isEmpty()) {
            List<TemplateChoice> templates = weighted(wideObjects);
            double perChunk = chance * density * wideObjects.size() / total;
            int startY = placement.getTranslate().getY() - 1 + lowestBase(templates);
            WideObjectStructures.Compiled compiled = wide.compile(input.name(), templates, perChunk, startY, processorList(input.processors()));
            resources.addAll(compiled.resources());
            structures.add(compiled.structureId());
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.WIDE_OBJECT, input.name(),
                    wideObjects.size() + " objects wider than " + MAX_FEATURE_WIDTH + " blocks export as single-piece structures"));
        }
        return new CompiledPlacement(step, placed, structures, resources);
    }

    private void compileNarrow(PlacementInput input, List<ObjectTemplates> narrow, double chance, int density, double share,
                               List<JsonResource> resources, List<String> placed) {
        IrisObjectPlacement placement = input.placement();
        List<TemplateChoice> templates = weighted(narrow);
        boolean paint = placement.getMode() == ObjectPlaceMode.PAINT && input.anchor() == PlacementInput.Anchor.SURFACE;
        List<JsonObject> processors = new ArrayList<>(input.processors());
        if (paint) {
            processors.add(gravity(placement));
        }
        JsonObject feature = template(templates, processors);
        if (stilted(placement.getMode())) {
            feature = stilts(feature, templates, input.stiltBases());
        }
        String name = input.name();
        String featurePath = "object/" + name + "_" + ContentIds.hash(feature);
        resources.add(new JsonResource("data/" + namespace + "/" + json.syntax().featureFolder() + "/" + featurePath + ".json", feature));
        JsonObject placedFeature = new JsonObject();
        placedFeature.addProperty("feature", namespace + ":" + featurePath);
        placedFeature.add("placement", modifiers(input, templates, chance, density, share, paint));
        String placedPath = "object/" + name + "_" + ContentIds.hash(placedFeature);
        resources.add(new JsonResource("data/" + namespace + "/worldgen/placed_feature/" + placedPath + ".json", placedFeature));
        placed.add(namespace + ":" + placedPath);
    }

    private JsonArray modifiers(PlacementInput input, List<TemplateChoice> templates, double chance, int density, double share, boolean paint) {
        IrisObjectPlacement placement = input.placement();
        int translateX = placement.getTranslate().getX();
        int translateY = placement.getTranslate().getY();
        int translateZ = placement.getTranslate().getZ();
        int yRandom = placement.getTranslate().getYRandom();
        int spreadLow = yRandom < 0 ? yRandom : 0;
        int spreadHigh = yRandom > 0 ? yRandom - 1 : yRandom < 0 ? -1 : 0;
        int base = lowestBase(templates);
        List<JsonObject> modifiers = new ArrayList<>(json.chanceAndCount(chance, density, share));
        modifiers.add(Placements.typed("minecraft:in_square"));
        if (input.anchor() == PlacementInput.Anchor.CAVE) {
            boolean ceiling = placement.getMode() == ObjectPlaceMode.CEILING_HANG;
            modifiers.add(caveRange());
            modifiers.add(environmentScan(ceiling));
            int lift = ceiling ? -tallest(templates) : 0;
            modifiers.addAll(json.offset(translateX, lift + translateY + base + spreadLow, lift + translateY + base + spreadHigh, translateZ));
        } else if (placement.getMode() == ObjectPlaceMode.FLOATING) {
            modifiers.add(fixedHeight(translateY + base + spreadLow, translateY + base + spreadHigh));
        } else {
            modifiers.add(Placements.heightmap(placement.isUnderwater() ? "OCEAN_FLOOR_WG" : "WORLD_SURFACE_WG"));
            if (!placement.isForcePlace() && placement.isRequireSurfaceSupport()) {
                modifiers.add(Placements.filter(support(templates, placement)));
            }
            if (paint) {
                modifiers.addAll(json.offset(translateX, 0, 0, translateZ));
            } else {
                modifiers.addAll(json.offset(translateX, translateY - 1 + base + spreadLow, translateY - 1 + base + spreadHigh, translateZ));
            }
        }
        JsonObject clamp = clamp(placement.getClamp());
        if (clamp != null) {
            modifiers.add(Placements.filter(clamp));
        }
        modifiers.add(Placements.typed("minecraft:biome"));
        JsonArray array = new JsonArray(modifiers.size());
        for (JsonObject modifier : modifiers) {
            array.add(modifier);
        }
        return array;
    }

    private JsonObject template(List<TemplateChoice> templates, List<JsonObject> processors) {
        JsonArray entries = new JsonArray(templates.size());
        for (TemplateChoice template : templates) {
            JsonObject data = new JsonObject();
            data.addProperty("id", template.variant().id());
            if (template.quarters().size() < QUARTER_NAMES.size()) {
                JsonArray rotations = new JsonArray(template.quarters().size());
                for (int quarter : template.quarters()) {
                    rotations.add(QUARTER_NAMES.get(quarter));
                }
                data.add("rotations", rotations);
            }
            JsonObject entry = new JsonObject();
            entry.add("data", data);
            entry.addProperty("weight", template.weight());
            entries.add(entry);
        }
        JsonObject fields = new JsonObject();
        fields.add("templates", entries);
        if (!processors.isEmpty()) {
            fields.add("processors", processorList(processors));
        }
        return json.syntax().feature("minecraft:template", fields);
    }

    private JsonObject stilts(JsonObject template, List<TemplateChoice> templates, List<VanillaBlockState> bases) {
        JsonArray features = new JsonArray();
        JsonObject anchored = new JsonObject();
        anchored.add("feature", template);
        anchored.add("placement", new JsonArray());
        features.add(anchored);
        int footprint = 1;
        for (TemplateChoice choice : templates) {
            footprint = Math.max(footprint, choice.variant().volume().footprint());
        }
        int start = -(footprint / 2);
        int end = footprint - 1 - footprint / 2;
        int tiles = (int) Math.ceil(footprint / (double) TILE_WIDTH);
        int width = (int) Math.ceil(footprint / (double) tiles);
        int size = Math.max(1, width - 1);
        JsonObject canReplace = canReplace();
        JsonObject pillar = pillar(bases, canReplace);
        JsonObject gate = Placements.allOf(List.of(Placements.not(Placements.air(0, 1, 0)), canReplace));
        for (int tileX = 0; tileX < tiles; tileX++) {
            for (int tileZ = 0; tileZ < tiles; tileZ++) {
                int x = Math.min(start + tileX * width, end - size);
                int z = Math.min(start + tileZ * width, end - size);
                JsonArray placement = new JsonArray(3);
                JsonObject offset = Placements.typed("minecraft:offset");
                offset.addProperty("x", x);
                offset.addProperty("y", -2);
                offset.addProperty("z", z);
                placement.add(offset);
                JsonObject cuboid = Placements.typed("minecraft:cuboid");
                cuboid.addProperty("xz_size", size);
                cuboid.addProperty("y_size", 1);
                placement.add(cuboid);
                placement.add(Placements.filter(gate));
                JsonObject tile = new JsonObject();
                tile.add("feature", pillar);
                tile.add("placement", placement);
                features.add(tile);
            }
        }
        JsonObject fields = new JsonObject();
        fields.add("features", features);
        return json.syntax().feature("minecraft:overlay", fields);
    }

    private JsonObject pillar(List<VanillaBlockState> bases, JsonObject canReplace) {
        JsonObject provider;
        if (bases.isEmpty()) {
            VanillaBlockState fill = VanillaBlockState.parse(dimension == null ? "minecraft:stone" : dimension.defaultBlock());
            provider = json.syntax().provider(List.of(new WeightedState(fill, 1)));
        } else {
            JsonArray rules = new JsonArray(bases.size());
            for (VanillaBlockState base : bases) {
                JsonObject match = Placements.typed("minecraft:matching_blocks");
                match.addProperty("blocks", base.name());
                match.add("offset", FeatureJson.vector(0, 1, 0));
                JsonObject rule = new JsonObject();
                rule.add("if_true", match);
                rule.add("then", json.syntax().provider(List.of(new WeightedState(base, 1))));
                rules.add(rule);
            }
            provider = new JsonObject();
            provider.addProperty("type", "minecraft:rule_based");
            provider.add("fallback", json.syntax().provider(List.of(new WeightedState(bases.getFirst(), 1))));
            provider.add("rules", rules);
        }
        JsonObject fields = new JsonObject();
        fields.add("block", provider);
        fields.add("can_replace", canReplace);
        fields.addProperty("direction", "down");
        return json.syntax().feature("minecraft:single_block_pillar", fields);
    }

    private static JsonObject canReplace() {
        JsonObject fluids = Placements.typed("minecraft:matching_fluids");
        JsonArray list = new JsonArray(FLUIDS.size());
        for (String fluid : FLUIDS) {
            list.add(fluid);
        }
        fluids.add("fluids", list);
        return Placements.anyOf(List.of(Placements.air(0, 0, 0), fluids, Placements.typed("minecraft:replaceable")));
    }

    private static JsonObject gravity(IrisObjectPlacement placement) {
        JsonObject gravity = new JsonObject();
        gravity.addProperty("processor_type", "minecraft:gravity");
        gravity.addProperty("heightmap", placement.isUnderwater() ? "OCEAN_FLOOR_WG" : "WORLD_SURFACE_WG");
        gravity.addProperty("offset", placement.getTranslate().getY() - 1);
        return gravity;
    }

    private static JsonObject processorList(List<JsonObject> processors) {
        JsonArray list = new JsonArray(processors.size());
        for (JsonObject processor : processors) {
            list.add(processor.deepCopy());
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("processors", list);
        return wrapper;
    }

    private static JsonObject support(List<TemplateChoice> templates, IrisObjectPlacement placement) {
        int half = Integer.MAX_VALUE;
        for (TemplateChoice template : templates) {
            half = Math.min(half, Math.min(template.variant().volume().sizeX(), template.variant().volume().sizeZ()) / 2);
        }
        int reach = Math.min(WINDOW_REACH, Math.max(0, half + Math.max(0, placement.getSurfaceSupportBuffer())));
        int depth = Math.max(1, placement.getSurfaceSupportDepth());
        JsonObject predicate = Placements.typed("minecraft:volume_match");
        predicate.add("match", Placements.typed("minecraft:solid"));
        predicate.add("max", FeatureJson.vector(reach, -1, reach));
        predicate.add("min", FeatureJson.vector(-reach, -depth, -reach));
        return predicate;
    }

    private JsonObject clamp(IrisObjectLimit limit) {
        if (limit == null) {
            return null;
        }
        int bottom = dimension == null ? MIN_ANCHOR : dimension.minY();
        int top = dimension == null ? MAX_ANCHOR : dimension.minY() + dimension.height() - 1;
        int min = Math.max(MIN_ANCHOR, limit.getMinimumHeight());
        int max = Math.min(MAX_ANCHOR, limit.getMaximumHeight());
        if (min <= bottom && max >= top) {
            return null;
        }
        JsonObject predicate = Placements.typed("minecraft:height_range");
        predicate.add("max_inclusive", FeatureJson.absolute(Math.max(min, max)));
        predicate.add("min_inclusive", FeatureJson.absolute(Math.min(min, max)));
        return predicate;
    }

    private JsonObject caveRange() {
        int floor = dimension == null ? 0 : dimension.minY() + CAVE_FLOOR_MARGIN;
        int ceiling = dimension == null ? 63 : dimension.fluidHeight();
        JsonObject height = new JsonObject();
        height.addProperty("type", "minecraft:uniform");
        height.add("max_inclusive", FeatureJson.absolute(Math.max(floor, ceiling)));
        height.add("min_inclusive", FeatureJson.absolute(Math.min(floor, ceiling)));
        JsonObject modifier = Placements.typed("minecraft:height_range");
        modifier.add("height", height);
        return modifier;
    }

    private static JsonObject environmentScan(boolean ceiling) {
        JsonObject scan = Placements.typed("minecraft:environment_scan");
        scan.add("allowed_search_condition", Placements.air(0, 0, 0));
        scan.addProperty("direction_of_search", ceiling ? "up" : "down");
        scan.addProperty("max_steps", CAVE_SCAN_STEPS);
        scan.add("target_condition", Placements.typed("minecraft:solid"));
        return scan;
    }

    private static JsonObject fixedHeight(int low, int high) {
        JsonObject modifier = Placements.typed("minecraft:height_range");
        int clampedLow = Math.max(MIN_ANCHOR, Math.min(MAX_ANCHOR, low));
        int clampedHigh = Math.max(MIN_ANCHOR, Math.min(MAX_ANCHOR, high));
        if (clampedLow == clampedHigh) {
            modifier.add("height", FeatureJson.absolute(clampedLow));
            return modifier;
        }
        JsonObject height = new JsonObject();
        height.addProperty("type", "minecraft:uniform");
        height.add("max_inclusive", FeatureJson.absolute(clampedHigh));
        height.add("min_inclusive", FeatureJson.absolute(clampedLow));
        modifier.add("height", height);
        return modifier;
    }

    static List<TemplateChoice> weighted(List<ObjectTemplates> objects) {
        BigInteger common = BigInteger.ONE;
        for (ObjectTemplates object : objects) {
            BigInteger total = BigInteger.valueOf(object.totalWeight());
            common = common.multiply(total).divide(common.gcd(total));
        }
        boolean exact = common.compareTo(BigInteger.valueOf(WEIGHT_CEILING)) <= 0;
        List<TemplateChoice> weighted = new ArrayList<>();
        for (ObjectTemplates object : objects) {
            long total = object.totalWeight();
            for (TemplateChoice choice : object.choices()) {
                long weight = exact
                        ? common.longValue() / total * choice.weight()
                        : Math.max(1, Math.round(WEIGHT_CEILING * (double) choice.weight() / total));
                weighted.add(new TemplateChoice(choice.variant(), choice.quarters(), weight));
            }
        }
        return weighted;
    }

    private static int lowestBase(List<TemplateChoice> templates) {
        int base = 0;
        for (TemplateChoice template : templates) {
            base = Math.min(base, template.variant().baseY());
        }
        return base;
    }

    private static int tallest(List<TemplateChoice> templates) {
        int tallest = 1;
        for (TemplateChoice template : templates) {
            tallest = Math.max(tallest, template.variant().volume().sizeY());
        }
        return tallest;
    }
}
