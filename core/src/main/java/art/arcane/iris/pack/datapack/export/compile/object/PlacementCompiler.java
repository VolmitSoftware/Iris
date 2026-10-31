package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class PlacementCompiler {
    public static final int MAX_FEATURE_WIDTH = 32;
    private static final int TILE_WIDTH = 17;
    private static final int WINDOW_REACH = 16;
    private static final int MAX_SUPPORT_DROP = 16;
    private static final int CAVE_SCAN_STEPS = 32;
    private static final int CAVE_FLOOR_MARGIN = 8;
    private static final int MIN_ANCHOR = -2032;
    private static final int MAX_ANCHOR = 2031;
    private static final int DEFAULT_FLUID_HEIGHT = 63;
    private static final long WEIGHT_CEILING = 1_000_000L;
    private static final long ROTATION_SHARES = 12L;
    private static final List<String> QUARTER_NAMES = List.of("none", "counterclockwise_90", "180", "clockwise_90");
    static final List<String> FLUIDS = List.of("minecraft:water", "minecraft:flowing_water", "minecraft:lava", "minecraft:flowing_lava");
    private static final Set<ObjectPlaceMode> STILT_MODES = Set.of(ObjectPlaceMode.STILT, ObjectPlaceMode.FAST_STILT,
            ObjectPlaceMode.MIN_STILT, ObjectPlaceMode.FAST_MIN_STILT, ObjectPlaceMode.CENTER_STILT,
            ObjectPlaceMode.ERODE_STILT, ObjectPlaceMode.ORGANIC_STILT);
    private static final Set<ObjectPlaceMode> VACUUM_MODES = Set.of(ObjectPlaceMode.VACUUM, ObjectPlaceMode.VACUUM_HIGH,
            ObjectPlaceMode.VACUUM_FAST, ObjectPlaceMode.VACUUM_ORGANIC, ObjectPlaceMode.VACUUM_WAVY);

    private final String namespace;
    private final FeatureJson json;
    private final DimensionDef dimension;
    private final WideObjectStructures wide;
    private final RootExtension roots;

    public PlacementCompiler(String namespace, ExportCapabilities capabilities, DimensionDef dimension) {
        this.namespace = VanillaResourceIdentifier.requireNamespace(namespace, "placement namespace");
        json = new FeatureJson(capabilities);
        this.dimension = dimension;
        wide = new WideObjectStructures(namespace);
        roots = new RootExtension(this.namespace, json);
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
        for (List<ObjectTemplates> group : depthGroups(input, narrow)) {
            compileNarrow(input, group, chance, density, (double) group.size() / total, resources, placed);
        }
        if (!narrow.isEmpty() && slopeLimited(input)) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.SLOPE, input.name(),
                    "Object slope conditions measure slope over 3 blocks in the four cardinal directions"));
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
        HeightAnchor.Sampling sampling = input.anchor() == PlacementInput.Anchor.SURFACE && !paint
                && placement.getMode() != ObjectPlaceMode.FLOATING ? HeightAnchor.sampling(placement.getMode()) : HeightAnchor.Sampling.CENTER;
        boolean grounded = grounds(input) && rooted(templates);
        boolean branched = grounded || sampling != HeightAnchor.Sampling.CENTER;
        JsonObject feature;
        if (branched) {
            feature = branches(input, templates, processors, sampling, grounded, resources);
        } else {
            feature = template(templates, processors);
            if (stilted(placement.getMode())) {
                feature = stilts(feature, templates, input.stiltBases());
            }
        }
        String name = input.name();
        String featurePath = "object/" + name + "_" + ContentIds.hash(feature);
        resources.add(new JsonResource("data/" + namespace + "/" + json.syntax().featureFolder() + "/" + featurePath + ".json", feature));
        JsonObject placedFeature = new JsonObject();
        placedFeature.addProperty("feature", namespace + ":" + featurePath);
        placedFeature.add("placement", modifiers(input, templates, chance, density, share, paint, branched));
        String placedPath = "object/" + name + "_" + ContentIds.hash(placedFeature);
        resources.add(new JsonResource("data/" + namespace + "/worldgen/placed_feature/" + placedPath + ".json", placedFeature));
        placed.add(namespace + ":" + placedPath);
    }

    private JsonArray modifiers(PlacementInput input, List<TemplateChoice> templates, double chance, int density, double share, boolean paint,
                                boolean branched) {
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
            boolean underwater = placement.isUnderwater();
            boolean onwater = onwater(placement);
            modifiers.add(Placements.heightmap(onwater ? Placements.WORLD_SURFACE : Placements.OCEAN_FLOOR));
            modifiers.add(Placements.filter(onwater ? Placements.water(0, -1, 0) : ground(underwater)));
            if (underwater) {
                modifiers.add(Placements.filter(belowSurface(fluidCeiling(templates, translateY))));
            }
            if (!underwater && !onwater && !placement.isForcePlace() && placement.isRequireSurfaceSupport() && !stilted(placement.getMode())) {
                JsonObject support = support(templates, placement);
                if (support != null) {
                    modifiers.add(Placements.filter(support));
                }
            }
            if (slopeLimited(input)) {
                for (JsonObject limit : Placements.slopeLimits(placement.getSlopeCondition())) {
                    modifiers.add(Placements.filter(limit));
                }
            }
            if (paint) {
                modifiers.addAll(json.offset(translateX, 0, 0, translateZ));
            } else if (!branched) {
                modifiers.addAll(surfaceOffset(placement, base));
            }
        }
        JsonObject clamp = clamp(placement.getClamp());
        if (clamp != null && !branched) {
            modifiers.add(Placements.filter(clamp));
        }
        modifiers.add(Placements.typed("minecraft:biome"));
        JsonArray array = new JsonArray(modifiers.size());
        for (JsonObject modifier : modifiers) {
            array.add(modifier);
        }
        return array;
    }

    private List<JsonObject> surfaceOffset(IrisObjectPlacement placement, int base) {
        int yRandom = placement.getTranslate().getYRandom();
        int spreadLow = yRandom < 0 ? yRandom : 0;
        int spreadHigh = yRandom > 0 ? yRandom - 1 : yRandom < 0 ? -1 : 0;
        int lift = placement.getTranslate().getY() - 1 + base;
        return json.offset(placement.getTranslate().getX(), lift + spreadLow, lift + spreadHigh, placement.getTranslate().getZ());
    }

    private JsonObject branches(PlacementInput input, List<TemplateChoice> templates, List<JsonObject> processors,
                                HeightAnchor.Sampling sampling, boolean grounded, List<JsonResource> resources) {
        IrisObjectPlacement placement = input.placement();
        Map<String, JsonResource> shared = new TreeMap<>();
        Branching branching = new Branching(input, processors, sampling, grounded, surfaceOffset(placement, lowestBase(templates)),
                clamp(placement.getClamp()), shared);
        List<JsonObject> branches = new ArrayList<>();
        List<Long> weights = new ArrayList<>();
        for (TemplateChoice choice : templates) {
            long weight = grounded ? choice.weight() * (ROTATION_SHARES / choice.quarters().size()) : choice.weight();
            List<List<Integer>> groups = new ArrayList<>(choice.quarters().size());
            if (grounded) {
                for (int quarter : choice.quarters()) {
                    groups.add(List.of(quarter));
                }
            } else {
                groups.add(choice.quarters());
            }
            for (List<Integer> quarters : groups) {
                branches.add(branch(branching, choice.variant(), quarters));
                weights.add(weight);
            }
        }
        resources.addAll(shared.values());
        long divisor = 0;
        for (long weight : weights) {
            divisor = BigInteger.valueOf(divisor).gcd(BigInteger.valueOf(weight)).longValue();
        }
        JsonArray entries = new JsonArray(branches.size());
        for (int index = 0; index < branches.size(); index++) {
            JsonObject entry = new JsonObject();
            entry.add("data", branches.get(index));
            entry.addProperty("weight", weights.get(index) / divisor);
            entries.add(entry);
        }
        JsonObject fields = new JsonObject();
        fields.add("features", entries);
        return json.syntax().feature("minecraft:weighted_random_selector", fields);
    }

    private JsonObject branch(Branching branching, BakedVariant variant, List<Integer> quarters) {
        IrisObjectPlacement placement = branching.input().placement();
        TemplateChoice single = new TemplateChoice(variant, quarters, 1);
        JsonObject feature = template(List.of(single), branching.processors());
        List<JsonObject> modifiers = new ArrayList<>();
        if (branching.sampling() != HeightAnchor.Sampling.CENTER) {
            modifiers.add(HeightAnchor.scan(branching.sampling(), HeightAnchor.fast(placement.getMode()), placement.isUnderwater(),
                    variant.volume(), quarters, new TemplateColumn(placement.getTranslate().getX(), placement.getTranslate().getZ())));
        }
        modifiers.addAll(branching.offset());
        if (branching.grounded() && variant.roots().present()) {
            int quarter = quarters.getFirst();
            feature = overlay(feature, roots.pillars(variant.roots(), variant.volume(), quarter, branching.shared()));
            JsonObject gap = RootExtension.gap(variant.roots(), variant.volume(), quarter);
            if (gap != null) {
                modifiers.add(Placements.filter(gap));
            }
        } else if (stilted(placement.getMode())) {
            feature = stilts(feature, List.of(single), branching.input().stiltBases());
        }
        if (branching.clamp() != null) {
            modifiers.add(Placements.filter(branching.clamp().deepCopy()));
        }
        JsonObject branch = new JsonObject();
        branch.add("feature", feature);
        branch.add("placement", array(modifiers));
        return branch;
    }

    private JsonObject overlay(JsonObject template, List<JsonObject> parts) {
        JsonArray features = new JsonArray(parts.size() + 1);
        JsonObject anchored = new JsonObject();
        anchored.add("feature", template);
        anchored.add("placement", new JsonArray());
        features.add(anchored);
        for (JsonObject part : parts) {
            features.add(part);
        }
        JsonObject fields = new JsonObject();
        fields.add("features", features);
        return json.syntax().feature("minecraft:overlay", fields);
    }

    private static boolean grounds(PlacementInput input) {
        IrisObjectPlacement placement = input.placement();
        ObjectPlaceMode mode = placement.getMode();
        return input.anchor() == PlacementInput.Anchor.SURFACE && !placement.isForcePlace() && !placement.isFromBottom()
                && mode != ObjectPlaceMode.PAINT && mode != ObjectPlaceMode.FLOATING && mode != ObjectPlaceMode.CEILING_HANG
                && mode != ObjectPlaceMode.STRUCTURE_PIECE;
    }

    private static boolean rooted(List<TemplateChoice> templates) {
        for (TemplateChoice template : templates) {
            if (template.variant().roots().present()) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray array(List<JsonObject> values) {
        JsonArray array = new JsonArray(values.size());
        for (JsonObject value : values) {
            array.add(value);
        }
        return array;
    }

    private static boolean slopeLimited(PlacementInput input) {
        IrisObjectPlacement placement = input.placement();
        return input.anchor() == PlacementInput.Anchor.SURFACE && placement.getMode() != ObjectPlaceMode.FLOATING
                && !placement.isForcePlace() && !Placements.slopeLimits(placement.getSlopeCondition()).isEmpty();
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

    static JsonObject canReplace() {
        return Placements.anyOf(List.of(Placements.air(0, 0, 0), Placements.fluids(FLUIDS, 0, 0, 0), Placements.typed("minecraft:replaceable")));
    }

    private static JsonObject ground(boolean underwater) {
        List<JsonObject> rules = new ArrayList<>(4);
        rules.add(Placements.solid(0, -1, 0));
        rules.add(Placements.not(Placements.blockTag("minecraft:leaves", 0, -1, 0)));
        rules.add(Placements.not(Placements.blockTag("minecraft:logs", 0, -1, 0)));
        rules.add(underwater ? Placements.water(0, 0, 0) : Placements.not(Placements.fluids(FLUIDS, 0, 0, 0)));
        return Placements.allOf(rules);
    }

    private static boolean onwater(IrisObjectPlacement placement) {
        return placement.isOnwater() && !placement.isUnderwater();
    }

    private List<List<ObjectTemplates>> depthGroups(PlacementInput input, List<ObjectTemplates> narrow) {
        IrisObjectPlacement placement = input.placement();
        if (narrow.isEmpty() || !placement.isUnderwater() || input.anchor() == PlacementInput.Anchor.CAVE
                || placement.getMode() == ObjectPlaceMode.FLOATING) {
            return narrow.isEmpty() ? List.of() : List.of(narrow);
        }
        Map<Integer, List<ObjectTemplates>> groups = new TreeMap<>();
        for (ObjectTemplates object : narrow) {
            groups.computeIfAbsent(fluidCeiling(object.choices(), placement.getTranslate().getY()), ignored -> new ArrayList<>()).add(object);
        }
        return new ArrayList<>(groups.values());
    }

    private int fluidCeiling(List<TemplateChoice> templates, int translateY) {
        int reach = 0;
        for (TemplateChoice template : templates) {
            reach = Math.max(reach, 3 * (rows(template.variant().volume()) / 2));
        }
        return (dimension == null ? DEFAULT_FLUID_HEIGHT : dimension.fluidHeight()) - reach - translateY;
    }

    private JsonObject belowSurface(int ceiling) {
        int bottom = dimension == null ? MIN_ANCHOR : dimension.minY();
        JsonObject predicate = Placements.typed("minecraft:height_range");
        predicate.add("max_inclusive", FeatureJson.absolute(Math.max(MIN_ANCHOR, Math.min(MAX_ANCHOR, ceiling))));
        predicate.add("min_inclusive", FeatureJson.absolute(Math.max(MIN_ANCHOR, Math.min(bottom, ceiling))));
        return predicate;
    }

    private static int rows(TemplateVolume volume) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (TemplateBlock block : volume.blocks()) {
            low = Math.min(low, block.y());
            high = Math.max(high, block.y());
        }
        return low > high ? 1 : high - low + 1;
    }

    private static JsonObject gravity(IrisObjectPlacement placement) {
        JsonObject gravity = new JsonObject();
        gravity.addProperty("processor_type", "minecraft:gravity");
        gravity.addProperty("heightmap", onwater(placement) ? Placements.WORLD_SURFACE : Placements.OCEAN_FLOOR);
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
        int minX = 0;
        int maxX = 0;
        int minZ = 0;
        int maxZ = 0;
        for (TemplateChoice template : templates) {
            TemplateVolume volume = template.variant().volume();
            int base = baseRow(volume);
            for (TemplateBlock block : volume.blocks()) {
                if (block.y() != base || foliage(block.state())) {
                    continue;
                }
                int x = block.x() - volume.sizeX() / 2;
                int z = block.z() - volume.sizeZ() / 2;
                for (int quarter : template.quarters()) {
                    int rotatedX = switch (quarter) {
                        case 1 -> z;
                        case 2 -> -x;
                        case 3 -> -z;
                        default -> x;
                    };
                    int rotatedZ = switch (quarter) {
                        case 1 -> -x;
                        case 2 -> -z;
                        case 3 -> x;
                        default -> z;
                    };
                    minX = Math.min(minX, rotatedX);
                    maxX = Math.max(maxX, rotatedX);
                    minZ = Math.min(minZ, rotatedZ);
                    maxZ = Math.max(maxZ, rotatedZ);
                }
            }
        }
        int buffer = Math.max(0, placement.getSurfaceSupportBuffer());
        int thickness = Math.max(1, Math.min(MAX_SUPPORT_DROP, placement.getSurfaceSupportDepth()));
        int[] xs = {reach(minX - buffer), reach(maxX + buffer)};
        int[] zs = {reach(minZ - buffer), reach(maxZ + buffer)};
        Set<Long> seen = new LinkedHashSet<>();
        List<JsonObject> corners = new ArrayList<>(4);
        for (int x : xs) {
            for (int z : zs) {
                if ((x == 0 && z == 0) || !seen.add(((long) x << 32) | (z & 0xFFFFFFFFL))) {
                    continue;
                }
                corners.add(groundBelow(x, z, thickness));
            }
        }
        return corners.isEmpty() ? null : Placements.allOf(corners);
    }

    private static JsonObject groundBelow(int x, int z, int thickness) {
        List<JsonObject> tops = new ArrayList<>(MAX_SUPPORT_DROP - thickness + 1);
        for (int top = 1; top + thickness - 1 <= MAX_SUPPORT_DROP; top++) {
            if (thickness == 1) {
                tops.add(Placements.solid(x, -top, z));
                continue;
            }
            List<JsonObject> layers = new ArrayList<>(thickness);
            for (int layer = 0; layer < thickness; layer++) {
                layers.add(Placements.solid(x, -(top + layer), z));
            }
            tops.add(Placements.allOf(layers));
        }
        return Placements.anyOf(tops);
    }

    private static int baseRow(TemplateVolume volume) {
        int base = Integer.MAX_VALUE;
        for (TemplateBlock block : volume.blocks()) {
            if (!foliage(block.state())) {
                base = Math.min(base, block.y());
            }
        }
        return base;
    }

    private static boolean foliage(VanillaBlockState state) {
        return state.name().endsWith("_leaves");
    }

    private static int reach(int offset) {
        return Math.max(-WINDOW_REACH, Math.min(WINDOW_REACH, offset));
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
        int ceiling = dimension == null ? DEFAULT_FLUID_HEIGHT : dimension.fluidHeight();
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

    private record Branching(PlacementInput input, List<JsonObject> processors, HeightAnchor.Sampling sampling, boolean grounded,
                             List<JsonObject> offset, JsonObject clamp, Map<String, JsonResource> shared) {
    }
}
