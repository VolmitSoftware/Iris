package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseType;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class NoiseStyleCompiler implements StyleCompiler {
    private static final long BUILD_SEED = 0x1215_57E1L;
    private static final long FIT_SALT = 0x0F17_0F17L;
    private static final String FIT_NAMESPACE = "iris_fit";
    private static final String NOISE_PREFIX = "style/";
    private static final String SHARED_PREFIX = "style/shared/";
    private static final double SINE_KNOT_SPACING = 1.0 / 16.0;
    private static final double SINE_DOMAIN_LIMIT = 16.0;

    private final CellularStyleLowering cellular;
    private final StyleCompiler selectors;
    private final Map<LeafKey, LeafNoiseFit.FittedLeaf> leaves = new ConcurrentHashMap<>();
    private final Map<LevelKey, WarpFit.LevelFit> levels = new ConcurrentHashMap<>();

    public NoiseStyleCompiler(CellularStyleLowering cellular) {
        this.cellular = Objects.requireNonNull(cellular, "cellular");
        this.selectors = this;
    }

    private NoiseStyleCompiler(Function<StyleCompiler, CellularStyleLowering> cellular, StyleCompiler selectors) {
        this.cellular = Objects.requireNonNull(cellular.apply(this), "cellular");
        this.selectors = selectors == null ? this : selectors;
    }

    public static NoiseStyleCompiler create(List<ExportDiagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        NoiseStyleCompiler selectors = new NoiseStyleCompiler(nested -> new DefaultCellularStyleLowering(diagnostics, nested::compile, CellularMode.QUADRANT_2X2), null);
        return new NoiseStyleCompiler(nested -> new DefaultCellularStyleLowering(diagnostics, nested::compile, CellularMode.GRID_3X3), selectors);
    }

    public static CNG generator(IrisGeneratorStyle style) {
        return build(prepared(Objects.requireNonNull(style, "style"), 1.0), 1);
    }

    @Override
    public Field compile(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Emit emit = new Emit(registry, capabilities, seedSalt);
        return style(Objects.requireNonNull(style, "style"), 1, Domain.IDENTITY, emit, "s");
    }

    @Override
    public StyleCompiler selectors() {
        return selectors;
    }

    public Field compileLayer(IrisNoiseGenerator layer, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Emit emit = new Emit(registry, capabilities, seedSalt);
        return layer(Objects.requireNonNull(layer, "layer"), Domain.IDENTITY, emit, "l" + layer.getSeed());
    }

    public Field compileComposite(IrisGenerator generator, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Objects.requireNonNull(generator, "generator");
        Emit emit = new Emit(registry, capabilities, seedSalt);
        KList<IrisNoiseGenerator> composite = generator.getComposite();
        if (composite.isEmpty()) {
            return new Field.Const(0.0);
        }
        Domain domain = new Domain(positive(generator.getZoom(), "generator zoom"), generator.getOffsetX(), generator.getOffsetZ(), null, null);
        Field combined = null;
        double totalOpacity = 0.0;
        for (int index = 0; index < composite.size(); index++) {
            IrisNoiseGenerator layer = composite.get(index);
            Field value = layer(layer, domain, emit, "g" + index + "_" + layer.getSeed());
            if (generator.isMultiplicitive()) {
                combined = combined == null ? value : binary(Field.BinaryOp.MUL, combined, value);
            } else {
                totalOpacity += layer.getOpacity();
                combined = combined == null ? value : binary(Field.BinaryOp.ADD, combined, value);
            }
        }
        if (generator.isMultiplicitive()) {
            return scaled(combined, generator.getOpacity());
        }
        if (totalOpacity == 0.0) {
            throw new IllegalArgumentException("Generator '" + generator.getLoadKey() + "' composite opacity sums to zero");
        }
        return scaled(combined, generator.getOpacity() / totalOpacity);
    }

    public Field compileRange(IrisStyledRange range, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Objects.requireNonNull(range, "range");
        if (range.getMin() == range.getMax()) {
            return new Field.Const(range.getMin());
        }
        if (range.getStyle().isFlat()) {
            return new Field.Const(range.getMin() + 0.5 * (range.getMax() - range.getMin()));
        }
        Field unit = compile(range.getStyle(), seedSalt, registry, capabilities);
        return binary(Field.BinaryOp.ADD, new Field.Const(range.getMin()), scaled(unit, range.getMax() - range.getMin()));
    }

    public Optional<Warp> compileRootWarp(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Emit emit = new Emit(registry, capabilities, seedSalt);
        CNG root = generator(style);
        if (!warped(root)) {
            return Optional.empty();
        }
        String path = "w" + styleName(style);
        Field warpX = displacement(node(root.getFracture(), Displacement.NONE, emit, path + "/x"), root.getFscale());
        Field warpZ = displacement(node(root.getFracture(), Displacement.NONE, emit, path + "/z"), root.getFscale());
        return Optional.of(new Warp(warpX, warpZ));
    }

    private Field layer(IrisNoiseGenerator layer, Domain domain, Emit emit, String path) {
        if (!layer.isEnabled()) {
            return new Field.Const(layer.getOffsetY());
        }
        Field warpX = null;
        Field warpZ = null;
        List<IrisNoiseGenerator> fractures = layer.getFracture();
        for (int index = 0; index < fractures.size(); index++) {
            IrisNoiseGenerator child = fractures.get(index);
            if (!child.isEnabled()) {
                continue;
            }
            Field center = new Field.Const(-child.getOpacity() / 2.0);
            String childPath = path + "/f" + index + "_" + child.getSeed();
            warpX = sum(warpX, binary(Field.BinaryOp.ADD, layer(child, domain, emit, childPath + "x"), center));
            warpZ = sum(warpZ, binary(Field.BinaryOp.ADD, layer(child, domain.swapped(), emit, childPath + "z"), center));
        }
        Domain sampled = domain.layer(positive(layer.getZoom(), "layer zoom"), layer.getOffsetX(), layer.getOffsetZ(), warpX, warpZ);
        Field value = style(layer.getStyle(), layer.getOctaves(), sampled, emit, path);
        return layerPost(layer, value, emit, path);
    }

    private Field layerPost(IrisNoiseGenerator layer, Field input, Emit emit, String path) {
        double opacity = layer.getOpacity();
        Field value = scaled(input, opacity);
        if (layer.isNegative()) {
            value = binary(Field.BinaryOp.SUB, new Field.Const(opacity), value);
        }
        if (layer.getExponent() != 1.0) {
            value = signedPower(value, layer.getExponent(), emit, path + "/e");
        }
        if (layer.getOffsetY() != 0.0) {
            value = binary(Field.BinaryOp.ADD, value, new Field.Const(layer.getOffsetY()));
        }
        if (layer.isParametric()) {
            Field shared = emit.shared(path + "/p", value);
            Field square = new Field.Unary(Field.UnaryOp.SQUARE, shared);
            Field complement = new Field.Unary(Field.UnaryOp.SQUARE, binary(Field.BinaryOp.SUB, new Field.Const(1.0), shared));
            value = binary(Field.BinaryOp.DIV, square, binary(Field.BinaryOp.ADD, square, complement));
        }
        if (layer.isBezier()) {
            Field shared = emit.shared(path + "/b", value);
            Field weight = binary(Field.BinaryOp.SUB, new Field.Const(3.0), scaled(shared, 2.0));
            value = binary(Field.BinaryOp.MUL, new Field.Unary(Field.UnaryOp.SQUARE, shared), weight);
        }
        if (layer.isSinCentered()) {
            value = sineCentered(value);
        }
        return value;
    }

    private Field style(IrisGeneratorStyle style, int octaveMultiplier, Domain domain, Emit emit, String path) {
        IrisGeneratorStyle prepared = prepared(Objects.requireNonNull(style, "noise style"), domain.zoom());
        String stylePath = path + "/" + styleName(style);
        CNG root = build(prepared, octaveMultiplier);
        if (cellular(prepared, root)) {
            if (domain.warpX() != null || domain.warpZ() != null) {
                throw new IllegalArgumentException("Cellular noise style " + styleName(style) + " cannot be exported under a layer fracture warp");
            }
            return cellular.lower(prepared, emit.salt(stylePath), emit.registry(), emit.capabilities());
        }
        Displacement displacement = new Displacement(domain.offsetX(), domain.offsetZ(), domain.warpX(), domain.warpZ());
        return node(root, displacement, emit, stylePath);
    }

    private Field node(CNG cng, Displacement displacement, Emit emit, String path) {
        WarpFit.LevelFit fit = fitted(cng) ? levelFit(cng, emit.capabilities().target()) : null;
        return fit == null ? node(cng, displacement, emit, path, 1.0, null) : node(cng, displacement, emit, path, fit.multiplier(), fit.distribution());
    }

    private Field node(CNG cng, Displacement displacement, Emit emit, String path, double multiplier, List<Field.Knot> distribution) {
        requireSupported(cng);
        NoiseType type = NoiseLeaves.typeOf(cng.getGenerator());
        Field value;
        if (type == NoiseType.FLAT) {
            value = new Field.Const(1.0);
        } else {
            Displacement leafDisplacement = displacement;
            if (warped(cng)) {
                Field warpX = displacement(node(cng.getFracture(), displacement, emit, path + "/x"), cng.getFscale());
                Field warpZ = displacement(node(cng.getFracture(), displacement.swapped(), emit, path + "/z"), cng.getFscale());
                leafDisplacement = displacement.warped(warpX, warpZ);
            }
            value = leaf(type, cng.getOct(), cng.getEffectiveScale() * multiplier, leafDisplacement, emit, path);
            if (distribution != null) {
                value = new Field.Curve(value, distribution);
            }
        }
        return post(cng, value, emit, path);
    }

    private Field leaf(NoiseType type, int octaves, double scale, Displacement displacement, Emit emit, String path) {
        NoiseLeaves.Fold fold = NoiseLeaves.fold(type);
        int effectiveOctaves = NoiseLeaves.effectiveOctaves(type, octaves);
        DataVersion target = emit.capabilities().target();
        if (fold.kind() == NoiseLeaves.FoldKind.NONE) {
            LeafNoiseFit.FittedLeaf fitted = fitted(type, effectiveOctaves, target);
            return fitted.curve(noise(fitted, fitted.xzScale() * scale, displacement, emit, path));
        }
        LeafNoiseFit.FittedLeaf base = fitted(fold.base(), 1, target);
        List<Field.Knot> signed = signed(base.distribution());
        Displacement shared = effectiveOctaves > 1 ? displacement.shared(emit, path) : displacement;
        double bounding = bounding(effectiveOctaves);
        Field sum = fold.kind() == NoiseLeaves.FoldKind.RIGID ? new Field.Const(bounding) : null;
        double amplitude = 1.0;
        for (int octave = 0; octave < effectiveOctaves; octave++) {
            double octaveScale = base.xzScale() * scale * Math.scalb(1.0, octave);
            Field noise = noise(base, octaveScale, shared, emit, path + "/o" + octave);
            Field folded = new Field.Unary(Field.UnaryOp.ABS, new Field.Curve(noise, signed));
            double weight = fold.kind() == NoiseLeaves.FoldKind.RIGID && octave == 0 ? -bounding : bounding * amplitude;
            sum = sum(sum, scaled(folded, weight));
            amplitude *= 0.5;
        }
        return sum;
    }

    private Field noise(LeafNoiseFit.FittedLeaf leaf, double xzScale, Displacement displacement, Emit emit, String path) {
        String id = emit.noise(leaf, path);
        return new Field.Noise(id, xzScale, 0.0, shift(displacement.offsetX(), displacement.warpX(), xzScale), null,
                shift(displacement.offsetZ(), displacement.warpZ(), xzScale));
    }

    private Field post(CNG cng, Field input, Emit emit, String path) {
        Field value = scaled(input, cng.getOpacity());
        if (cng.getPower() != 1.0) {
            value = signedPower(value, cng.getPower(), emit, path + "/power");
        }
        double offset = cng.getUp() - cng.getDown();
        if (offset != 0.0) {
            value = binary(Field.BinaryOp.ADD, value, new Field.Const(offset));
        }
        return scaled(value, cng.getPatch());
    }

    private WarpFit.LevelFit levelFit(CNG level, DataVersion target) {
        LevelSpec spec = LevelSpec.of(level).canonical();
        LevelKey key = new LevelKey(spec, target);
        WarpFit.LevelFit cached = levels.get(key);
        if (cached != null) {
            return cached;
        }
        NoiseRegistry scratch = new NoiseRegistry(FIT_NAMESPACE);
        Emit fitEmit = new Emit(scratch, ExportCapabilities.forTarget(target), FIT_SALT);
        CNG iris = spec.build(LeafNoiseFit.FIT_SEED);
        WarpFit.LevelFit fit = WarpFit.fit((x, z) -> iris.noise(x, z),
                candidate -> node(iris, Displacement.NONE, fitEmit, "fit", candidate, null), scratch, target);
        WarpFit.LevelFit existing = levels.putIfAbsent(key, fit);
        return existing == null ? fit : existing;
    }

    private LeafNoiseFit.FittedLeaf fitted(NoiseType type, int octaves, DataVersion target) {
        LeafKey key = new LeafKey(type, octaves, target);
        LeafNoiseFit.FittedLeaf cached = leaves.get(key);
        if (cached != null) {
            return cached;
        }
        LeafNoiseFit.FittedLeaf fitted = LeafNoiseFit.fit(type, octaves, target);
        LeafNoiseFit.FittedLeaf existing = leaves.putIfAbsent(key, fitted);
        return existing == null ? fitted : existing;
    }

    private static Field signedPower(Field value, double power, Emit emit, String path) {
        Field exponent = new Field.Const(power);
        if (value.range().min() >= 0.0) {
            return binary(Field.BinaryOp.POW, value, exponent);
        }
        Field shared = emit.shared(path, value);
        Field magnitude = binary(Field.BinaryOp.POW, new Field.Unary(Field.UnaryOp.ABS, shared), exponent);
        return binary(Field.BinaryOp.MUL, new Field.Unary(Field.UnaryOp.SIGN, shared), magnitude);
    }

    private static Field sineCentered(Field value) {
        double lower = Math.max(-SINE_DOMAIN_LIMIT, Math.floor(clampFinite(value.range().min(), -1.0) / SINE_KNOT_SPACING) * SINE_KNOT_SPACING);
        double upper = Math.min(SINE_DOMAIN_LIMIT, Math.ceil(clampFinite(value.range().max(), 2.0) / SINE_KNOT_SPACING) * SINE_KNOT_SPACING);
        int count = (int) Math.round((upper - lower) / SINE_KNOT_SPACING) + 1;
        List<Field.Knot> knots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double location = lower + index * SINE_KNOT_SPACING;
            knots.add(new Field.Knot(location, new Field.Const(Math.sin(location * Math.PI)), Math.PI * Math.cos(location * Math.PI)));
        }
        return new Field.Curve(value, knots);
    }

    private static double clampFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static List<Field.Knot> signed(List<Field.Knot> knots) {
        List<Field.Knot> signed = new ArrayList<>(knots.size());
        for (Field.Knot knot : knots) {
            double value = ((Field.Const) knot.value()).value();
            signed.add(new Field.Knot(knot.location(), new Field.Const(2.0 * value - 1.0), 2.0 * knot.derivative()));
        }
        return List.copyOf(signed);
    }

    private static double bounding(int octaves) {
        double amplitude = 0.5;
        double total = 1.0;
        for (int octave = 1; octave < octaves; octave++) {
            total += amplitude;
            amplitude *= 0.5;
        }
        return 1.0 / total;
    }

    private static Field displacement(Field fracture, double scale) {
        return binary(Field.BinaryOp.ADD, scaled(fracture, scale), new Field.Const(-0.5 * scale));
    }

    private static Field shift(double offset, Field warp, double xzScale) {
        if (warp == null) {
            return offset == 0.0 ? null : new Field.Const(offset * xzScale);
        }
        Field scaledWarp = scaled(warp, xzScale);
        return offset == 0.0 ? scaledWarp : binary(Field.BinaryOp.ADD, scaledWarp, new Field.Const(offset * xzScale));
    }

    private static Field scaled(Field field, double factor) {
        if (factor == 1.0) {
            return field;
        }
        return binary(Field.BinaryOp.MUL, new Field.Const(factor), field);
    }

    private static Field sum(Field total, Field addition) {
        return total == null ? addition : binary(Field.BinaryOp.ADD, total, addition);
    }

    private static Field binary(Field.BinaryOp op, Field left, Field right) {
        return new Field.Binary(op, left, right);
    }

    private static boolean fitted(CNG cng) {
        NoiseType type = NoiseLeaves.typeOf(cng.getGenerator());
        return type != NoiseType.FLAT && (warped(cng) || NoiseLeaves.fold(type).kind() != NoiseLeaves.FoldKind.NONE);
    }

    private static boolean warped(CNG cng) {
        return cng.getFracture() != null && !cng.isNoscale();
    }

    private static boolean cellular(IrisGeneratorStyle style, CNG root) {
        for (IrisGeneratorStyle current = style; current != null; current = current.getFracture()) {
            if (current.getCellularFrequency() > 0.0) {
                return true;
            }
        }
        for (CNG current = root; current != null; current = current.getFracture()) {
            if (NoiseLeaves.isCellular(NoiseLeaves.typeOf(current.getGenerator()))) {
                return true;
            }
        }
        return false;
    }

    private static void requireSupported(CNG cng) {
        if (cng.getChildren() != null && !cng.getChildren().isEmpty()) {
            throw new IllegalArgumentException("Noise graphs with injected children are not exportable");
        }
        if (cng.getCustomGenerator() != null) {
            throw new IllegalArgumentException("Noise graphs with custom generators are not exportable");
        }
    }

    private static CNG build(IrisGeneratorStyle style, int octaveMultiplier) {
        return style.createForLayer(new RNG(BUILD_SEED), null, octaveMultiplier);
    }

    private static IrisGeneratorStyle prepared(IrisGeneratorStyle style, double zoom) {
        if (style.getExpression() != null) {
            throw new IllegalArgumentException("Noise style expression '" + style.getExpression() + "' is not exportable");
        }
        IrisGeneratorStyle copy = new IrisGeneratorStyle(style.getStyle());
        copy.setCellularFrequency(style.getCellularFrequency());
        copy.setCellularZoom(style.getCellularZoom());
        copy.setZoom(style.getZoom() * zoom);
        copy.setMultiplier(style.getMultiplier());
        copy.setExponent(style.getExponent());
        copy.setCacheSize(0);
        copy.setFracture(style.getFracture() == null ? null : prepared(style.getFracture(), 1.0));
        return copy;
    }

    private static String styleName(IrisGeneratorStyle style) {
        return style.getStyle() == null ? "flat" : style.getStyle().name().toLowerCase(Locale.ROOT);
    }

    private static double positive(double value, String role) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(role + " must be finite and positive, got " + value);
        }
        return value;
    }

    public record Warp(Field x, Field z) {
        public Warp {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(z, "z");
        }
    }

    private record LeafKey(NoiseType type, int octaves, DataVersion target) {
    }

    private record LevelKey(LevelSpec spec, DataVersion target) {
    }

    private record LevelSpec(NoiseType type, int octaves, double scale, double opacity, double power, double offset, double patch,
                             double fractureScale, LevelSpec fracture) {
        private static LevelSpec of(CNG cng) {
            NoiseType type = NoiseLeaves.typeOf(cng.getGenerator());
            boolean warped = warped(cng);
            return new LevelSpec(type, NoiseLeaves.effectiveOctaves(type, cng.getOct()), cng.getEffectiveScale(), cng.getOpacity(),
                    cng.getPower(), cng.getUp() - cng.getDown(), cng.getPatch(), warped ? cng.getFscale() : 0.0,
                    warped ? of(cng.getFracture()) : null);
        }

        private LevelSpec canonical() {
            double factor = type == NoiseType.FLAT || scale == 0.0 ? 1.0 : type.getCoordinateScale() / scale;
            return new LevelSpec(type, octaves, rounded(scale * factor), 1.0, 1.0, 0.0, 1.0, rounded(fractureScale / factor),
                    fracture == null ? null : fracture.rescaled(factor));
        }

        private LevelSpec rescaled(double factor) {
            return new LevelSpec(type, octaves, rounded(scale * factor), opacity, power, offset, patch, rounded(fractureScale / factor),
                    fracture == null ? null : fracture.rescaled(factor));
        }

        private CNG build(long seed) {
            CNG cng = new CNG(new RNG(seed), type, opacity, octaves);
            cng.scale(scale).bake();
            cng.pow(power);
            cng.up(offset);
            cng.patch(patch);
            if (fracture != null) {
                cng.fractureWith(fracture.build(seed + 1L), fractureScale);
            }
            return cng;
        }

        private static double rounded(double value) {
            return (float) value;
        }
    }

    private record Domain(double zoom, double offsetX, double offsetZ, Field warpX, Field warpZ) {
        private static final Domain IDENTITY = new Domain(1.0, 0.0, 0.0, null, null);

        private Domain layer(double layerZoom, double layerOffsetX, double layerOffsetZ, Field layerWarpX, Field layerWarpZ) {
            double combined = zoom * layerZoom;
            return new Domain(combined, offsetX + combined * layerOffsetX, offsetZ + combined * layerOffsetZ,
                    sum(warpX, layerWarpX == null ? null : scaled(layerWarpX, zoom)),
                    sum(warpZ, layerWarpZ == null ? null : scaled(layerWarpZ, zoom)));
        }

        private Domain swapped() {
            return new Domain(zoom, offsetZ, offsetX, warpZ, warpX);
        }
    }

    private record Displacement(double offsetX, double offsetZ, Field warpX, Field warpZ) {
        private static final Displacement NONE = new Displacement(0.0, 0.0, null, null);

        private Displacement swapped() {
            return new Displacement(offsetZ, offsetX, warpZ, warpX);
        }

        private Displacement warped(Field addedX, Field addedZ) {
            return new Displacement(offsetX, offsetZ, sum(warpX, addedX), sum(warpZ, addedZ));
        }

        private Displacement shared(Emit emit, String path) {
            return new Displacement(offsetX, offsetZ, emit.shared(path + "/dx", warpX), emit.shared(path + "/dz", warpZ));
        }
    }

    private record Emit(NoiseRegistry registry, ExportCapabilities capabilities, long seedSalt) {
        private Emit {
            Objects.requireNonNull(registry, "registry");
            Objects.requireNonNull(capabilities, "capabilities");
        }

        private String noise(LeafNoiseFit.FittedLeaf leaf, String path) {
            NoiseDef template = leaf.noise();
            String id = NOISE_PREFIX + template.id() + "/" + HexFormat.of().toHexDigits(salt(path));
            return registry.register(new NoiseDef(id, template.firstOctave(), template.amplitudes()));
        }

        private Field shared(String path, Field expr) {
            if (expr == null || expr.cost() == CostClass.CHEAP) {
                return expr;
            }
            return new Field.Shared(SHARED_PREFIX + HexFormat.of().toHexDigits(mix(salt(path) ^ digest(expr.toString()))), expr);
        }

        private static long digest(String text) {
            long hash = 0xCBF29CE484222325L;
            for (int index = 0; index < text.length(); index++) {
                hash = (hash ^ text.charAt(index)) * 0x100000001B3L;
            }
            return hash;
        }

        private long salt(String path) {
            long hash = mix(seedSalt);
            for (int index = 0; index < path.length(); index++) {
                hash = mix(hash ^ path.charAt(index));
            }
            return hash;
        }

        private static long mix(long value) {
            long mixed = value + 0x9E3779B97F4A7C15L;
            mixed = (mixed ^ mixed >>> 30) * 0xBF58476D1CE4E5B9L;
            mixed = (mixed ^ mixed >>> 27) * 0x94D049BB133111EBL;
            return mixed ^ mixed >>> 31;
        }
    }
}
