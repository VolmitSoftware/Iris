package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.ExportedBiomes;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.decoration.FeatureCatalog;
import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.compile.decoration.Placements;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class RepaintFeatureCompiler {
    public static final String SLOPE_MESSAGE = "Slope-dependent layers and decorators measure slope over 3 blocks in the four cardinal directions";
    private static final double SLOPE_CEILING = 10.0;
    private static final int WALL_DROP = 3;
    private static final int WALL_DEPTH = 32;
    private static final int UNBOUNDED = -1;

    private final PackCompileContext context;
    private final ExportedBiomes biomes;
    private final SurfacePalettes palettes;
    private final FeatureCatalog catalog;
    private final WorldgenSyntax syntax;

    public RepaintFeatureCompiler(PackCompileContext context, ExportedBiomes biomes, BlockStateResolver blocks, FeatureCatalog catalog) {
        this.context = context;
        this.biomes = biomes;
        this.palettes = new SurfacePalettes(context.namespace(), context.noises(), blocks, context.capabilities().target());
        this.catalog = catalog;
        this.syntax = catalog.syntax();
    }

    public void compile() {
        Map<RepaintRule, List<String>> rules = new LinkedHashMap<>();
        for (Map.Entry<String, IrisBiome> entry : biomes.byId().entrySet()) {
            for (RepaintRule rule : rules(entry.getKey(), entry.getValue())) {
                rules.computeIfAbsent(rule, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }
        if (rules.isEmpty()) {
            return;
        }
        for (Map.Entry<RepaintRule, List<String>> rule : rules.entrySet()) {
            String placed = placed(rule.getKey());
            for (String biomeId : rule.getValue()) {
                context.features().add(biomeId, GenerationStep.LOCAL_MODIFICATIONS, placed);
            }
        }
    }

    private List<RepaintRule> rules(String biomeId, IrisBiome biome) {
        String resource = biomes.resource(biomeId);
        List<RepaintRule> rules = new ArrayList<>();
        if (!biome.isLockLayers()) {
            rules.addAll(slopeRules(biome, resource));
        }
        IrisBiomePaletteLayer wall = biome.getWall();
        if (wall != null && wall.getPalette() != null && !wall.getPalette().isEmpty()) {
            List<WeightedState> materials = palettes.resolve(wall.getPalette(), resource);
            if (!materials.isEmpty()) {
                rules.add(new RepaintRule(Kind.WALL, 0, UNBOUNDED, materials, WALL_DEPTH, WALL_DEPTH));
            }
        }
        IrisBiomePaletteLayer slab = biome.getSlab();
        if (slab != null && slab.getPalette() != null && !slab.getPalette().isEmpty()) {
            List<WeightedState> materials = palettes.resolve(slab.getPalette(), resource);
            if (!materials.isEmpty()) {
                rules.add(new RepaintRule(Kind.SLAB, 0, UNBOUNDED, materials, 1, 1));
            }
        }
        return rules;
    }

    private List<RepaintRule> slopeRules(IrisBiome biome, String resource) {
        List<IrisBiomePaletteLayer> layers = biome.getLayers();
        TreeSet<Double> breakpoints = new TreeSet<>();
        List<List<WeightedState>> resolved = new ArrayList<>(layers == null ? 0 : layers.size());
        boolean gated = false;
        for (int index = 0; layers != null && index < layers.size(); index++) {
            IrisBiomePaletteLayer layer = layers.get(index);
            List<WeightedState> entries = layer == null ? List.of() : palettes.resolve(layer.getPalette(), resource);
            resolved.add(entries);
            IrisSlopeClip clip = layer == null ? null : layer.getSlopeCondition();
            if (clip == null || clip.isDefault()) {
                continue;
            }
            gated = true;
            if (clip.getMinimumSlope() > 0) {
                breakpoints.add(clip.getMinimumSlope());
            }
            if (clip.getMaximumSlope() < SLOPE_CEILING) {
                breakpoints.add(clip.getMaximumSlope() + Math.ulp(clip.getMaximumSlope()));
            }
        }
        if (!gated) {
            return List.of();
        }
        ExportDiagnostic slope = new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.SLOPE, resource, SLOPE_MESSAGE);
        if (!context.diagnostics().contains(slope)) {
            context.diagnostics().add(slope);
        }
        int flatTop = topLayer(layers, resolved, 0.0);
        List<RepaintRule> rules = new ArrayList<>();
        List<Double> points = new ArrayList<>(breakpoints);
        int startRise = 0;
        int currentTop = flatTop;
        for (int index = 0; index < points.size(); index++) {
            double from = points.get(index);
            double to = index + 1 < points.size() ? points.get(index + 1) : from + 1.0;
            int top = topLayer(layers, resolved, (from + to) / 2.0);
            if (top == currentTop) {
                continue;
            }
            addSlopeRule(rules, layers, resolved, currentTop, flatTop, startRise, rise(from));
            currentTop = top;
            startRise = rise(from);
        }
        addSlopeRule(rules, layers, resolved, currentTop, flatTop, startRise, UNBOUNDED);
        return rules;
    }

    private void addSlopeRule(List<RepaintRule> rules, List<IrisBiomePaletteLayer> layers, List<List<WeightedState>> resolved, int top, int flatTop,
                              int fromRise, int toRise) {
        if (top < 0 || top == flatTop || fromRise == toRise) {
            return;
        }
        IrisBiomePaletteLayer layer = layers.get(top);
        int min = Math.max(1, layer.getMinHeight());
        int max = Math.max(min, layer.getMaxHeight());
        rules.add(new RepaintRule(Kind.SLOPE, fromRise, toRise, resolved.get(top), min, max));
    }

    private String placed(RepaintRule rule) {
        String name = "repaint/" + rule.kind().name().toLowerCase() + "_" + rule.materials().getFirst().state().name().substring("minecraft:".length());
        JsonArray placement = new JsonArray();
        for (JsonObject modifier : Placements.everyColumn()) {
            placement.add(modifier);
        }
        if (rule.kind() == Kind.SLAB) {
            placement.add(Placements.heightmap(Placements.WORLD_SURFACE));
            placement.add(Placements.biome());
            placement.add(Placements.filter(Placements.allOf(List.of(Placements.air(0, 0, 0), Placements.solid(0, -1, 0), Placements.stepUpBeside()))));
            return catalog.placed(name, catalog.feature(name, "minecraft:simple_block", simpleBlock(rule)), placement);
        }
        placement.add(Placements.heightmap(Placements.OCEAN_FLOOR));
        placement.add(Placements.verticalOffset(-1));
        placement.add(Placements.biome());
        placement.add(Placements.filter(trigger(rule)));
        JsonObject allowed = rule.kind() == Kind.WALL ? Placements.exposed() : Placements.solid(0, 0, 0);
        return catalog.placed(name, catalog.feature(name, "minecraft:block_column", column(rule, allowed)), placement);
    }

    private JsonObject trigger(RepaintRule rule) {
        if (rule.kind() == Kind.WALL) {
            return Placements.allOf(List.of(Placements.solid(0, 0, 0), Placements.dropAtLeast(WALL_DROP)));
        }
        List<JsonObject> conditions = new ArrayList<>(3);
        conditions.add(Placements.solid(0, 0, 0));
        if (rule.fromRise() > 0) {
            conditions.add(Placements.steeperThan(rule.fromRise(), 0));
        }
        if (rule.toRise() != UNBOUNDED) {
            conditions.add(Placements.not(Placements.steeperThan(rule.toRise(), 0)));
        }
        return Placements.allOf(conditions);
    }

    private JsonObject column(RepaintRule rule, JsonObject allowed) {
        JsonObject layer = new JsonObject();
        layer.add("height", Placements.intRange(rule.minThickness(), rule.maxThickness()));
        layer.add("provider", syntax.provider(rule.materials()));
        JsonArray layers = new JsonArray(1);
        layers.add(layer);
        JsonObject config = new JsonObject();
        config.add("layers", layers);
        config.addProperty("direction", "down");
        config.add("allowed_placement", allowed);
        config.addProperty("prioritize_tip", false);
        return config;
    }

    private JsonObject simpleBlock(RepaintRule rule) {
        JsonObject config = new JsonObject();
        config.add("to_place", syntax.provider(rule.materials()));
        return config;
    }

    private static int topLayer(List<IrisBiomePaletteLayer> layers, List<List<WeightedState>> resolved, double slope) {
        for (int index = 0; index < layers.size(); index++) {
            IrisBiomePaletteLayer layer = layers.get(index);
            if (layer == null || resolved.get(index).isEmpty() || Math.max(layer.getMinHeight(), layer.getMaxHeight()) <= 0) {
                continue;
            }
            if (layer.getSlopeCondition() == null || layer.getSlopeCondition().isValid(slope)) {
                return index;
            }
        }
        return -1;
    }

    private static int rise(double slope) {
        return Math.max(1, (int) Math.round(slope));
    }

    private enum Kind {
        SLOPE,
        WALL,
        SLAB
    }

    private record RepaintRule(Kind kind, int fromRise, int toRise, List<WeightedState> materials, int minThickness, int maxThickness) {
    }
}
