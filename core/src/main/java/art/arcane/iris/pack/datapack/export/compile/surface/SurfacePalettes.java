package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.compile.WorldgenSyntax;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SurfacePalettes {
    static final double NEVER = -1.0e6;
    private static final List<Double> NOISE_AMPLITUDES = List.of(1.0, 1.0);
    private static final int MIN_OCTAVE = -12;
    private static final int MAX_OCTAVE = -1;
    private static final int NOISE_VARIANTS = 8;
    private static final int SAMPLE_AXIS = 16;
    private static final double SAMPLE_SPACING = 0.731;
    private static final double SINGLE_PAIR_FLOOR = 0.25;
    private static final Comparator<WeightedState> ASCENDING_WEIGHT = Comparator.comparingInt(WeightedState::weight)
            .thenComparing(entry -> entry.state().canonical());

    private final String namespace;
    private final NoiseRegistry noises;
    private final BlockStateResolver blocks;
    private final DataVersion target;
    private final Map<String, double[]> samples = new HashMap<>();

    SurfacePalettes(String namespace, NoiseRegistry noises, BlockStateResolver blocks, DataVersion target) {
        this.namespace = namespace;
        this.noises = noises;
        this.blocks = blocks;
        this.target = target;
    }

    Optional<SurfacePalette> palette(List<IrisBlockData> palette, IrisGeneratorStyle style, double zoom, int variant, String resource) {
        List<WeightedState> entries = resolve(palette, resource);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        if (isStatic(style)) {
            entries = merged(entries);
        }
        if (entries.size() == 1) {
            return Optional.of(new SurfacePalette(entries, SurfacePalette.Selection.SINGLE, null, List.of()));
        }
        if (isStatic(style)) {
            return Optional.of(new SurfacePalette(entries, SurfacePalette.Selection.STATIC, null, List.of()));
        }
        String noise = noise(style, zoom, variant);
        int total = 0;
        for (WeightedState entry : entries) {
            total += entry.weight();
        }
        List<Double> thresholds = new ArrayList<>(entries.size() - 1);
        int cumulative = 0;
        for (int index = 0; index < entries.size() - 1; index++) {
            cumulative += entries.get(index).weight();
            thresholds.add(quantile(noise, (double) cumulative / total));
        }
        return Optional.of(new SurfacePalette(entries, SurfacePalette.Selection.NOISE, noise, thresholds));
    }

    List<WeightedState> resolve(List<IrisBlockData> palette, String resource) {
        List<WeightedState> entries = new ArrayList<>();
        if (palette == null) {
            return entries;
        }
        for (IrisBlockData block : palette) {
            Optional<VanillaBlockState> state = blocks.resolve(block, resource);
            if (state.isPresent()) {
                entries.add(new WeightedState(state.get(), Math.max(1, block.getWeight())));
            }
        }
        return entries;
    }

    OreChance chance(double probability, IrisGeneratorStyle style, double zoom, int variant) {
        if (isStatic(style)) {
            return new OreChance(probability, null, 0.0);
        }
        String noise = noise(style, zoom, variant);
        return new OreChance(probability, noise, quantile(noise, probability));
    }

    JsonObject render(SurfacePalette palette, WorldgenSyntax syntax, String scope, int centerY) {
        return switch (palette.selection()) {
            case SINGLE -> MaterialRules.block(syntax, palette.entries().getFirst().state());
            case STATIC -> renderStatic(palette, syntax, scope, centerY);
            case NOISE -> renderNoise(palette, syntax);
        };
    }

    JsonObject renderChance(OreChance chance, String scope, int centerY, JsonObject rule) {
        if (chance.noise() != null) {
            return MaterialRules.condition(MaterialRules.noiseThreshold(chance.noise(), NEVER, chance.threshold()), rule);
        }
        return MaterialRules.guarded(gradientChance(chance.probability(), randomPrefix(scope), centerY), rule);
    }

    static boolean isStatic(IrisGeneratorStyle style) {
        return style == null || style.getStyle() == null || style.getStyle() == NoiseStyle.STATIC;
    }

    static List<JsonObject> gradientChance(double probability, String randomPrefix, int centerY) {
        if (probability >= 1.0) {
            return List.of();
        }
        double clamped = Math.max(probability, 1.0e-9);
        int factors = clamped >= SINGLE_PAIR_FLOOR ? 1 : (int) Math.ceil(Math.log(clamped) / Math.log(SINGLE_PAIR_FLOOR));
        double factor = Math.pow(clamped, 1.0 / factors);
        int center = Math.max(MaterialRules.MIN_ANCHOR + 1, Math.min(MaterialRules.MAX_ANCHOR - 1, centerY));
        int reach = Math.min(center - MaterialRules.MIN_ANCHOR, MaterialRules.MAX_ANCHOR - center);
        int width = (int) Math.min(2L * reach, Math.round(reach / Math.sqrt(factor)));
        List<JsonObject> conditions = new ArrayList<>(factors * 2);
        for (int index = 0; index < factors; index++) {
            int falling = center + reach;
            int rising = center - reach;
            conditions.add(MaterialRules.verticalGradient(randomPrefix + index + "_a", falling - width, falling));
            conditions.add(MaterialRules.not(MaterialRules.verticalGradient(randomPrefix + index + "_b", rising, rising + width)));
        }
        return conditions;
    }

    private JsonObject renderStatic(SurfacePalette palette, WorldgenSyntax syntax, String scope, int centerY) {
        List<WeightedState> entries = new ArrayList<>(palette.entries());
        entries.sort(ASCENDING_WEIGHT);
        int remaining = 0;
        for (WeightedState entry : entries) {
            remaining += entry.weight();
        }
        List<JsonObject> rules = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size() - 1; index++) {
            WeightedState entry = entries.get(index);
            double probability = (double) entry.weight() / remaining;
            rules.add(MaterialRules.guarded(gradientChance(probability, randomPrefix(scope) + index + "_", centerY),
                    MaterialRules.block(syntax, entry.state())));
            remaining -= entry.weight();
        }
        rules.add(MaterialRules.block(syntax, entries.getLast().state()));
        return MaterialRules.sequence(rules);
    }

    private JsonObject renderNoise(SurfacePalette palette, WorldgenSyntax syntax) {
        List<JsonObject> rules = new ArrayList<>(palette.entries().size());
        for (int index = 0; index < palette.entries().size() - 1; index++) {
            JsonObject condition = MaterialRules.noiseThreshold(palette.noise(), NEVER, palette.thresholds().get(index));
            rules.add(MaterialRules.condition(condition, MaterialRules.block(syntax, palette.entries().get(index).state())));
        }
        rules.add(MaterialRules.block(syntax, palette.entries().getLast().state()));
        return MaterialRules.sequence(rules);
    }

    private String randomPrefix(String scope) {
        return namespace + ":surface/" + scope + "/";
    }

    private String noise(IrisGeneratorStyle style, double zoom, int variant) {
        double styleZoom = style == null ? 1.0 : Math.max(style.getZoom(), 1.0e-4);
        double effectiveZoom = Math.max(1.0, Math.max(zoom, 1.0e-4) * styleZoom);
        int octave = (int) Math.max(MIN_OCTAVE, Math.min(MAX_OCTAVE, -Math.round(Math.log(effectiveZoom) / Math.log(2.0))));
        String id = "surface/palette_" + (-octave) + "_" + Math.floorMod(variant, NOISE_VARIANTS);
        return noises.register(new NoiseDef(id, octave, NOISE_AMPLITUDES));
    }

    private double quantile(String noise, double fraction) {
        double[] sorted = samples.computeIfAbsent(noise, this::sample);
        double position = Math.max(0.0, Math.min(1.0, fraction)) * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }

    private double[] sample(String noise) {
        NoiseDef definition = definition(noise);
        VanillaNoiseModel model = VanillaNoiseModel.create(noise, definition.firstOctave(), definition.amplitudes(), 0L, target);
        double spacing = SAMPLE_SPACING * Math.pow(2.0, -definition.firstOctave());
        double[] values = new double[SAMPLE_AXIS * SAMPLE_AXIS * SAMPLE_AXIS];
        int index = 0;
        for (int x = 0; x < SAMPLE_AXIS; x++) {
            for (int y = 0; y < SAMPLE_AXIS; y++) {
                for (int z = 0; z < SAMPLE_AXIS; z++) {
                    values[index++] = model.sample(x * spacing + 0.37, y * spacing * 1.13 + 0.71, z * spacing * 0.89 + 0.13);
                }
            }
        }
        Arrays.sort(values);
        return values;
    }

    private NoiseDef definition(String noise) {
        String id = noise.substring(namespace.length() + 1);
        for (NoiseDef definition : noises.definitions()) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        throw new IllegalStateException("Surface noise '" + noise + "' is not registered");
    }

    private static List<WeightedState> merged(List<WeightedState> entries) {
        Map<VanillaBlockState, Integer> weights = new LinkedHashMap<>();
        for (WeightedState entry : entries) {
            weights.merge(entry.state(), entry.weight(), Integer::sum);
        }
        List<WeightedState> merged = new ArrayList<>(weights.size());
        for (Map.Entry<VanillaBlockState, Integer> weight : weights.entrySet()) {
            merged.add(new WeightedState(weight.getKey(), weight.getValue()));
        }
        return merged;
    }

    record OreChance(double probability, String noise, double threshold) {
    }
}
