package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.iris.generation.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class HeightCompiler {
    public static final String HEIGHT = "height";
    public static final String SLOPE = "terrain/height/slope";
    private static final String SCOPE = "height";
    private static final double SLOPE_STEP = 4.0;
    private static final Field ZERO = new Field.Const(0.0);
    private static final String OVERLAY_PATH = "height/overlay/";
    private static final String BUCKET_PREFIX = "terrain/height/bucket/";
    private static final Comparator<IrisInterpolator> INTERPOLATOR_ORDER = Comparator
            .comparing((IrisInterpolator interpolator) -> interpolator.getFunction().name())
            .thenComparingDouble(IrisInterpolator::getHorizontalScale);

    private final StyleCompiler styles;
    private final List<ExportDiagnostic> diagnostics;

    public HeightCompiler(StyleCompiler styles, List<ExportDiagnostic> diagnostics) {
        this.styles = Objects.requireNonNull(styles, "styles");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public TerrainHeight compile(BiomeCodes codes, IrisDimension dimension, IrisData data, NoiseRegistry registry, ExportCapabilities capabilities) {
        Map<String, IrisBiome> biomes = TerrainBiomes.resolve(codes, data);
        SortedMap<IrisInterpolator, SortedMap<String, IrisGenerator>> buckets = buckets(biomes, data);
        BandTreeCompiler bands = new BandTreeCompiler(codes, SCOPE);
        GeneratorCompiler generators = new GeneratorCompiler(styles, registry, capabilities, diagnostics);
        int step = dimension.getBiomeBoundsSamplingStep();
        List<Term> terms = new ArrayList<>();
        Field base = new Field.Const(dimension.getMinHeight() + dimension.getFluidHeight());
        terms.add(new Term(base, ZERO, ZERO));
        int index = 0;
        for (Map.Entry<IrisInterpolator, SortedMap<String, IrisGenerator>> bucket : buckets.entrySet()) {
            terms.add(bucket(bucket.getKey(), bucket.getValue(), biomes, bands, generators, step, BUCKET_PREFIX + index));
            index++;
        }
        List<IrisShapedGeneratorStyle> overlay = dimension.getOverlayNoise();
        for (int layer = 0; overlay != null && layer < overlay.size(); layer++) {
            terms.add(overlay(overlay.get(layer), generators, layer));
        }
        List<Field> heights = new ArrayList<>(terms.size());
        List<Field> eastward = new ArrayList<>(terms.size());
        List<Field> southward = new ArrayList<>(terms.size());
        for (Term term : terms) {
            heights.add(term.height());
            eastward.add(term.eastward());
            southward.add(term.southward());
        }
        Field height = clamp(FieldMacros.sum(heights), dimension.getMinHeight(), dimension.getMaxHeight());
        FieldValidator.requireTwoDimensional(height, HEIGHT);
        Field slope = FieldMacros.share(SLOPE, FieldMacros.norm(new FieldMacros.Gradient(FieldMacros.sum(eastward), FieldMacros.sum(southward))));
        return new TerrainHeight(new Field.Shared(HEIGHT, height), slope);
    }

    private static Term bucket(IrisInterpolator interpolator, SortedMap<String, IrisGenerator> members, Map<String, IrisBiome> biomes,
                               BandTreeCompiler bands, GeneratorCompiler generators, int step, String name) {
        Map<String, Double> low = new HashMap<>();
        Map<String, Double> span = new HashMap<>();
        boolean contributes = false;
        boolean spans = false;
        for (Map.Entry<String, IrisBiome> entry : biomes.entrySet()) {
            double minimum = 0.0;
            double maximum = 0.0;
            for (String key : members.keySet()) {
                minimum += entry.getValue().getGenLinkMin(key);
                maximum += entry.getValue().getGenLinkMax(key);
            }
            low.put(entry.getKey(), minimum);
            span.put(entry.getKey(), maximum - minimum);
            contributes |= minimum != 0.0 || maximum != 0.0;
            spans |= maximum != minimum;
        }
        if (!contributes) {
            return new Term(ZERO, ZERO, ZERO);
        }
        Field lowBand = band(bands, low, interpolator, step);
        if (!spans) {
            return new Term(lowBand, ZERO, ZERO);
        }
        Field spanBand = FieldMacros.share(name + "/span", band(bands, span, interpolator, step));
        Field spanHard = bands.hard(span);
        List<Field> heights = new ArrayList<>(members.size());
        for (IrisGenerator generator : members.values()) {
            heights.add(generators.compile(generator, SCOPE));
        }
        Field mean = FieldMacros.scale(FieldMacros.sum(heights), 1.0 / heights.size());
        FieldMacros.Gradient gradient = FieldMacros.grad2(mean, SLOPE_STEP);
        return new Term(FieldMacros.sum(List.of(lowBand, FieldMacros.product(spanBand, mean))),
                FieldMacros.product(spanHard, gradient.x()), FieldMacros.product(spanHard, gradient.z()));
    }

    static Term overlay(IrisShapedGeneratorStyle style, GeneratorCompiler generators, int index) {
        double minimum = style.getMin();
        double maximum = style.getMax();
        if (minimum == maximum) {
            return new Term(new Field.Const(minimum), ZERO, ZERO);
        }
        Field noise = generators.style(style.getGenerator(), 1.0, OVERLAY_PATH + index);
        FieldMacros.Gradient gradient = FieldMacros.grad2(noise, SLOPE_STEP);
        Field overlay = FieldMacros.sum(List.of(new Field.Const(minimum), FieldMacros.scale(noise, maximum - minimum)));
        return new Term(overlay, FieldMacros.scale(gradient.x(), maximum - minimum), FieldMacros.scale(gradient.z(), maximum - minimum));
    }

    static SortedMap<IrisInterpolator, SortedMap<String, IrisGenerator>> buckets(Map<String, IrisBiome> biomes, IrisData data) {
        SortedMap<IrisInterpolator, SortedMap<String, IrisGenerator>> buckets = new TreeMap<>(INTERPOLATOR_ORDER);
        for (IrisBiome biome : biomes.values()) {
            for (IrisBiomeGeneratorLink link : TerrainBiomes.links(biome)) {
                IrisGenerator generator = TerrainBiomes.generator(link, data);
                IrisInterpolator interpolator = generator.getInterpolator() == null ? IrisInterpolator.DEFAULT : generator.getInterpolator();
                buckets.computeIfAbsent(interpolator, ignored -> new TreeMap<>()).putIfAbsent(generator.getLoadKey(), generator);
            }
        }
        return buckets;
    }

    private static Field band(BandTreeCompiler bands, Map<String, Double> values, IrisInterpolator interpolator, int step) {
        if (BandTreeCompiler.blends(interpolator)) {
            return bands.band(values, interpolator);
        }
        Field hard = bands.hard(values);
        return hard instanceof Field.Const ? hard : new Field.Grid(hard, step, 1);
    }

    static Field clamp(Field height, double minimum, double maximum) {
        if (height instanceof Field.Const constant) {
            return new Field.Const(Math.max(minimum, Math.min(maximum, constant.value())));
        }
        return new Field.Clamp(height, minimum, maximum);
    }

    record Term(Field height, Field eastward, Field southward) {
    }
}
