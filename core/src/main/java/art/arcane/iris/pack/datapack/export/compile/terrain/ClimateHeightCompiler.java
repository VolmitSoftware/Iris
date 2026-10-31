package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.GenerationCostCounter;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

public final class ClimateHeightCompiler {
    public static final String CLIMATE_HEIGHT = "height/climate";
    private static final String GENERATOR_SCOPE = "height";
    private static final String SELECTOR_SCOPE = "climate";
    private static final String MEAN_PREFIX = "height/climate/mean/";
    private static final double GENERATOR_POINT_LIMIT = 1024.0;
    private static final long CALIBRATION_SEED = 0x5EED_C11AL;
    private static final int CALIBRATION_SIDE = 16;
    private static final int CALIBRATION_STEP = 97;

    private final StyleCompiler styles;

    public ClimateHeightCompiler(StyleCompiler styles) {
        this.styles = Objects.requireNonNull(styles, "styles");
    }

    public Field compile(BiomeCodes codes, IrisDimension dimension, IrisData data, NoiseRegistry registry, ExportCapabilities capabilities) {
        Map<String, IrisBiome> biomes = TerrainBiomes.resolve(codes, data);
        List<SortedMap<String, IrisGenerator>> buckets = new ArrayList<>(HeightCompiler.buckets(biomes, data).values());
        List<ExportDiagnostic> repeatedDiagnostics = new ArrayList<>();
        Generators generators = new Generators(new GeneratorCompiler(styles, registry, capabilities, repeatedDiagnostics), registry, capabilities);
        Map<Integer, Field> means = new HashMap<>(buckets.size() * 2);
        List<String> ids = BandTreeCompiler.surfaceBiomeIds(codes);
        Map<String, Field> heightById = new HashMap<>(ids.size() * 2);
        for (String id : ids) {
            IrisBiome biome = biomes.get(id);
            heightById.put(id, biome == null ? new Field.Const(0.0) : biomeHeight(biome, buckets, means, generators));
        }
        List<Field> terms = new ArrayList<>();
        terms.add(new Field.Const(dimension.getMinHeight() + dimension.getFluidHeight()));
        terms.add(select(new BandTreeCompiler(codes, SELECTOR_SCOPE), ids, heightById));
        List<IrisShapedGeneratorStyle> overlay = dimension.getOverlayNoise();
        for (int layer = 0; overlay != null && layer < overlay.size(); layer++) {
            terms.add(HeightCompiler.overlay(overlay.get(layer), generators.compiler(), layer).height());
        }
        Field height = HeightCompiler.clamp(FieldMacros.sum(terms), dimension.getMinHeight(), dimension.getMaxHeight());
        FieldValidator.requireTwoDimensional(height, CLIMATE_HEIGHT);
        return new Field.Shared(CLIMATE_HEIGHT, height);
    }

    private static Field biomeHeight(IrisBiome biome, List<SortedMap<String, IrisGenerator>> buckets, Map<Integer, Field> means, Generators generators) {
        List<Field> terms = new ArrayList<>(buckets.size() + 1);
        double low = 0.0;
        for (int index = 0; index < buckets.size(); index++) {
            double minimum = 0.0;
            double maximum = 0.0;
            for (String key : buckets.get(index).keySet()) {
                minimum += biome.getGenLinkMin(key);
                maximum += biome.getGenLinkMax(key);
            }
            low += minimum;
            if (maximum != minimum) {
                terms.add(FieldMacros.scale(mean(index, buckets, means, generators), maximum - minimum));
            }
        }
        terms.add(new Field.Const(low));
        return FieldMacros.sum(terms);
    }

    private static Field mean(int index, List<SortedMap<String, IrisGenerator>> buckets, Map<Integer, Field> means, Generators generators) {
        Field known = means.get(index);
        if (known != null) {
            return known;
        }
        SortedMap<String, IrisGenerator> members = buckets.get(index);
        List<Field> heights = new ArrayList<>(members.size());
        for (IrisGenerator generator : members.values()) {
            heights.add(generators.proxy(generator));
        }
        Field mean = FieldMacros.share(MEAN_PREFIX + index, FieldMacros.scale(FieldMacros.sum(heights), 1.0 / heights.size()));
        means.put(index, mean);
        return mean;
    }

    private static Field select(BandTreeCompiler bands, List<String> ids, Map<String, Field> heightById) {
        Map<String, Double> constants = new HashMap<>(ids.size() * 2);
        Map<String, Double> positions = new HashMap<>(ids.size() * 2);
        List<Field> cases = new ArrayList<>(ids.size());
        boolean constant = true;
        for (String id : ids) {
            Field height = heightById.get(id);
            if (height instanceof Field.Const value) {
                constants.put(id, value.value());
            } else {
                constant = false;
            }
            positions.put(id, (double) cases.size());
            cases.add(height);
        }
        if (constant) {
            return bands.hard(constants);
        }
        Field index = bands.hard(positions);
        return index instanceof Field.Const ? cases.getFirst() : new Field.Mux(index, cases);
    }

    private static final class Generators {
        private final GeneratorCompiler compiler;
        private final NoiseRegistry registry;
        private final ExportCapabilities capabilities;

        private Generators(GeneratorCompiler compiler, NoiseRegistry registry, ExportCapabilities capabilities) {
            this.compiler = compiler;
            this.registry = registry;
            this.capabilities = capabilities;
        }

        private GeneratorCompiler compiler() {
            return compiler;
        }

        private Field proxy(IrisGenerator generator) {
            Field raw = compiler.compileRaw(generator, GENERATOR_SCOPE);
            if (GenerationCostCounter.worstCase(raw, VanillaBounds.of(registry.namespace(), registry.definitions()), capabilities.target()) <= GENERATOR_POINT_LIMIT) {
                return raw;
            }
            return new Field.Const(calibratedMean(raw));
        }

        private double calibratedMean(Field raw) {
            Map<String, NoiseDef> noises = registry.byReference();
            double total = 0.0;
            int origin = -CALIBRATION_SIDE / 2 * CALIBRATION_STEP;
            for (int column = 0; column < CALIBRATION_SIDE; column++) {
                for (int row = 0; row < CALIBRATION_SIDE; row++) {
                    total += FieldEvaluator.sample(raw, noises, CALIBRATION_SEED, capabilities.target(),
                            origin + column * CALIBRATION_STEP, 0, origin + row * CALIBRATION_STEP);
                }
            }
            return total / (CALIBRATION_SIDE * CALIBRATION_SIDE);
        }
    }
}
