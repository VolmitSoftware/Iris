package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ObjectStage implements CompileStage {
    private static final String NAME = "objects";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(PackCompileContext context) {
        if (!context.objects()) {
            return;
        }
        new Run(context).compile();
    }

    private static final class Run {
        private static final int MAX_STILT_BASES = 16;
        private static final String TREE_FOLDER = "trees/";
        private static final String PROCEDURAL_FOLDER = "procedural/";
        private static final String CAVE_SUFFIX = "_cave";

        private final PackCompileContext context;
        private final String namespace;
        private final DataVersion target;
        private final IrisData data;
        private final List<ExportDiagnostic> diagnostics;
        private final FeatureJson json;
        private final ObjectVariantBaker baker;
        private final VariantPlanner planner;
        private final PlacementCompiler compiler;
        private final LootCompiler loot;
        private final WideObjectStructures wide;
        private final StructureTemplateEncoder encoder = new StructureTemplateEncoder();
        private final Map<String, ObjectSource> sources = new HashMap<>();
        private final Map<IrisProceduralPlacement, IrisObjectPlacement> proceduralPlacements = new IdentityHashMap<>();
        private final Map<String, BakedVariant> baked = new HashMap<>();
        private final Map<IrisObjectPlacement, VariantPlanner.Plan> plans = new IdentityHashMap<>();
        private final Map<IrisObjectPlacement, List<ObjectTemplates>> templates = new IdentityHashMap<>();
        private final Map<IrisObjectPlacement, Boolean> reported = new IdentityHashMap<>();
        private final Map<IrisObjectPlacement, Set<VanillaBlockState>> containers = new IdentityHashMap<>();
        private final Map<IrisObjectPlacement, List<VanillaBlockState>> stiltBases = new IdentityHashMap<>();
        private final Map<IrisObjectPlacement, Map<String, CompiledPlacement>> compiled = new IdentityHashMap<>();
        private final Map<String, JsonResource> jsonResources = new TreeMap<>();
        private final Map<String, BinaryResource> binaryResources = new TreeMap<>();
        private final Map<String, Set<String>> structureBiomes = new TreeMap<>();
        private int exported;

        private Run(PackCompileContext context) {
            this.context = context;
            namespace = context.namespace();
            target = context.capabilities().target();
            data = context.data();
            diagnostics = context.diagnostics();
            json = new FeatureJson(context.capabilities());
            baker = new ObjectVariantBaker();
            planner = new VariantPlanner(context.dimension(), data, preventLeafDecay());
            compiler = new PlacementCompiler(namespace, context.capabilities(), context.dimensionDef());
            loot = new LootCompiler(namespace, data);
            wide = new WideObjectStructures(namespace);
        }

        private void compile() {
            IrisDimension dimension = context.dimension();
            reportSpawners(dimension.getEntitySpawners(), "dimensions/" + dimension.getLoadKey());
            Map<String, IrisRegion> regions = regions(dimension);
            Set<String> defined = new TreeSet<>();
            for (BiomeDef biome : context.biomes()) {
                defined.add(namespace + ":" + biome.id());
            }
            for (Map.Entry<String, String> entry : context.biomeCodes().loadKeyByBiomeId().entrySet()) {
                IrisBiome biome = defined.contains(entry.getKey()) ? data.getBiomeLoader().load(entry.getValue(), false) : null;
                if (biome == null) {
                    continue;
                }
                reportSpawners(biome.getEntitySpawners(), "biomes/" + biome.getLoadKey());
                IrisRegion region = regions.get(entry.getValue());
                LootCompiler.LootScope scope = new LootCompiler.LootScope(dimension, region, biome);
                List<IrisObjectPlacement> placements = new ArrayList<>(biome.getObjects());
                placements.addAll(procedural(biome.getProceduralObjects()));
                if (region != null) {
                    placements.addAll(region.getObjects());
                    placements.addAll(procedural(region.getProceduralObjects()));
                }
                for (IrisObjectPlacement placement : placements) {
                    if (placement == null || placement.getPlace().isEmpty()) {
                        continue;
                    }
                    if (placement.getCarvingSupport().supportsSurface()) {
                        place(entry.getKey(), placement, PlacementInput.Anchor.SURFACE, scope);
                    }
                    if (placement.getCarvingSupport().supportsCarving()) {
                        place(entry.getKey(), placement, PlacementInput.Anchor.CAVE, scope);
                    }
                }
            }
            if (exported > 0) {
                diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.STRUCTURE_AVOIDANCE,
                        "dimensions/" + dimension.getLoadKey(), "Objects do not avoid Minecraft structures"));
            }
            emit();
        }

        private List<IrisObjectPlacement> procedural(IrisProceduralObjects objects) {
            if (objects == null || objects.isEmpty()) {
                return List.of();
            }
            List<IrisObjectPlacement> placements = new ArrayList<>();
            for (IrisProceduralPlacement placement : objects.getAllPlacements()) {
                placements.add(proceduralPlacements.computeIfAbsent(placement, this::procedural));
            }
            return placements;
        }

        private IrisObjectPlacement procedural(IrisProceduralPlacement source) {
            IrisObjectPlacement placement = source.asPlacement();
            String folder = (source instanceof IrisProceduralTree ? TREE_FOLDER : "") + PROCEDURAL_FOLDER + source.getName() + "-";
            KList<String> keys = new KList<>();
            for (IrisObject variant : source.getVariantObjects(data)) {
                ObjectSource object = ObjectSource.of(variant, variant.getLoadKey(), diagnostics);
                String key = folder + ContentIds.hash(object.volume() + ";base=" + object.baseY()
                        + (object.roots().present() ? ";roots=" + object.roots() : ""));
                sources.putIfAbsent(key, object);
                keys.add(key);
            }
            placement.setPlace(keys);
            return placement;
        }

        private void place(String biomeId, IrisObjectPlacement placement, PlacementInput.Anchor anchor, LootCompiler.LootScope scope) {
            String name = name(placement, anchor);
            VariantPlanner.Plan plan = plans.computeIfAbsent(placement, ignored -> planner.plan(placement, name, diagnostics));
            List<ObjectTemplates> objects = templates.computeIfAbsent(placement, ignored -> bake(placement, plan));
            if (objects.isEmpty()) {
                return;
            }
            reportMarkers(placement, name);
            Map<VanillaBlockState, String> bindings = bindings(placement, objects, scope);
            Map<String, CompiledPlacement> byBinding = compiled.computeIfAbsent(placement, ignored -> new HashMap<>());
            String key = anchor + "|" + bindings;
            CompiledPlacement result = byBinding.get(key);
            if (result == null) {
                result = compile(placement, anchor, plan, objects, bindings, name);
                byBinding.put(key, result);
            }
            for (String placed : result.placedFeatures()) {
                context.features().add(biomeId, result.step(), placed);
            }
            for (String structure : result.structures()) {
                structureBiomes.computeIfAbsent(structure, ignored -> new TreeSet<>()).add(biomeId);
            }
            exported++;
        }

        private CompiledPlacement compile(IrisObjectPlacement placement, PlacementInput.Anchor anchor, VariantPlanner.Plan plan,
                                          List<ObjectTemplates> objects, Map<VanillaBlockState, String> bindings, String name) {
            List<JsonObject> processors = new ArrayList<>();
            for (ObjectEdit edit : plan.processorEdits()) {
                processors.add(editProcessor(edit));
            }
            if (!bindings.isEmpty()) {
                processors.add(loot.appendLoot(bindings, json));
            }
            List<VanillaBlockState> bases = PlacementCompiler.stilted(placement.getMode())
                    ? stiltBases.computeIfAbsent(placement, ignored -> stiltBases(objects))
                    : List.of();
            PlacementInput input = new PlacementInput(placement, anchor, tree(placement), objects, processors, bases, name);
            CompiledPlacement result = compiler.compile(input, diagnostics);
            for (JsonResource resource : result.resources()) {
                addJson(resource);
            }
            for (ObjectTemplates object : objects) {
                for (TemplateChoice choice : object.choices()) {
                    addTemplate(choice.variant());
                }
            }
            return result;
        }

        private List<ObjectTemplates> bake(IrisObjectPlacement placement, VariantPlanner.Plan plan) {
            List<String> keys = new ArrayList<>();
            List<List<TemplateChoice>> choices = new ArrayList<>();
            List<BakedVariant> all = new ArrayList<>();
            for (String objectKey : placement.getPlace()) {
                ObjectSource source = source(objectKey);
                if (source == null) {
                    continue;
                }
                String objectPath = VanillaResourceIdentifier.sanitizePath(objectKey);
                List<TemplateChoice> objectChoices = new ArrayList<>(plan.variants().size());
                for (VariantPlanner.PlannedVariant planned : plan.variants()) {
                    String id = ObjectVariantBaker.templateId(namespace, objectPath, planned.spec(), target);
                    BakedVariant variant = baked.computeIfAbsent(id,
                            ignored -> baker.bake(id, source, planned.spec()));
                    objectChoices.add(new TemplateChoice(variant, planned.quarters(), planned.weight()));
                    all.add(variant);
                }
                keys.add(objectKey);
                choices.add(objectChoices);
            }
            Map<String, BakedVariant> aligned = new HashMap<>();
            List<BakedVariant> lifted = ObjectVariantBaker.alignBases(all);
            for (int index = 0; index < all.size(); index++) {
                aligned.put(all.get(index).id(), lifted.get(index));
            }
            List<ObjectTemplates> objects = new ArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++) {
                List<TemplateChoice> alignedChoices = new ArrayList<>();
                for (TemplateChoice choice : choices.get(index)) {
                    alignedChoices.add(new TemplateChoice(aligned.get(choice.variant().id()), choice.quarters(), choice.weight()));
                }
                objects.add(new ObjectTemplates(keys.get(index), alignedChoices));
            }
            return objects;
        }

        private ObjectSource source(String objectKey) {
            if (sources.containsKey(objectKey)) {
                return sources.get(objectKey);
            }
            IrisObject object = data.getObjectLoader().load(objectKey, false);
            ObjectSource source = object == null ? null : ObjectSource.of(object, "objects/" + objectKey, diagnostics);
            sources.put(objectKey, source);
            return source;
        }

        private Map<VanillaBlockState, String> bindings(IrisObjectPlacement placement, List<ObjectTemplates> objects, LootCompiler.LootScope scope) {
            Set<VanillaBlockState> found = containers.computeIfAbsent(placement, ignored -> containers(objects));
            Map<String, String> byBlock = new TreeMap<>();
            Map<VanillaBlockState, String> bindings = new LinkedHashMap<>();
            for (VanillaBlockState container : found) {
                String table = byBlock.computeIfAbsent(container.name(),
                        block -> loot.binding(placement, block, scope, diagnostics).orElse(""));
                if (!table.isEmpty()) {
                    bindings.put(container, table);
                }
            }
            return bindings;
        }

        private static Set<VanillaBlockState> containers(List<ObjectTemplates> objects) {
            Set<VanillaBlockState> found = new TreeSet<>((first, second) -> first.canonical().compareTo(second.canonical()));
            for (ObjectTemplates object : objects) {
                for (TemplateChoice choice : object.choices()) {
                    for (TemplateBlock block : choice.variant().volume().blocks()) {
                        if (LootCompiler.container(block.state())) {
                            found.add(block.state());
                        }
                    }
                }
            }
            return found;
        }

        private JsonObject editProcessor(ObjectEdit edit) {
            JsonArray rules = new JsonArray();
            int total = edit.totalWeight();
            for (VanillaBlockState find : edit.find()) {
                double consumed = 0;
                for (WeightedState replacement : edit.replace()) {
                    double share = edit.chance() * replacement.weight() / total;
                    double probability = consumed >= 1 ? 1 : Math.min(1, share / (1 - consumed));
                    consumed += share;
                    JsonObject input = new JsonObject();
                    if (edit.exact()) {
                        input.addProperty("predicate_type", "minecraft:random_blockstate_match");
                        input.add("block_state", json.syntax().blockState(find));
                    } else {
                        input.addProperty("predicate_type", "minecraft:random_block_match");
                        input.addProperty("block", find.name());
                    }
                    input.addProperty("probability", (float) probability);
                    JsonObject location = new JsonObject();
                    location.addProperty("predicate_type", "minecraft:always_true");
                    JsonObject rule = new JsonObject();
                    rule.add("input_predicate", input);
                    rule.add("location_predicate", location);
                    rule.add("output_state", json.syntax().blockState(replacement.state()));
                    rules.add(rule);
                }
            }
            JsonObject processor = new JsonObject();
            processor.addProperty("processor_type", "minecraft:rule");
            processor.add("rules", rules);
            return processor;
        }

        private static List<VanillaBlockState> stiltBases(List<ObjectTemplates> objects) {
            Map<String, Integer> counts = new TreeMap<>();
            Map<String, VanillaBlockState> states = new HashMap<>();
            for (ObjectTemplates object : objects) {
                for (TemplateChoice choice : object.choices()) {
                    int lowest = Integer.MAX_VALUE;
                    for (TemplateBlock block : choice.variant().volume().blocks()) {
                        lowest = Math.min(lowest, block.y());
                    }
                    for (TemplateBlock block : choice.variant().volume().blocks()) {
                        if (block.y() == lowest) {
                            counts.merge(block.state().name(), 1, Integer::sum);
                            states.putIfAbsent(block.state().name(), block.state());
                        }
                    }
                }
            }
            List<String> names = new ArrayList<>(counts.keySet());
            names.sort((first, second) -> Integer.compare(counts.get(second), counts.get(first)));
            List<VanillaBlockState> bases = new ArrayList<>(Math.min(MAX_STILT_BASES, names.size()));
            for (String name : names.subList(0, Math.min(MAX_STILT_BASES, names.size()))) {
                bases.add(states.get(name));
            }
            return bases;
        }

        private Map<String, IrisRegion> regions(IrisDimension dimension) {
            Map<String, IrisRegion> regions = new HashMap<>();
            for (String regionKey : dimension.getRegions()) {
                IrisRegion region = data.getRegionLoader().load(regionKey, false);
                if (region == null) {
                    continue;
                }
                reportSpawners(region.getEntitySpawners(), "regions/" + region.getLoadKey());
                List<String> roots = new ArrayList<>();
                roots.addAll(region.getLandBiomes());
                roots.addAll(region.getSeaBiomes());
                roots.addAll(region.getShoreBiomes());
                roots.addAll(region.getCaveBiomes());
                for (String biome : members(roots)) {
                    regions.putIfAbsent(biome, region);
                }
            }
            return regions;
        }

        private Set<String> members(List<String> roots) {
            Set<String> members = new TreeSet<>();
            List<String> pending = new ArrayList<>(roots);
            while (!pending.isEmpty()) {
                String key = pending.removeLast();
                if (key == null || !members.add(key)) {
                    continue;
                }
                IrisBiome biome = data.getBiomeLoader().load(key, false);
                if (biome != null) {
                    pending.addAll(biome.getChildren());
                }
            }
            return members;
        }

        private void reportMarkers(IrisObjectPlacement placement, String name) {
            if (placement.getMarkers().isEmpty() || reported.put(placement, Boolean.TRUE) != null) {
                return;
            }
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.CUSTOM_ENTITY, name,
                    placement.getMarkers().size() + " object markers are not exported"));
        }

        private void reportSpawners(List<String> spawners, String resource) {
            if (spawners == null || spawners.isEmpty()) {
                return;
            }
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.CUSTOM_ENTITY, resource,
                    spawners.size() + " entity spawner rules are not exported"));
        }

        private void addTemplate(BakedVariant variant) {
            String path = "data/" + namespace + "/structure/" + variant.id().substring(namespace.length() + 1) + ".nbt";
            if (binaryResources.containsKey(path)) {
                return;
            }
            try {
                binaryResources.put(path, new BinaryResource(path, encoder.encode(variant.volume(), target)));
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not encode object template " + variant.id(), exception);
            }
        }

        private void addJson(JsonResource resource) {
            JsonResource existing = jsonResources.putIfAbsent(resource.path(), resource);
            if (existing != null && !existing.json().equals(resource.json())) {
                throw new IllegalStateException("Object export produced two different resources at " + resource.path());
            }
        }

        private void emit() {
            for (JsonResource resource : loot.resources()) {
                addJson(resource);
            }
            for (Map.Entry<String, Set<String>> structure : structureBiomes.entrySet()) {
                addJson(wide.biomeTag(structure.getKey(), structure.getValue()));
            }
            context.jsonResources().addAll(jsonResources.values());
            context.binaryResources().addAll(binaryResources.values());
        }

        private String name(IrisObjectPlacement placement, PlacementInput.Anchor anchor) {
            String first = Objects.requireNonNull(placement.getPlace().getFirst(), "Object placement key");
            String path = VanillaResourceIdentifier.sanitizePath(first);
            String leaf = path.substring(path.lastIndexOf('/') + 1);
            return anchor == PlacementInput.Anchor.CAVE ? leaf + CAVE_SUFFIX : leaf;
        }

        private static boolean tree(IrisObjectPlacement placement) {
            if (placement.getTrees() != null && !placement.getTrees().isEmpty()) {
                return true;
            }
            for (String key : placement.getPlace()) {
                if (key != null && key.startsWith(TREE_FOLDER)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean preventLeafDecay() {
            IrisSettings settings = IrisSettings.settings;
            return settings == null || settings.getGenerator().preventLeafDecay;
        }
    }
}
