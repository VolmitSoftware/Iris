package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MaterialProgramCompiler {
    public static final int ORE_VEIN_BUDGET = 8;
    public static final String WALL_SHELL = "cave/wall_shell";
    private static final int WALL_MATERIALS = 3;
    private static final int LOCK_PHASE = 512;
    private static final String PALETTE_SCOPE = "palette";
    private static final String DATA_PREFIX = "data/";
    private static final String DENSITY_FUNCTION_FOLDER = "/worldgen/density_function/";
    private static final String JSON = ".json";
    private static final VanillaBlockState AIR = VanillaBlockState.parse("minecraft:air");
    private static final Map<String, String> SUBMERGED_SOIL = Map.of(
            "minecraft:grass_block", "minecraft:dirt",
            "minecraft:mycelium", "minecraft:dirt",
            "minecraft:dirt_path", "minecraft:dirt",
            "minecraft:farmland", "minecraft:dirt",
            "minecraft:crimson_nylium", "minecraft:netherrack",
            "minecraft:warped_nylium", "minecraft:netherrack");
    private static final Comparator<WeightedState> HEAVIEST_FIRST = Comparator.comparingInt(WeightedState::weight).reversed()
            .thenComparing(entry -> entry.state().canonical());
    private static final Comparator<Similarity> MOST_SIMILAR = Comparator.comparingDouble(Similarity::blocks).thenComparingDouble(Similarity::materials);
    private static final Set<String> FORM_WORDS = Set.of("block", "powder");

    private final PackCompileContext context;
    private final SurfacePalettes palettes;
    private final DimensionDef dimension;
    private final List<ExportDiagnostic> diagnostics;
    private final Set<String> reported = new HashSet<>();

    public MaterialProgramCompiler(PackCompileContext context, BlockStateResolver blocks) {
        this.context = context;
        this.palettes = new SurfacePalettes(context.namespace(), context.noises(), blocks, context.capabilities().target());
        this.dimension = context.dimensionDef();
        this.diagnostics = context.diagnostics();
    }

    public MaterialProgram compile() {
        Plan plan = plan(ExportedBiomes.load(context));
        if (!plan.walls().isEmpty()) {
            context.jsonResources().add(new JsonResource(DATA_PREFIX + context.namespace() + DENSITY_FUNCTION_FOLDER + WALL_SHELL + JSON, wallShell()));
        }
        return new MaterialProgram(render(plan, WorldgenSyntax.forTarget(context.capabilities().target())));
    }

    private Plan plan(ExportedBiomes biomes) {
        Map<Stack, List<String>> floors = new LinkedHashMap<>();
        Map<Stack, List<String>> ceilings = new LinkedHashMap<>();
        Map<List<OreRule>, List<String>> biomeSurfaceOres = new LinkedHashMap<>();
        Map<List<OreRule>, List<String>> biomeUndergroundOres = new LinkedHashMap<>();
        Map<List<OreRule>, List<String>> regionSurfaceOres = new LinkedHashMap<>();
        Map<List<OreRule>, List<String>> regionUndergroundOres = new LinkedHashMap<>();
        for (String biomeId : biomes.inCodeOrder(biomes.byId().keySet())) {
            IrisBiome biome = biomes.byId().get(biomeId);
            String resource = biomes.resource(biomeId);
            group(floors, floorStack(biome, resource), biomeId);
            group(ceilings, layerStack(biome.getCaveCeilingLayers(), false, resource, biome), biomeId);
            group(biomeSurfaceOres, ores(biome.getOres(), true, resource), biomeId);
            group(biomeUndergroundOres, ores(biome.getOres(), false, resource), biomeId);
            IrisRegion region = biomes.region(biomeId);
            if (region != null) {
                String regionResource = "regions/" + region.getLoadKey();
                group(regionSurfaceOres, ores(region.getOres(), true, regionResource), biomeId);
                group(regionUndergroundOres, ores(region.getOres(), false, regionResource), biomeId);
            }
            if (biome.getSeaLayers() != null && !biome.getSeaLayers().isEmpty()) {
                report(ExportDiagnostic.Severity.INFO, ExportLimit.SEA_LAYER, resource, "Sea layers paint inside the water column and are not exported");
            }
        }
        String dimensionResource = "dimensions/" + context.dimension().getLoadKey();
        List<OreGroup> surfaceOres = oreGroups(biomeSurfaceOres, regionSurfaceOres, ores(context.dimension().getOres(), true, dimensionResource));
        List<OreGroup> undergroundOres = oreGroups(biomeUndergroundOres, regionUndergroundOres, ores(context.dimension().getOres(), false, dimensionResource));
        VanillaBlockState bedrock = context.dimension().isBedrock() ? VanillaBlockState.parse("minecraft:bedrock") : null;
        return new Plan(bedrock, surfaceOres, stackGroups(floors), stackGroups(ceilings), wallShells(biomes), undergroundOres);
    }

    private Stack floorStack(IrisBiome biome, String resource) {
        if (biome.isLockLayers()) {
            return lockStack(biome, resource);
        }
        return layerStack(biome.getLayers(), true, resource, biome);
    }

    private Stack layerStack(List<IrisBiomePaletteLayer> layers, boolean floor, String resource, IrisBiome biome) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        List<LayerBand> bands = new ArrayList<>(layers.size());
        int cumulativeMin = 0;
        int cumulativeRange = 0;
        boolean variable = false;
        for (int index = 0; index < layers.size(); index++) {
            IrisBiomePaletteLayer layer = layers.get(index);
            if (layer == null || (floor && !flatValid(layer))) {
                continue;
            }
            int min = Math.max(0, layer.getMinHeight());
            int max = Math.max(min, layer.getMaxHeight());
            if (max <= 0) {
                continue;
            }
            Optional<SurfacePalette> palette = palettes.palette(layer.getPalette(), layer.getStyle(), layer.getZoom(), index, resource);
            if (palette.isEmpty()) {
                continue;
            }
            cumulativeMin += min;
            cumulativeRange += max - min;
            variable |= min != max;
            SurfacePalette submerged = floor && bands.isEmpty() ? submerged(palette.get()) : null;
            bands.add(new LayerBand(cumulativeMin - 1, cumulativeRange, palette.get(), submerged));
        }
        if (variable) {
            report(ExportDiagnostic.Severity.INFO, ExportLimit.LAYER_THICKNESS, resource,
                    "Layer thickness in biome '" + biome.getLoadKey() + "' follows Minecraft's surface noise, so its layers vary together");
        }
        return bands.isEmpty() ? null : new LayerStack(bands, floor);
    }

    private Stack lockStack(IrisBiome biome, String resource) {
        List<SurfacePalette> layerPalettes = new ArrayList<>();
        List<Integer> thicknesses = new ArrayList<>();
        boolean variable = false;
        for (int index = 0; index < biome.getLayers().size(); index++) {
            IrisBiomePaletteLayer layer = biome.getLayers().get(index);
            if (layer == null || !flatValid(layer)) {
                continue;
            }
            int min = Math.max(0, layer.getMinHeight());
            int max = Math.max(min, layer.getMaxHeight());
            int thickness = (int) Math.round((min + max) / 2.0);
            Optional<SurfacePalette> palette = palettes.palette(layer.getPalette(), layer.getStyle(), layer.getZoom(), index, resource);
            if (thickness <= 0 || palette.isEmpty()) {
                continue;
            }
            variable |= min != max;
            layerPalettes.add(palette.get());
            thicknesses.add(thickness);
        }
        int cycle = 0;
        for (int thickness : thicknesses) {
            cycle += thickness;
        }
        if (cycle == 0) {
            return null;
        }
        if (variable) {
            report(ExportDiagnostic.Severity.INFO, ExportLimit.LAYER_THICKNESS, resource,
                    "Locked layer bands in biome '" + biome.getLoadKey() + "' use each layer's mean thickness");
        }
        List<LockBand> bands = new ArrayList<>();
        int current = -1;
        int top = dimension.minY() + dimension.height() - 1;
        for (int y = top; y >= dimension.minY(); y--) {
            int layer = lockLayer(thicknesses, Math.floorMod(LOCK_PHASE - (y - dimension.minY()) - 1, cycle));
            if (layer != current && current >= 0) {
                bands.add(new LockBand(y + 1, layerPalettes.get(current)));
            }
            current = layer;
        }
        bands.add(new LockBand(dimension.minY(), layerPalettes.get(current)));
        return new LockStack(Math.max(1, biome.getLockLayersMax()), bands);
    }

    private List<OreRule> ores(List<IrisOreGenerator> generators, boolean surface, String resource) {
        List<OreRule> ores = new ArrayList<>();
        if (generators == null) {
            return ores;
        }
        for (int index = 0; index < generators.size(); index++) {
            IrisOreGenerator generator = generators.get(index);
            if (generator == null || generator.isGenerateSurface() != surface || generator.getThreshold() <= 0 || generator.getRange() == null) {
                continue;
            }
            IrisMaterialPalette source = generator.getPalette();
            if (source == null) {
                continue;
            }
            Optional<SurfacePalette> palette = palettes.palette(source.getPalette(), source.getStyle(), source.getZoom(), index, resource);
            int minY = dimension.minY() + (int) Math.ceil(generator.getRange().getMin());
            int maxY = dimension.minY() + (int) Math.floor(generator.getRange().getMax());
            if (palette.isEmpty() || maxY < minY) {
                continue;
            }
            SurfacePalettes.OreChance chance = palettes.chance(Math.min(1.0, generator.getThreshold()), generator.getChanceStyle(), 1.0, index);
            ores.add(new OreRule(minY, maxY, chance, palette.get()));
        }
        return ores;
    }

    private List<WallShell> wallShells(ExportedBiomes biomes) {
        if (context.caveField().isEmpty()) {
            return List.of();
        }
        Map<List<WeightedState>, List<String>> groups = new LinkedHashMap<>();
        for (String biomeId : biomes.inCodeOrder(biomes.byId().keySet())) {
            IrisBiome biome = biomes.byId().get(biomeId);
            IrisBiomePaletteLayer wall = biome.getWall();
            if (wall == null || wall.getPalette() == null || wall.getPalette().isEmpty()) {
                continue;
            }
            List<WeightedState> materials = wallMaterials(palettes.resolve(wall.getPalette(), biomes.resource(biomeId)));
            if (materials.isEmpty()) {
                continue;
            }
            if (materials.size() > WALL_MATERIALS) {
                materials = List.copyOf(materials.subList(0, WALL_MATERIALS));
                report(ExportDiagnostic.Severity.INFO, ExportLimit.WALL_PALETTE, biomes.resource(biomeId),
                        "The wall palette keeps its " + WALL_MATERIALS + " most common materials");
            }
            groups.computeIfAbsent(materials, ignored -> new ArrayList<>()).add(biomeId);
        }
        List<Map.Entry<List<WeightedState>, List<String>>> ranked = new ArrayList<>(groups.entrySet());
        ranked.sort(Comparator.comparingInt((Map.Entry<List<WeightedState>, List<String>> entry) -> entry.getValue().size()).reversed());
        int budget = ORE_VEIN_BUDGET - deepCaveRules();
        List<WallShell> kept = new ArrayList<>(Math.min(budget, ranked.size()));
        for (int index = 0; index < ranked.size(); index++) {
            Map.Entry<List<WeightedState>, List<String>> group = ranked.get(index);
            if (index < budget) {
                kept.add(new WallShell(group.getKey(), new ArrayList<>(group.getValue())));
                continue;
            }
            WallShell nearest = nearest(kept, group.getKey());
            for (String biomeId : group.getValue()) {
                nearest.biomeIds().add(biomeId);
                report(ExportDiagnostic.Severity.WARNING, ExportLimit.WALL_PALETTE, biomes.resource(biomeId),
                        "The wall palette exceeds the " + budget + " wall palettes per dimension and uses the nearest kept palette (" + names(nearest.materials()) + ")");
            }
        }
        List<WallShell> shells = new ArrayList<>(kept.size());
        for (WallShell shell : kept) {
            shells.add(new WallShell(shell.materials(), biomes.inCodeOrder(shell.biomeIds())));
        }
        return shells;
    }

    private JsonObject render(Plan plan, WorldgenSyntax syntax) {
        List<JsonObject> rules = new ArrayList<>();
        context.deepCaves().ifPresent(opening -> rules.add(deepCaveRule(opening, syntax)));
        if (plan.bedrock() != null) {
            JsonObject floor = MaterialRules.verticalGradient(context.namespace() + ":surface/bedrock", dimension.minY(), dimension.minY() + 1);
            rules.add(MaterialRules.condition(floor, MaterialRules.block(syntax, plan.bedrock())));
        }
        int oreIndex = renderOres(plan.surfaceOres(), syntax, 0, rules);
        if (!plan.floors().isEmpty()) {
            rules.add(MaterialRules.condition(MaterialRules.stoneDepth(depth(plan.floors()) - 1, 0, false), dispatch(plan.floors(), syntax)));
        }
        if (!plan.ceilings().isEmpty()) {
            rules.add(MaterialRules.condition(MaterialRules.stoneDepth(depth(plan.ceilings()) - 1, 0, true), dispatch(plan.ceilings(), syntax)));
        }
        for (WallShell shell : plan.walls()) {
            rules.add(MaterialRules.condition(MaterialRules.biome(shell.biomeIds()), wallRule(shell, syntax)));
        }
        renderOres(plan.undergroundOres(), syntax, oreIndex, rules);
        return MaterialRules.sequence(rules);
    }

    private JsonObject dispatch(List<StackGroup> groups, WorldgenSyntax syntax) {
        int chunk = Math.max(1, (int) Math.ceil(Math.sqrt(groups.size())));
        List<JsonObject> outer = new ArrayList<>();
        for (int start = 0; start < groups.size(); start += chunk) {
            List<StackGroup> slice = groups.subList(start, Math.min(groups.size(), start + chunk));
            List<JsonObject> inner = new ArrayList<>(slice.size());
            Set<String> union = new LinkedHashSet<>();
            for (StackGroup group : slice) {
                inner.add(MaterialRules.condition(MaterialRules.biome(group.biomeIds()), renderStack(group.stack(), syntax)));
                union.addAll(group.biomeIds());
            }
            if (slice.size() == 1 || slice.size() == groups.size()) {
                outer.addAll(inner);
            } else {
                outer.add(MaterialRules.condition(MaterialRules.biome(union), MaterialRules.sequence(inner)));
            }
        }
        return outer.size() == 1 ? outer.getFirst() : MaterialRules.sequence(outer);
    }

    private JsonObject renderStack(Stack stack, WorldgenSyntax syntax) {
        return switch (stack) {
            case LayerStack layers -> renderLayers(layers, syntax);
            case LockStack lock -> renderLock(lock, syntax);
        };
    }

    private JsonObject renderLayers(LayerStack stack, WorldgenSyntax syntax) {
        List<JsonObject> rules = new ArrayList<>(stack.bands().size());
        for (LayerBand band : stack.bands()) {
            JsonObject palette = palettes.render(band.palette(), syntax, PALETTE_SCOPE, dimension.fluidHeight());
            if (band.submerged() != null) {
                palette = MaterialRules.sequence(List.of(
                        MaterialRules.condition(MaterialRules.notUnderwater(), palette),
                        palettes.render(band.submerged(), syntax, PALETTE_SCOPE, dimension.fluidHeight())));
            }
            rules.add(MaterialRules.condition(MaterialRules.stoneDepth(band.offset(), band.secondaryRange(), !stack.floor()), palette));
        }
        return rules.size() == 1 ? rules.getFirst() : MaterialRules.sequence(rules);
    }

    private JsonObject renderLock(LockStack stack, WorldgenSyntax syntax) {
        List<JsonObject> rules = new ArrayList<>(stack.bands().size());
        for (LockBand band : stack.bands()) {
            JsonObject palette = palettes.render(band.palette(), syntax, PALETTE_SCOPE, dimension.fluidHeight());
            rules.add(MaterialRules.condition(MaterialRules.yAbove(band.fromY()), palette));
        }
        return MaterialRules.condition(MaterialRules.stoneDepth(stack.maxDepth() - 1, 0, false), MaterialRules.sequence(rules));
    }

    private int renderOres(List<OreGroup> groups, WorldgenSyntax syntax, int firstIndex, List<JsonObject> rules) {
        int oreIndex = firstIndex;
        for (OreGroup group : groups) {
            List<JsonObject> ores = new ArrayList<>(group.ores().size());
            for (OreRule ore : group.ores()) {
                int center = (ore.minY() + ore.maxY()) / 2;
                JsonObject palette = palettes.render(ore.palette(), syntax, PALETTE_SCOPE, center);
                JsonObject chance = palettes.renderChance(ore.chance(), "ore_" + oreIndex++, center, palette);
                List<JsonObject> band = new ArrayList<>(2);
                if (ore.minY() > dimension.minY()) {
                    band.add(MaterialRules.yAbove(ore.minY()));
                }
                if (ore.maxY() < dimension.minY() + dimension.height() - 1) {
                    band.add(MaterialRules.not(MaterialRules.yAbove(ore.maxY() + 1)));
                }
                ores.add(MaterialRules.guarded(band, chance));
            }
            JsonObject sequence = ores.size() == 1 ? ores.getFirst() : MaterialRules.sequence(ores);
            rules.add(group.biomeIds().isEmpty() ? sequence : MaterialRules.condition(MaterialRules.biome(group.biomeIds()), sequence));
        }
        return oreIndex;
    }

    private JsonObject wallRule(WallShell shell, WorldgenSyntax syntax) {
        List<WeightedState> materials = shell.materials();
        int total = 0;
        for (WeightedState material : materials) {
            total += material.weight();
        }
        VanillaBlockState filler = materials.getFirst().state();
        VanillaBlockState ore = materials.size() > 1 ? materials.get(1).state() : filler;
        VanillaBlockState raw = materials.size() > 2 ? materials.get(2).state() : ore;
        int oreWeight = materials.size() > 1 ? materials.get(1).weight() : 0;
        int rawWeight = materials.size() > 2 ? materials.get(2).weight() : 0;
        float richness = (float) (oreWeight + rawWeight) / total;
        float rawChance = oreWeight + rawWeight == 0 ? 0.0f : (float) rawWeight / (oreWeight + rawWeight);
        return MaterialRules.oreVein(syntax, new MaterialRules.OreVeinBlocks(filler, ore, raw, richness, rawChance),
                new JsonPrimitive(context.namespace() + ":" + WALL_SHELL));
    }

    private JsonObject wallShell() {
        JsonPrimitive caves = new JsonPrimitive(context.namespace() + ":" + context.caveField().orElseThrow().name());
        return MaterialRules.rangeChoice(caves, 0.0, context.wallShellBand());
    }

    private JsonObject deepCaveRule(Field.Shared opening, WorldgenSyntax syntax) {
        return MaterialRules.oreVein(syntax, new MaterialRules.OreVeinBlocks(AIR, AIR, AIR, 0.0f, 0.0f),
                new JsonPrimitive(context.namespace() + ":" + opening.name()));
    }

    private int deepCaveRules() {
        return context.deepCaves().isPresent() ? 1 : 0;
    }

    private List<OreGroup> oreGroups(Map<List<OreRule>, List<String>> biomeOres, Map<List<OreRule>, List<String>> regionOres, List<OreRule> dimensionOres) {
        List<OreGroup> groups = new ArrayList<>(biomeOres.size() + regionOres.size() + 1);
        for (Map.Entry<List<OreRule>, List<String>> entry : biomeOres.entrySet()) {
            groups.add(new OreGroup(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<List<OreRule>, List<String>> entry : regionOres.entrySet()) {
            groups.add(new OreGroup(entry.getKey(), entry.getValue()));
        }
        if (!dimensionOres.isEmpty()) {
            groups.add(new OreGroup(dimensionOres, List.of()));
        }
        return groups;
    }

    private SurfacePalette submerged(SurfacePalette palette) {
        boolean changed = false;
        List<WeightedState> entries = new ArrayList<>(palette.entries().size());
        for (WeightedState entry : palette.entries()) {
            String replacement = SUBMERGED_SOIL.get(entry.state().name());
            changed |= replacement != null;
            entries.add(replacement == null ? entry : new WeightedState(VanillaBlockState.parse(replacement), entry.weight()));
        }
        return changed ? new SurfacePalette(entries, palette.selection(), palette.noise(), palette.thresholds()) : null;
    }

    private void report(ExportDiagnostic.Severity severity, ExportLimit limit, String resource, String message) {
        if (!reported.add(limit + "|" + resource + "|" + message)) {
            return;
        }
        diagnostics.add(new ExportDiagnostic(severity, limit, resource, message));
    }

    private static List<StackGroup> stackGroups(Map<Stack, List<String>> grouped) {
        List<StackGroup> groups = new ArrayList<>(grouped.size());
        for (Map.Entry<Stack, List<String>> entry : grouped.entrySet()) {
            groups.add(new StackGroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return groups;
    }

    private static <K> void group(Map<K, List<String>> groups, K key, String biomeId) {
        if (key == null || (key instanceof List<?> list && list.isEmpty())) {
            return;
        }
        groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(biomeId);
    }

    private static int depth(List<StackGroup> groups) {
        int depth = 1;
        for (StackGroup group : groups) {
            depth = Math.max(depth, switch (group.stack()) {
                case LayerStack layers -> layers.bands().getLast().offset() + layers.bands().getLast().secondaryRange() + 1;
                case LockStack lock -> lock.maxDepth();
            });
        }
        return depth;
    }

    private static boolean flatValid(IrisBiomePaletteLayer layer) {
        return layer.getSlopeCondition() == null || layer.getSlopeCondition().isValid(0.0);
    }

    private static int lockLayer(List<Integer> thicknesses, int position) {
        int boundary = 0;
        for (int index = 0; index < thicknesses.size(); index++) {
            boundary += thicknesses.get(index);
            if (position < boundary) {
                return index;
            }
        }
        return thicknesses.size() - 1;
    }

    private static List<WeightedState> wallMaterials(List<WeightedState> entries) {
        Map<VanillaBlockState, Integer> weights = new LinkedHashMap<>();
        for (WeightedState entry : entries) {
            weights.merge(entry.state(), entry.weight(), Integer::sum);
        }
        List<WeightedState> materials = new ArrayList<>(weights.size());
        for (Map.Entry<VanillaBlockState, Integer> weight : weights.entrySet()) {
            materials.add(new WeightedState(weight.getKey(), weight.getValue()));
        }
        materials.sort(HEAVIEST_FIRST);
        return materials;
    }

    private static WallShell nearest(List<WallShell> kept, List<WeightedState> materials) {
        WallShell nearest = kept.getFirst();
        Similarity best = similarity(materials, nearest.materials());
        for (WallShell shell : kept) {
            Similarity candidate = similarity(materials, shell.materials());
            if (MOST_SIMILAR.compare(candidate, best) > 0) {
                best = candidate;
                nearest = shell;
            }
        }
        return nearest;
    }

    private static Similarity similarity(List<WeightedState> materials, List<WeightedState> candidate) {
        return new Similarity(overlap(shares(materials, false), shares(candidate, false)), overlap(shares(materials, true), shares(candidate, true)));
    }

    private static Map<String, Double> shares(List<WeightedState> materials, boolean byMaterial) {
        double total = 0.0;
        for (WeightedState material : materials) {
            total += material.weight();
        }
        Map<String, Double> shares = new HashMap<>(materials.size() * 2);
        for (WeightedState material : materials) {
            String name = material.state().name();
            shares.merge(byMaterial ? baseMaterial(name) : name, material.weight() / total, Double::sum);
        }
        return shares;
    }

    private static double overlap(Map<String, Double> first, Map<String, Double> second) {
        double overlap = 0.0;
        for (Map.Entry<String, Double> share : first.entrySet()) {
            overlap += Math.min(share.getValue(), second.getOrDefault(share.getKey(), 0.0));
        }
        return overlap;
    }

    private static String baseMaterial(String block) {
        List<String> words = List.of(block.substring(block.indexOf(':') + 1).split("_"));
        int last = words.size() - 1;
        while (last > 0 && FORM_WORDS.contains(words.get(last))) {
            last--;
        }
        return words.get(last);
    }

    private static String names(List<WeightedState> materials) {
        List<String> names = new ArrayList<>(materials.size());
        for (WeightedState material : materials) {
            names.add(material.state().canonical());
        }
        return String.join(", ", names);
    }

    private sealed interface Stack permits LayerStack, LockStack {
    }

    private record LayerStack(List<LayerBand> bands, boolean floor) implements Stack {
    }

    private record LockStack(int maxDepth, List<LockBand> bands) implements Stack {
    }

    private record LayerBand(int offset, int secondaryRange, SurfacePalette palette, SurfacePalette submerged) {
    }

    private record LockBand(int fromY, SurfacePalette palette) {
    }

    private record StackGroup(Stack stack, List<String> biomeIds) {
    }

    private record OreRule(int minY, int maxY, SurfacePalettes.OreChance chance, SurfacePalette palette) {
    }

    private record OreGroup(List<OreRule> ores, List<String> biomeIds) {
    }

    private record WallShell(List<WeightedState> materials, List<String> biomeIds) {
    }

    private record Similarity(double blocks, double materials) {
    }

    private record Plan(VanillaBlockState bedrock, List<OreGroup> surfaceOres, List<StackGroup> floors, List<StackGroup> ceilings,
                        List<WallShell> walls, List<OreGroup> undergroundOres) {
    }
}
