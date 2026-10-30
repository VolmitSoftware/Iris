package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class DecoratorCompiler {
    private static final int COLUMNS = 256;
    private static final double CAVE_AIR_SHARE = 0.08;
    private static final int CAVE_SCAN_STEPS = 12;
    private static final int MAX_ATTEMPTS = 256;
    private static final double SLOPE_CEILING = 10.0;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final String STYLE_MESSAGE = "Decorators gated or varied by a noise style place at the style's average density without its pattern";
    private static final String STACK_MESSAGE = "Decorator stacks scaled to cave height use their fixed stack range";
    private static final String CAVE_MESSAGE = "Cave ceiling decorators place at an estimated density because features cannot enumerate every cave ceiling";

    private final PackCompileContext context;
    private final ExportedBiomes biomes;
    private final BlockStateResolver blocks;
    private final FeatureCatalog catalog;
    private final WorldgenSyntax syntax;

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
                chances[index] = Math.max(0.0, Math.min(1.0, candidates.get(index).decorator().getChance()));
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
                    context.features().add(biomeId, GenerationStep.UNDERGROUND_DECORATION, ceiling(candidate, shares[index]));
                } else {
                    context.features().add(biomeId, GenerationStep.VEGETAL_DECORATION, column(bucket.getKey(), candidate, sequential[index]));
                }
            }
        }
    }

    private Optional<Candidate> candidate(IrisDecorator decorator, String resource) {
        List<WeightedState> entries = new ArrayList<>();
        int airWeight = 0;
        int total = 0;
        for (IrisBlockData block : decorator.getPalette()) {
            Optional<VanillaBlockState> state = blocks.resolve(block, resource);
            if (state.isEmpty()) {
                continue;
            }
            int weight = Math.max(1, block.getWeight());
            total += weight;
            if (AIR.contains(state.get().name())) {
                airWeight += weight;
            } else {
                entries.add(new WeightedState(state.get(), weight));
            }
        }
        if (total == 0) {
            return Optional.empty();
        }
        if (!isStatic(decorator.getStyle()) || (entries.size() > 1 && !isStatic(decorator.getVariance()))) {
            report(resource, ExportLimit.DECORATOR_STYLE, STYLE_MESSAGE);
        }
        if (decorator.isScaleStack()) {
            report(resource, ExportLimit.SCALE_STACK, STACK_MESSAGE);
        }
        if (decorator.getSlopeCondition() != null && !decorator.getSlopeCondition().isDefault()) {
            report(resource, ExportLimit.SLOPE, RepaintFeatureCompiler.SLOPE_MESSAGE);
        }
        List<WeightedState> top = new ArrayList<>();
        for (IrisBlockData block : decorator.getTopPalette()) {
            Optional<VanillaBlockState> state = blocks.resolve(block, resource);
            if (state.isPresent() && !AIR.contains(state.get().name())) {
                top.add(new WeightedState(state.get(), Math.max(1, block.getWeight())));
            }
        }
        VanillaBlockState force = decorator.getForceBlock() == null ? null : blocks.resolve(decorator.getForceBlock(), resource).orElse(null);
        return Optional.of(new Candidate(decorator, entries, top, (double) airWeight / total, force, names(decorator.getWhitelist(), resource),
                names(decorator.getBlacklist(), resource)));
    }

    private String column(IrisDecorationPart part, Candidate candidate, double sequential) {
        String featureId = feature(candidate, false);
        JsonArray placement = new JsonArray();
        String heightmap = part == IrisDecorationPart.SEA_FLOOR ? Placements.OCEAN_FLOOR : Placements.WORLD_SURFACE;
        for (JsonObject modifier : Placements.everyColumn()) {
            placement.add(modifier);
        }
        placement.add(Placements.heightmap(heightmap));
        if (sequential < 1.0) {
            placement.add(Placements.randomChance(sequential));
        }
        placement.add(Placements.biome());
        placement.add(Placements.filter(Placements.allOf(predicates(part, candidate))));
        return catalog.placed(name(candidate), featureId, placement);
    }

    private String ceiling(Candidate candidate, double share) {
        String featureId = feature(candidate, true);
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
            case SEA_FLOOR -> predicates.add(Placements.water(0, 0, 0));
            case SEA_SURFACE -> {
                predicates.add(Placements.air(0, 0, 0));
                predicates.add(Placements.water(0, -1, 0));
            }
            case SHORE_LINE -> {
                predicates.add(Placements.air(0, 0, 0));
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
        IrisSlopeClip slope = candidate.decorator().getSlopeCondition();
        if (slope != null && !slope.isDefault()) {
            if (slope.getMinimumSlope() > 0) {
                predicates.add(Placements.steeperThan(rise(slope.getMinimumSlope()), -1));
            }
            if (slope.getMaximumSlope() < SLOPE_CEILING) {
                predicates.add(Placements.not(Placements.steeperThan(rise(slope.getMaximumSlope()) + 1, -1)));
            }
        }
        return predicates;
    }

    private String feature(Candidate candidate, boolean hanging) {
        IrisDecorator decorator = candidate.decorator();
        int stackMax = Math.max(1, Math.min(decorator.getStackMax(), Math.max(1, decorator.getAbsoluteMaxStack())));
        int stackMin = Math.max(1, Math.min(decorator.getStackMin(), stackMax));
        String name = name(candidate);
        String main;
        if (!hanging && stackMax == 1 && candidate.top().isEmpty()) {
            JsonObject config = new JsonObject();
            config.add("to_place", syntax.provider(candidate.entries()));
            main = catalog.feature(name, "minecraft:simple_block", config);
        } else {
            JsonArray layers = new JsonArray(2);
            if (candidate.top().isEmpty()) {
                layers.add(layer(Placements.intRange(stackMin, stackMax), candidate.entries()));
            } else {
                layers.add(layer(Placements.intRange(stackMin - 1, stackMax - 1), candidate.entries()));
                layers.add(layer(Placements.intRange(1, 1), candidate.top()));
            }
            JsonObject config = new JsonObject();
            config.add("layers", layers);
            config.addProperty("direction", hanging ? "down" : "up");
            config.add("allowed_placement", Placements.air(0, 0, 0));
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

    private static int rise(double slope) {
        return Math.max(1, (int) Math.round(slope));
    }

    private record Candidate(IrisDecorator decorator, List<WeightedState> entries, List<WeightedState> top, double airShare,
                             VanillaBlockState force, List<String> whitelist, List<String> blacklist) {
    }
}
