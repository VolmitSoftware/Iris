package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

public final class SelectorCompiler {
    private static final double COHERENT_BLOCKS = 64.0;
    private static final long CALIBRATION_SEED = 0x1215_5E1EL;
    private static final int CURVE_KNOTS = 9;
    private static final String SHARED_PREFIX = "biome/selector/";

    private final StyleCompiler styles;
    private final IrisData data;
    private final NoiseRegistry registry;
    private final ExportCapabilities capabilities;
    private final RarityCalibrator calibrator;
    private final Map<SelectorKey, Selector> selectors = new HashMap<>();
    private int shared;

    public SelectorCompiler(StyleCompiler styles, Sources sources) {
        this.styles = Objects.requireNonNull(styles);
        Objects.requireNonNull(sources);
        data = sources.data();
        registry = sources.registry();
        capabilities = sources.capabilities();
        calibrator = new RarityCalibrator(registry, capabilities.target());
    }

    public Selector zoomed(IrisGeneratorStyle style, double zoom, long salt) {
        return selectors.computeIfAbsent(new SelectorKey(style, false, zoom, salt), this::create);
    }

    public Selector scaled(IrisGeneratorStyle style, double scale, long salt) {
        return selectors.computeIfAbsent(new SelectorKey(style, true, scale, salt), this::create);
    }

    public Selector sampled(IrisGeneratorStyle style, double zoom, long salt, DoubleBinaryOperator iris) {
        IrisGeneratorStyle exported = exportable(style);
        exported.setZoom(exported.getZoom() * zoom);
        return compile(exported, salt, iris, COHERENT_BLOCKS * exported.getZoom());
    }

    private Selector create(SelectorKey key) {
        IrisGeneratorStyle exported = exportable(key.style());
        RNG seed = new RNG(CALIBRATION_SEED).nextParallelRNG(key.salt());
        if (key.scaled()) {
            CNG iris = exported.createScaledGenerator(seed, data, key.factor());
            IrisGeneratorStyle compiled = scaledCopy(exported, key.factor());
            return compile(compiled, key.salt(), iris::noiseFast2D, COHERENT_BLOCKS * compiled.getZoom());
        }
        CNG iris = exported.create(seed, data);
        double zoom = key.factor();
        IrisGeneratorStyle compiled = copy(exported);
        compiled.setZoom(exported.getZoom() * zoom);
        return compile(compiled, key.salt(), (x, z) -> iris.noise(x / zoom, z / zoom), COHERENT_BLOCKS * compiled.getZoom());
    }

    private Selector compile(IrisGeneratorStyle style, long salt, DoubleBinaryOperator iris, double featureBlocks) {
        Field field = styles.compile(style, salt, registry, capabilities);
        FieldValidator.requireTwoDimensional(field, "biome selector");
        RarityCalibrator.Calibration calibration = calibrator.calibrate(iris, field, featureBlocks, salt);
        Field selector = field instanceof Field.Const ? field : new Field.Shared(SHARED_PREFIX + shared++, field);
        return new Selector(selector, calibration);
    }

    private static IrisGeneratorStyle exportable(IrisGeneratorStyle style) {
        IrisGeneratorStyle copy = copy(style);
        for (IrisGeneratorStyle current = copy; current != null; current = current.getFracture()) {
            current.setImageMap(null);
            current.setCacheSize(0);
        }
        return copy;
    }

    private static IrisGeneratorStyle scaledCopy(IrisGeneratorStyle style, double scale) {
        IrisGeneratorStyle copy = copy(style);
        copy.setZoom(style.getZoom() / scale);
        IrisGeneratorStyle fracture = copy.getFracture();
        if (fracture != null) {
            fracture.setZoom(fracture.getZoom() * scale);
            fracture.setMultiplier(fracture.getMultiplier() * scale);
        }
        return copy;
    }

    private static IrisGeneratorStyle copy(IrisGeneratorStyle style) {
        IrisGeneratorStyle copy = new IrisGeneratorStyle(style.getStyle());
        copy.setCellularFrequency(style.getCellularFrequency());
        copy.setCellularZoom(style.getCellularZoom());
        copy.setZoom(style.getZoom());
        copy.setExpression(style.getExpression());
        copy.setImageMap(style.getImageMap());
        copy.setMultiplier(style.getMultiplier());
        copy.setExponent(style.getExponent());
        copy.setCacheSize(style.getCacheSize());
        copy.setFracture(style.getFracture() == null ? null : copy(style.getFracture()));
        return copy;
    }

    public record Sources(IrisData data, NoiseRegistry registry, ExportCapabilities capabilities) {
        public Sources {
            Objects.requireNonNull(data);
            Objects.requireNonNull(registry);
            Objects.requireNonNull(capabilities);
        }
    }

    public record Selector(Field field, RarityCalibrator.Calibration calibration) {
        public Selector {
            Objects.requireNonNull(field);
            Objects.requireNonNull(calibration);
        }

        IndexedField partition(List<Double> irisCuts, List<Integer> leaves) {
            if (leaves.size() != irisCuts.size() + 1) {
                throw new IllegalArgumentException("Selector partition needs " + (irisCuts.size() + 1) + " leaves, got " + leaves.size());
            }
            List<Double> upper = new ArrayList<>(leaves.size());
            List<Integer> kept = new ArrayList<>(leaves.size());
            double lower = 0.0;
            for (int index = 0; index < leaves.size(); index++) {
                double bound = index == irisCuts.size() ? 1.0 : Math.max(lower, calibration.cumulative(irisCuts.get(index)));
                if (bound > lower) {
                    upper.add(bound);
                    kept.add(leaves.get(index));
                }
                lower = bound;
            }
            if (kept.isEmpty()) {
                return IndexedField.constant(leaves.getLast());
            }
            List<Double> thresholds = new ArrayList<>(kept.size() - 1);
            for (int index = 0; index + 1 < kept.size(); index++) {
                thresholds.add(calibration.threshold(upper.get(index)));
            }
            return IndexedField.partition(field, thresholds, kept);
        }

        Field curve() {
            List<Field.Knot> knots = new ArrayList<>(CURVE_KNOTS);
            double previousLocation = Double.NEGATIVE_INFINITY;
            List<double[]> points = new ArrayList<>(CURVE_KNOTS);
            for (int index = 0; index < CURVE_KNOTS; index++) {
                double probability = (index + 0.5) / CURVE_KNOTS;
                double location = calibration.threshold(probability);
                if (location <= previousLocation) {
                    continue;
                }
                points.add(new double[]{location, calibration.irisQuantile(probability)});
                previousLocation = location;
            }
            if (points.size() == 1) {
                return new Field.Const(points.getFirst()[1]);
            }
            for (int index = 0; index < points.size(); index++) {
                double derivative = 0.0;
                if (index > 0 && index + 1 < points.size()) {
                    double[] before = points.get(index - 1);
                    double[] after = points.get(index + 1);
                    derivative = (after[1] - before[1]) / (after[0] - before[0]);
                }
                knots.add(new Field.Knot(points.get(index)[0], new Field.Const(points.get(index)[1]), derivative));
            }
            return new Field.Curve(field, knots);
        }
    }

    private record SelectorKey(IrisGeneratorStyle style, boolean scaled, double factor, long salt) {
    }
}
