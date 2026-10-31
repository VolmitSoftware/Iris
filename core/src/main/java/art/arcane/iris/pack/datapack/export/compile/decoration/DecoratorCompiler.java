package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.surface.RepaintFeatureCompiler;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class DecoratorCompiler {
    private static final int COLUMNS = 256;
    private static final double CAVE_AIR_SHARE = 0.08;
    private static final int CAVE_SCAN_STEPS = 12;
    private static final int MAX_ATTEMPTS = 256;
    private static final int MAX_PATTERN_STATES = 64;
    private static final VanillaBlockState AIR_STATE = new VanillaBlockState("minecraft:air", Map.of());
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> AQUATIC = Set.of("minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp",
            "minecraft:kelp_plant", "minecraft:sea_pickle");
    private static final String STYLE_MESSAGE = "Decorators gated by a noise style keep the style's coverage, with its pattern fitted to Minecraft noise";
    private static final String STYLE_STACK_MESSAGE = "Stacked, forced, underwater, and ceiling decorators gated by a noise style keep the style's coverage without its pattern";
    private static final String VARIANCE_MESSAGE = "Decorator palettes varied by a noise style pick their blocks at random";
    private static final String STACK_MESSAGE = "Decorator stacks scaled to cave height use their fixed stack range";
    private static final String CAVE_MESSAGE = "Cave ceiling decorators place at an estimated density because features cannot enumerate every cave ceiling";

    private final PackCompileContext context;
    private final ExportedBiomes biomes;
    private final BlockStateResolver blocks;
    private final FeatureCatalog catalog;
    private final WorldgenSyntax syntax;
    private final Map<CoverageKey, DecoratorCoverage.Coverage> coverages = new HashMap<>();
    private final Map<DecoratorCoverage.Coverage, DecoratorCoverage.Pattern> patterns = new HashMap<>();

    public DecoratorCompiler(PackCompileContext context, ExportedBiomes biomes, BlockStateResolver blocks, FeatureCatalog catalog) {
        this.context = context;
        this.biomes = biomes;
        this.blocks = blocks;
        this.catalog = catalog;
        this.syntax = catalog.syntax();
    }

    public void compile() {
        for (Map.Entry<String, IrisBiome> entry : biomes.byId().entrySet()) {
            compileBiome(entry.getKey(), entry.getValue());
        }
    }

    static double[] columnShares(double[] chances) {
        double[] shares = new double[chances.length];
        for (int index = 0; index < chances.length; index++) {
            double[] others = new double[chances.length];
            others[0] = 1.0;
            int size = 1;
            for (int other = 0; other < chances.length; other++) {
                if (other == index) {
                    continue;
                }
                double chance = chances[other];
                for (int count = size; count >= 0; count--) {
                    double stays = others[count] * (1.0 - chance);
                    double grows = count > 0 ? others[count - 1] * chance : 0.0;
                    others[count] = stays + grows;
                }
                size++;
            }
            double expectation = 0.0;
            for (int count = 0; count < size; count++) {
                expectation += others[count] / (count + 1);
            }
            shares[index] = chances[index] * expectation;
        }
        return shares;
    }

    static double[] sequentialChances(double[] shares) {
        double[] chances = new double[shares.length];
        double claimed = 0.0;
        for (int index = 0; index < shares.length; index++) {
            double free = 1.0 - claimed;
            chances[index] = free <= 0.0 ? 0.0 : Math.min(1.0, shares[index] / free);
            claimed += shares[index];
        }
        return chances;
    }

    private void compileBiome(String biomeId, IrisBiome biome) {
        if (biome.getDecorators() == null || biome.getDecorators().isEmpty()) {
            return;
        }
        String resource = biomes.resource(biomeId);
        Map<IrisDecorationPart, List<Candidate>> buckets = new EnumMap<>(IrisDecorationPart.class);
        for (IrisDecorator decorator : biome.getDecorators()) {
            if (decorator == null) {
                continue;
            }
            Optional<Candidate> candidate = candidate(decorator, resource);
            IrisDecorationPart part = decorator.getPartOf() == null ? IrisDecorationPart.NONE : decorator.getPartOf();
            candidate.ifPresent(value -> buckets.computeIfAbsent(part, ignored -> new ArrayList<>()).add(value));
        }
        for (Map.Entry<IrisDecorationPart, List<Candidate>> bucket : buckets.entrySet()) {
            List<Candidate> candidates = bucket.getValue();
            double[] chances = new double[candidates.size()];
            for (int index = 0; index < chances.length; index++) {
                chances[index] = candidates.get(index).coverage().share();
            }
            double[] shares = columnShares(chances);
            for (int index = 0; index < shares.length; index++) {
                shares[index] *= 1.0 - candidates.get(index).airShare();
            }
            double[] sequential = sequentialChances(shares);
            for (int index = 0; index < candidates.size(); index++) {
                Candidate candidate = candidates.get(index);
                if (candidate.entries().isEmpty() || shares[index] <= 0.0) {
                    continue;
                }
                if (bucket.getKey() == IrisDecorationPart.CEILING) {
                    report(resource, ExportLimit.CAVE_DECORATOR, CAVE_MESSAGE);
                    if (candidate.coverage().patterned()) {
                        report(resource, ExportLimit.DECORATOR_STYLE, STYLE_STACK_MESSAGE);
                    }
                    context.features().add(biomeId, GenerationStep.UNDERGROUND_DECORATION, ceiling(candidate, shares[index]));
                } else {
                    context.features().add(biomeId, GenerationStep.VEGETAL_DECORATION, column(bucket.getKey(), candidate, sequential[index], resource));
                }
            }
        }
    }

    private Optional<Candidate> candidate(IrisDecorator decorator, String resource) {
        List<WeightedState> entries = new ArrayList<>();
        int airWeight = 0;
        int total = 0;
        for (WeightedState entry : weighted(decorator.getPalette(), decorator.getVariance(), resource)) {
            total += entry.weight();
            if (AIR.contains(entry.state().name())) {
                airWeight += entry.weight();
            } else {
                entries.add(entry);
            }
        }
        if (total == 0) {
            return Optional.empty();
        }
        if (entries.size() > 1 && !isStatic(decorator.getVariance())) {
            report(resource, ExportLimit.DECORATOR_STYLE, VARIANCE_MESSAGE);
        }
        if (decorator.isScaleStack()) {
            report(resource, ExportLimit.SCALE_STACK, STACK_MESSAGE);
        }
        if (decorator.getSlopeCondition() != null && !decorator.getSlopeCondition().isDefault()) {
            report(resource, ExportLimit.SLOPE, RepaintFeatureCompiler.SLOPE_MESSAGE);
        }
        List<WeightedState> top = new ArrayList<>();
        for (WeightedState entry : weighted(decorator.getTopPalette(), decorator.getVariance(), resource)) {
            if (!AIR.contains(entry.state().name())) {
                top.add(entry);
            }
        }
        VanillaBlockState force = decorator.getForceBlock() == null ? null : blocks.resolve(decorator.getForceBlock(), resource).orElse(null);
        return Optional.of(new Candidate(decorator, entries, top, (double) airWeight / total, force, aquatic(entries, top),
                names(decorator.getWhitelist(), resource), names(decorator.getBlacklist(), resource), coverage(decorator)));
    }

    private List<WeightedState> weighted(List<IrisBlockData> palette, IrisGeneratorStyle variance, String resource) {
        List<WeightedState> resolved = new ArrayList<>(palette.size());
        int count = 0;
        for (IrisBlockData block : palette) {
            Optional<VanillaBlockState> state = blocks.resolve(block, resource);
            if (state.isPresent()) {
                int weight = Math.max(1, block.getWeight());
                resolved.add(new WeightedState(state.get(), weight));
                count += weight;
            }
        }
        if (count <= 1 || !isStatic(variance)) {
            return resolved;
        }
        List<WeightedState> rounded = new ArrayList<>(resolved.size());
        int start = 0;
        for (WeightedState entry : resolved) {
            int end = start + entry.weight();
            rounded.add(new WeightedState(entry.state(), 2 * entry.weight() - (start == 0 ? 1 : 0) - (end == count ? 1 : 0)));
            start = end;
        }
        return rounded;
    }

    private String column(IrisDecorationPart part, Candidate candidate, double sequential, String resource) {
        IrisDecorationPart target = candidate.aquatic() && part == IrisDecorationPart.NONE ? IrisDecorationPart.SEA_FLOOR : part;
        boolean underwater = target == IrisDecorationPart.SEA_FLOOR;
        boolean patterned = candidate.coverage().patterned() && !underwater && single(candidate);
        if (candidate.coverage().patterned()) {
            report(resource, ExportLimit.DECORATOR_STYLE, patterned ? STYLE_MESSAGE : STYLE_STACK_MESSAGE);
        }
        String featureId = patterned ? patterned(candidate) : feature(candidate, false, underwater);
        double chance = patterned ? Math.min(1.0, sequential / candidate.coverage().share()) : sequential;
        JsonArray placement = new JsonArray();
        for (JsonObject modifier : Placements.everyColumn()) {
            placement.add(modifier);
        }
        placement.add(Placements.heightmap(underwater ? Placements.OCEAN_FLOOR : Placements.WORLD_SURFACE));
        if (chance < 1.0) {
            placement.add(Placements.randomChance(chance));
        }
        placement.add(Placements.biome());
        placement.add(Placements.filter(Placements.allOf(predicates(target, candidate))));
        return catalog.placed(name(candidate), featureId, placement);
    }

    private String ceiling(Candidate candidate, double share) {
        String featureId = feature(candidate, true, false);
        JsonArray placement = new JsonArray();
        placement.add(Placements.count(Placements.meanCount(Math.min(MAX_ATTEMPTS, COLUMNS * share / CAVE_AIR_SHARE))));
        placement.add(Placements.inSquare());
        placement.add(Placements.heightRange(Placements.fullHeight()));
        placement.add(Placements.environmentScanUp(CAVE_SCAN_STEPS));
        placement.add(Placements.verticalOffset(-1));
        placement.add(Placements.biome());
        placement.add(Placements.filter(Placements.air(0, 0, 0)));
        return catalog.placed(name(candidate) + "_ceiling", featureId, placement);
    }

    private List<JsonObject> predicates(IrisDecorationPart part, Candidate candidate) {
        List<JsonObject> predicates = new ArrayList<>();
        switch (part) {
            case SEA_FLOOR -> {
                predicates.add(Placements.water(0, 0, 0));
                if (tall(candidate)) {
                    predicates.add(Placements.water(0, 1, 0));
                }
            }
            case SEA_SURFACE -> {
                predicates.add(Placements.air(0, 0, 0));
                predicates.add(Placements.water(0, -1, 0));
            }
            case SHORE_LINE -> {
                predicates.add(Placements.air(0, 0, 0));
                predicates.add(Placements.solid(0, -1, 0));
                predicates.add(Placements.anyOf(List.of(Placements.water(1, -1, 0), Placements.water(-1, -1, 0), Placements.water(0, -1, 1),
                        Placements.water(0, -1, -1))));
            }
            default -> {
                predicates.add(Placements.air(0, 0, 0));
                predicates.add(Placements.solid(0, -1, 0));
            }
        }
        if (!candidate.whitelist().isEmpty()) {
            predicates.add(Placements.matchingBlocks(candidate.whitelist(), 0, -1, 0));
        }
        if (!candidate.blacklist().isEmpty()) {
            predicates.add(Placements.not(Placements.matchingBlocks(candidate.blacklist(), 0, -1, 0)));
        }
        predicates.addAll(Placements.slopeLimits(candidate.decorator().getSlopeCondition()));
        return predicates;
    }

    private String feature(Candidate candidate, boolean hanging, boolean underwater) {
        IrisDecorator decorator = candidate.decorator();
        int stackMax = stackMax(decorator);
        int stackMin = Math.max(1, Math.min(decorator.getStackMin(), stackMax));
        String name = name(candidate);
        List<WeightedState> entries = waterlogged(candidate.entries(), underwater);
        List<WeightedState> top = waterlogged(candidate.top(), underwater);
        String main;
        boolean tall = !hanging && tall(candidate);
        if (tall || !hanging && stackMax == 1 && top.isEmpty()) {
            JsonObject config = new JsonObject();
            config.add("to_place", syntax.provider(tall ? lowerHalves(entries) : entries));
            main = catalog.feature(name, "minecraft:simple_block", config);
        } else {
            JsonArray layers = new JsonArray(2);
            if (top.isEmpty()) {
                layers.add(layer(Placements.intRange(stackMin, stackMax), entries));
            } else {
                layers.add(layer(Placements.intRange(stackMin - 1, stackMax - 1), entries));
                layers.add(layer(Placements.intRange(1, 1), top));
            }
            JsonObject config = new JsonObject();
            config.add("layers", layers);
            config.addProperty("direction", hanging ? "down" : "up");
            config.add("allowed_placement", underwater ? Placements.water(0, 0, 0) : Placements.air(0, 0, 0));
            config.addProperty("prioritize_tip", true);
            main = catalog.feature(name, "minecraft:block_column", config);
        }
        if (candidate.force() == null) {
            return main;
        }
        JsonObject forceConfig = new JsonObject();
        forceConfig.add("to_place", syntax.provider(List.of(new WeightedState(candidate.force(), 1))));
        String force = catalog.feature("decorator/ground_" + path(candidate.force()), "minecraft:simple_block", forceConfig);
        JsonArray groundPlacement = new JsonArray(1);
        groundPlacement.add(Placements.verticalOffset(-1));
        JsonArray features = new JsonArray(2);
        features.add(catalog.inlinePlaced(force, groundPlacement));
        features.add(catalog.inlinePlaced(main, new JsonArray()));
        JsonObject sequence = new JsonObject();
        sequence.add("features", features);
        return catalog.feature(name + "_forced", "minecraft:sequence", sequence);
    }

    private String patterned(Candidate candidate) {
        IrisDecorator decorator = candidate.decorator();
        DecoratorCoverage.Pattern pattern = patterns.computeIfAbsent(candidate.coverage(), coverage -> DecoratorCoverage.pattern(coverage, syntax.target()));
        String salt = name(candidate) + "|" + decorator.getStyle().getStyle() + "|" + decorator.getStyle().getZoom() + "|" + decorator.getChance();
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "minecraft:noise_threshold");
        provider.addProperty("seed", (long) salt.hashCode());
        provider.add("noise", patternNoise());
        provider.addProperty("scale", (float) pattern.scale());
        provider.addProperty("threshold", (float) pattern.threshold());
        provider.addProperty("high_chance", 0.0f);
        provider.add("default_state", syntax.blockState(AIR_STATE));
        provider.add("low_states", expanded(waterlogged(candidate.entries(), false)));
        JsonArray high = new JsonArray(1);
        high.add(syntax.blockState(AIR_STATE));
        provider.add("high_states", high);
        JsonObject config = new JsonObject();
        config.add("to_place", provider);
        return catalog.feature(name(candidate), "minecraft:simple_block", config);
    }

    private JsonArray expanded(List<WeightedState> entries) {
        int divisor = 0;
        int total = 0;
        for (WeightedState entry : entries) {
            divisor = gcd(divisor, entry.weight());
            total += entry.weight();
        }
        double shrink = Math.min(1.0, (double) MAX_PATTERN_STATES * divisor / total);
        JsonArray states = new JsonArray();
        for (WeightedState entry : entries) {
            long copies = Math.max(1L, Math.round(entry.weight() / (double) divisor * shrink));
            for (long copy = 0; copy < copies; copy++) {
                states.add(syntax.blockState(entry.state()));
            }
        }
        return states;
    }

    private static JsonObject patternNoise() {
        JsonArray amplitudes = new JsonArray(DecoratorCoverage.PATTERN_AMPLITUDES.size());
        for (double amplitude : DecoratorCoverage.PATTERN_AMPLITUDES) {
            amplitudes.add(amplitude);
        }
        JsonObject noise = new JsonObject();
        noise.addProperty("base_octave", DecoratorCoverage.PATTERN_OCTAVE);
        noise.addProperty("octave_count", DecoratorCoverage.PATTERN_AMPLITUDES.size());
        noise.add("amplitude_modifiers", amplitudes);
        noise.addProperty("base_amplitude", 1.0);
        noise.addProperty("normalize", false);
        return noise;
    }

    private DecoratorCoverage.Coverage coverage(IrisDecorator decorator) {
        double chance = Math.max(0.0, Math.min(1.0, decorator.getChance()));
        if (isStatic(decorator.getStyle())) {
            return new DecoratorCoverage.Coverage(chance, 0.0);
        }
        return coverages.computeIfAbsent(new CoverageKey(decorator.getStyle(), chance),
                key -> DecoratorCoverage.measure(key.style(), key.chance(), context.data()));
    }

    private static boolean single(Candidate candidate) {
        IrisDecorator decorator = candidate.decorator();
        return Math.min(decorator.getStackMax(), decorator.getAbsoluteMaxStack()) <= 1 && candidate.top().isEmpty() && candidate.force() == null;
    }

    private static int gcd(int first, int second) {
        return second == 0 ? first : gcd(second, first % second);
    }

    private static List<WeightedState> waterlogged(List<WeightedState> entries, boolean underwater) {
        List<WeightedState> placed = new ArrayList<>(entries.size());
        for (WeightedState entry : entries) {
            placed.add(new WeightedState(entry.state().waterlogged(underwater), entry.weight()));
        }
        return placed;
    }

    private static int stackMax(IrisDecorator decorator) {
        return Math.max(1, Math.min(decorator.getStackMax(), Math.max(1, decorator.getAbsoluteMaxStack())));
    }

    private static boolean tall(Candidate candidate) {
        if (stackMax(candidate.decorator()) != 1) {
            return false;
        }
        for (WeightedState entry : candidate.entries()) {
            String half = entry.state().properties().get("half");
            if (!"lower".equals(half) && !"upper".equals(half)) {
                return false;
            }
        }
        return true;
    }

    private static List<WeightedState> lowerHalves(List<WeightedState> entries) {
        List<WeightedState> lower = new ArrayList<>(entries.size());
        for (WeightedState entry : entries) {
            Map<String, String> properties = new TreeMap<>(entry.state().properties());
            properties.put("half", "lower");
            lower.add(new WeightedState(new VanillaBlockState(entry.state().name(), properties), entry.weight()));
        }
        return lower;
    }

    private JsonObject layer(JsonElement height, List<WeightedState> entries) {
        JsonObject layer = new JsonObject();
        layer.add("height", height);
        layer.add("provider", syntax.provider(entries));
        return layer;
    }

    private List<String> names(List<IrisBlockData> list, String resource) {
        TreeSet<String> names = new TreeSet<>();
        if (list == null) {
            return List.of();
        }
        for (IrisBlockData block : list) {
            blocks.resolve(block, resource).ifPresent(state -> names.add(state.name()));
        }
        return List.copyOf(names);
    }

    private void report(String resource, ExportLimit limit, String message) {
        ExportDiagnostic diagnostic = new ExportDiagnostic(ExportDiagnostic.Severity.INFO, limit, resource, message);
        if (!context.diagnostics().contains(diagnostic)) {
            context.diagnostics().add(diagnostic);
        }
    }

    private static String name(Candidate candidate) {
        return "decorator/" + path(candidate.entries().getFirst().state());
    }

    private static String path(VanillaBlockState state) {
        return state.name().substring(state.name().indexOf(':') + 1);
    }

    private static boolean isStatic(IrisGeneratorStyle style) {
        return style == null || style.getStyle() == null || style.getStyle() == NoiseStyle.STATIC;
    }

    private static boolean aquatic(List<WeightedState> entries, List<WeightedState> top) {
        if (entries.isEmpty()) {
            return false;
        }
        for (WeightedState entry : entries) {
            if (!aquatic(entry.state())) {
                return false;
            }
        }
        for (WeightedState entry : top) {
            if (!aquatic(entry.state())) {
                return false;
            }
        }
        return true;
    }

    private static boolean aquatic(VanillaBlockState state) {
        String name = state.name();
        if ("true".equals(state.properties().get("waterlogged")) || AQUATIC.contains(name)) {
            return true;
        }
        return name.startsWith("minecraft:") && name.contains("_coral") && !name.startsWith("minecraft:dead_");
    }

    private record Candidate(IrisDecorator decorator, List<WeightedState> entries, List<WeightedState> top, double airShare,
                             VanillaBlockState force, boolean aquatic, List<String> whitelist, List<String> blacklist,
                             DecoratorCoverage.Coverage coverage) {
    }

    private record CoverageKey(IrisGeneratorStyle style, double chance) {
    }
}
