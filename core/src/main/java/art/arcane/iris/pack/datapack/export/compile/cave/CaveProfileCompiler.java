package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.value.IrisRange;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CaveProfileCompiler {
    static final double SOLID = 1.0;
    private static final long BASE_SALT = 934_447L;
    private static final long DETAIL_SALT = 612_991L;
    private static final long THRESHOLD_SALT = 489_112L;
    private static final long SURFACE_BREAK_SALT = 341_219L;
    private static final long MODULE_SALT = 1_000_003L;
    private static final long MODULE_SALT_STEP = 65_537L;
    private static final double LOW_WEIGHT = 0.1;
    private static final int CEILING_FADE_DEPTH = 12;
    private static final double CLOSURE_EPSILON = 1.0e-6;
    private static final double SEALED_MARGIN = 1.0;
    private static final Set<NoiseStyle> CELLULAR_STYLES = cellularStyles();

    private final StyleCompiler styles;
    private final CaveInputs inputs;
    private final Field blockY;

    public CaveProfileCompiler(StyleCompiler styles, CaveInputs inputs) {
        this.styles = Objects.requireNonNull(styles);
        this.inputs = Objects.requireNonNull(inputs);
        this.blockY = inputs.blockY();
    }

    public Field compile(CaveSource source, double gain) {
        if (!(gain > 0.0) || Double.isInfinite(gain)) {
            throw new IllegalArgumentException("Cave field gain must be finite and positive, got " + gain);
        }
        IrisCaveProfile profile = source.profile();
        IrisRange range = profile.getVerticalRange();
        int floor = floor(inputs, source);
        int ceiling = Math.min(source.maxY(), Math.min(inputs.maxY(), inputs.minY() + (int) Math.ceil(range.getMax())));
        if (ceiling < floor) {
            return new Field.Const(SOLID);
        }
        double normalization = normalization(profile) / gain;
        List<Field> terms = densityTerms(source, normalization, floor, ceiling);
        edgeFade(profile, floor, ceiling, gain).ifPresent(terms::add);
        terms.add(surface(source, closure(profile, normalization), gain));
        return bounded(sum(terms), floor, ceiling);
    }

    Optional<CaveInterior> interior(CaveSource source) {
        IrisCaveProfile profile = source.profile();
        int floor = floor(inputs, source);
        int ceiling = Math.min(source.maxY(), Math.min(inputs.maxY(), inputs.minY() + (int) Math.ceil(profile.getVerticalRange().getMax())));
        if (ceiling < floor) {
            return Optional.empty();
        }
        List<Field> terms = densityTerms(source, normalization(profile), floor, ceiling);
        terms.add(negativeThreshold(profile, 1.0));
        return Optional.of(new CaveInterior(sum(terms), floor, ceiling));
    }

    static int floor(CaveInputs inputs, CaveSource source) {
        return Math.max(source.minY(), inputs.minY() + Math.max(0, (int) Math.floor(source.profile().getVerticalRange().getMin())));
    }

    static boolean cellular(IrisGeneratorStyle style) {
        if (style.getExpression() != null || style.getImageMap() != null) {
            return false;
        }
        if (style.getCellularFrequency() > 0.0 || CELLULAR_STYLES.contains(style.getStyle())) {
            return true;
        }
        return style.getFracture() != null && cellular(style.getFracture());
    }

    private List<Field> densityTerms(CaveSource source, double normalization, int floor, int ceiling) {
        IrisCaveProfile profile = source.profile();
        List<Field> terms = new ArrayList<>();
        addTerm(terms, source, new Term(profile.getBaseDensityStyle(), BASE_SALT, profile.getBaseWeight(), 0.0, false), normalization);
        addTerm(terms, source, new Term(profile.getDetailDensityStyle(), DETAIL_SALT, profile.getDetailWeight(), 0.0, false), normalization);
        List<IrisCaveFieldModule> modules = profile.getModules();
        for (int index = 0; index < modules.size(); index++) {
            IrisCaveFieldModule module = modules.get(index);
            IrisRange range = module.getVerticalRange();
            int low = inputs.minY() + (int) Math.floor(range.getMin());
            int high = inputs.minY() + (int) Math.ceil(range.getMax());
            if (module.getWeight() == 0.0 || high < floor || low > ceiling) {
                continue;
            }
            Field term = term(source, new Term(module.getStyle(), MODULE_SALT + index * MODULE_SALT_STEP, module.getWeight(), module.getThreshold(), module.isInvert()), normalization);
            terms.add(low <= floor && high >= ceiling ? term : new Field.Gate(window(low, high), term));
        }
        return terms;
    }

    private void addTerm(List<Field> terms, CaveSource source, Term term, double normalization) {
        if (term.weight() != 0.0) {
            terms.add(term(source, term, normalization));
        }
    }

    private Field term(CaveSource source, Term term, double normalization) {
        double sign = term.invert() ? -1.0 : 1.0;
        double scale = sign * term.weight() / normalization;
        Field unit = volume(source, term.style(), term.salt(), Math.abs(term.weight()) < LOW_WEIGHT);
        return affine(unit, 2.0 * scale, -scale * (1.0 + term.threshold()));
    }

    private Field volume(CaveSource source, IrisGeneratorStyle style, long salt, boolean lowWeight) {
        boolean cellularStyle = cellular(style);
        if (cellularStyle && lowWeight) {
            return VolumeLift.lift(styles.compile(substitute(style), salt, inputs.noises(), inputs.capabilities()));
        }
        Field compiled = styles.compile(style, salt, inputs.noises(), inputs.capabilities());
        if (compiled.axes().contains(Axis.Y)) {
            return compiled;
        }
        if (!cellularStyle) {
            return VolumeLift.lift(compiled);
        }
        inputs.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.NOISE_STYLE, source.resource(),
                "Cellular cave style " + style.getStyle() + " exports as smooth noise"));
        return VolumeLift.lift(styles.compile(substitute(style), salt, inputs.noises(), inputs.capabilities()));
    }

    private Field planar(IrisGeneratorStyle style, long salt) {
        Field compiled = styles.compile(style, salt, inputs.noises(), inputs.capabilities());
        return compiled.axes().contains(Axis.Y) ? new Field.Slice(compiled, Axis.Y, 0) : compiled;
    }

    private Field surface(CaveSource source, double closure, double gain) {
        IrisCaveProfile profile = source.profile();
        Field negativeThreshold = negativeThreshold(profile, gain);
        Field depthBelowClearance = new Field.Binary(Field.BinaryOp.SUB,
                new Field.Binary(Field.BinaryOp.ADD, inputs.surface(), new Field.Const(-Math.max(0, profile.getSurfaceClearance()))),
                blockY);
        Field sealed = new Field.Curve(depthBelowClearance, List.of(
                new Field.Knot(-1.0, new Field.Const(SEALED_MARGIN * gain - closure), 0.0),
                new Field.Knot(0.0, new Field.Const(-closure), 0.0),
                new Field.Knot(CEILING_FADE_DEPTH, negativeThreshold, 0.0)));
        if (!profile.isAllowSurfaceBreak()) {
            return sealed;
        }
        Field breaking = new Field.Binary(Field.BinaryOp.ADD, breakSteps(profile, closure, gain), negativeThreshold);
        Field breakNoise = planar(profile.getSurfaceBreakStyle(), SURFACE_BREAK_SALT);
        Field cutoff = new Field.Const((profile.getSurfaceBreakNoiseThreshold() + 1.0) / 2.0);
        return new Field.Binary(Field.BinaryOp.ADD,
                new Field.Gate(new Field.Binary(Field.BinaryOp.SUB, cutoff, breakNoise), sealed),
                new Field.Gate(new Field.Binary(Field.BinaryOp.SUB, breakNoise, cutoff), breaking));
    }

    private Field breakSteps(IrisCaveProfile profile, double closure, double gain) {
        int depth = Math.max(0, profile.getSurfaceBreakDepth());
        double boost = Math.max(0.0, profile.getSurfaceBreakThresholdBoost()) * gain;
        Field belowSurface = new Field.Binary(Field.BinaryOp.SUB, inputs.surface(), blockY);
        List<Field.Knot> knots = new ArrayList<>();
        knots.add(new Field.Knot(-1.0, new Field.Const((thresholdCeiling(profile) + SEALED_MARGIN) * gain - closure + boost), 0.0));
        knots.add(new Field.Knot(0.0, new Field.Const(-boost), 0.0));
        if (boost > 0.0) {
            if (depth > 0) {
                knots.add(new Field.Knot(depth, new Field.Const(-boost), 0.0));
            }
            knots.add(new Field.Knot(depth + 1.0, new Field.Const(0.0), 0.0));
        }
        return new Field.Curve(belowSurface, knots);
    }

    private Field negativeThreshold(IrisCaveProfile profile, double gain) {
        IrisStyledRange range = profile.getDensityThreshold();
        double bias = profile.getThresholdBias();
        if (range.getMin() == range.getMax()) {
            return new Field.Const(gain * (bias - range.getMin()));
        }
        if (range.getStyle().isFlat()) {
            return new Field.Const(gain * (bias - (range.getMin() + 0.5 * (range.getMax() - range.getMin()))));
        }
        return affine(planar(range.getStyle(), THRESHOLD_SALT), gain * (range.getMin() - range.getMax()), gain * (bias - range.getMin()));
    }

    private Optional<Field> edgeFade(IrisCaveProfile profile, int floor, int ceiling, double gain) {
        int fade = Math.max(0, profile.getVerticalEdgeFade());
        double strength = Math.max(0.0, profile.getVerticalEdgeFadeStrength()) * gain;
        if (fade <= 0 || strength <= 0.0 || ceiling <= floor) {
            return Optional.empty();
        }
        List<Field.Knot> knots = new ArrayList<>();
        knots.add(new Field.Knot(floor, new Field.Const(strength), 0.0));
        if (floor + fade <= ceiling - fade) {
            knots.add(new Field.Knot(floor + fade, new Field.Const(0.0), 0.0));
            if (ceiling - fade > floor + fade) {
                knots.add(new Field.Knot(ceiling - fade, new Field.Const(0.0), 0.0));
            }
        } else {
            double middle = floor + (ceiling - floor) / 2.0;
            double progress = Math.min(middle - floor, ceiling - middle) / fade;
            knots.add(new Field.Knot(middle, new Field.Const((1.0 - smoothstep(progress)) * strength), 0.0));
        }
        knots.add(new Field.Knot(ceiling, new Field.Const(strength), 0.0));
        return Optional.of(new Field.Curve(blockY, knots));
    }

    private Field bounded(Field core, int floor, int ceiling) {
        if (floor <= inputs.minY() && ceiling >= inputs.maxY()) {
            return core;
        }
        Field shifted = new Field.Binary(Field.BinaryOp.ADD, core, new Field.Const(-SOLID));
        return new Field.Binary(Field.BinaryOp.ADD, new Field.Gate(window(floor, ceiling), shifted), new Field.Const(SOLID));
    }

    private Field window(int low, int high) {
        return new Field.RangeIf(blockY, low, high + 1.0, new Field.Const(1.0), new Field.Const(0.0));
    }

    private static Field affine(Field input, double scale, double offset) {
        if (input instanceof Field.Const constant) {
            return new Field.Const(scale * constant.value() + offset);
        }
        if (scale == 0.0) {
            return new Field.Const(offset);
        }
        return new Field.Curve(input, List.of(
                new Field.Knot(0.0, new Field.Const(offset), scale),
                new Field.Knot(1.0, new Field.Const(scale + offset), scale)));
    }

    private static Field sum(List<Field> terms) {
        Field total = terms.getFirst();
        for (int index = 1; index < terms.size(); index++) {
            total = new Field.Binary(Field.BinaryOp.ADD, total, terms.get(index));
        }
        return total;
    }

    private static double normalization(IrisCaveProfile profile) {
        double weight = Math.abs(profile.getBaseWeight()) + Math.abs(profile.getDetailWeight());
        for (IrisCaveFieldModule module : profile.getModules()) {
            weight += Math.abs(module.getWeight());
        }
        return weight <= 0.0 ? 1.0 : weight;
    }

    private static double closure(IrisCaveProfile profile, double normalization) {
        double minimum = -Math.abs(profile.getBaseWeight()) - Math.abs(profile.getDetailWeight());
        for (IrisCaveFieldModule module : profile.getModules()) {
            double threshold = module.getThreshold();
            double low = module.isInvert() ? threshold - 1.0 : -1.0 - threshold;
            double high = module.isInvert() ? threshold + 1.0 : 1.0 - threshold;
            minimum += Math.min(low * module.getWeight(), high * module.getWeight());
        }
        return minimum / normalization - CLOSURE_EPSILON;
    }

    private static double thresholdCeiling(IrisCaveProfile profile) {
        IrisStyledRange range = profile.getDensityThreshold();
        return Math.max(range.getMin(), range.getMax()) - profile.getThresholdBias();
    }

    private static double smoothstep(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static IrisGeneratorStyle substitute(IrisGeneratorStyle style) {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(style.getZoom());
    }

    private static Set<NoiseStyle> cellularStyles() {
        Set<NoiseStyle> cellular = EnumSet.noneOf(NoiseStyle.class);
        for (NoiseStyle style : NoiseStyle.values()) {
            String name = style.name();
            if (name.contains("CELL") || name.contains("VASCULAR") || name.contains("GLOB") || name.contains("HEX")) {
                cellular.add(style);
            }
        }
        return cellular;
    }

    private record Term(IrisGeneratorStyle style, long salt, double weight, double threshold, boolean invert) {
    }
}
