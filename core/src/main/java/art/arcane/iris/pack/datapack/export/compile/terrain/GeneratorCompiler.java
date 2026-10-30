package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

public final class GeneratorCompiler {
    private static final String SHARED_PREFIX = "terrain/generator/";
    private static final String RESOURCE_PREFIX = "generators/";
    private static final String DEFAULT_KEY = "default";
    private static final int SURFACE_GRID = 6;
    private static final double CLIFF_RESOLUTION = 255.0;
    private static final double PARAMETRIC_ALPHA = 2.0;
    private static final int SEGMENTS_PER_UNIT = 16;
    private static final double CURVE_REACH = 4.0;

    private final StyleCompiler styles;
    private final NoiseRegistry registry;
    private final ExportCapabilities capabilities;
    private final List<ExportDiagnostic> diagnostics;
    private final Map<String, Field> compiled = new HashMap<>();
    private final Set<String> names = new HashSet<>();

    public GeneratorCompiler(StyleCompiler styles, NoiseRegistry registry, ExportCapabilities capabilities, List<ExportDiagnostic> diagnostics) {
        this.styles = Objects.requireNonNull(styles, "styles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public static long salt(String path) {
        long hash = mix(0x1F3D5B79A2C4E6F8L);
        for (int index = 0; index < path.length(); index++) {
            hash = mix(hash ^ path.charAt(index));
        }
        return hash;
    }

    public static IrisGeneratorStyle zoomed(IrisGeneratorStyle style, double factor) {
        IrisGeneratorStyle source = style == null ? new IrisGeneratorStyle(NoiseStyle.FLAT) : style;
        IrisGeneratorStyle copy = new IrisGeneratorStyle(source.getStyle());
        copy.setCellularFrequency(source.getCellularFrequency());
        copy.setCellularZoom(source.getCellularZoom());
        copy.setZoom(source.getZoom() * factor);
        copy.setExpression(source.getExpression());
        copy.setMultiplier(source.getMultiplier());
        copy.setFracture(source.getFracture());
        copy.setExponent(source.getExponent());
        copy.setCacheSize(source.getCacheSize());
        return copy;
    }

    public Field compile(IrisGenerator generator, String scope) {
        Objects.requireNonNull(generator, "generator");
        VanillaResourceIdentifier.requirePath(scope, "generator scope");
        String key = key(generator);
        String cacheKey = scope + "\u0000" + key;
        Field cached = compiled.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String name = name(scope, key);
        Field value = generator(generator, scope + "/" + key, name);
        Field result = value.cost() == CostClass.CHEAP ? value : new Field.Shared(name, value);
        compiled.put(cacheKey, result);
        return result;
    }

    public Field compileRaw(IrisGenerator generator, String scope) {
        Objects.requireNonNull(generator, "generator");
        VanillaResourceIdentifier.requirePath(scope, "generator scope");
        String key = key(generator);
        String cacheKey = scope + "\u0000raw\u0000" + key;
        Field cached = compiled.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String name = name(scope, key);
        Field result = FieldMacros.share(name + "/raw", shape(generator, scope + "/" + key, name));
        compiled.put(cacheKey, result);
        return result;
    }

    public Field style(IrisGeneratorStyle style, double zoom, String path) {
        return styles.compile(zoomed(style, zoom), salt(path), registry, capabilities);
    }

    private Field generator(IrisGenerator generator, String path, String name) {
        return surfaceDetail(shape(generator, path, name), generator.getSurfaceDetail(), name);
    }

    private Field shape(IrisGenerator generator, String path, String name) {
        List<IrisNoiseGenerator> composite = generator.getComposite();
        if (composite == null || composite.isEmpty()) {
            return new Field.Const(0.0);
        }
        Domain root = new Domain(positive(generator.getZoom(), "generator zoom"), generator.getOffsetX(), generator.getOffsetZ(), null, null);
        String resource = RESOURCE_PREFIX + key(generator);
        Field value = combine(generator, root, path, resource, name);
        if (generator.hasCliffs()) {
            value = new Field.Round(Field.RoundMode.ROUND, value, cliffStep(generator, root, path, resource, name));
        }
        if (generator.getCellFractureHeight() != 0.0) {
            report(resource, "Generator cell fracture is not exported; the unfractured height is used");
        }
        return value;
    }

    private Field combine(IrisGenerator generator, Domain root, String path, String resource, String name) {
        List<IrisNoiseGenerator> composite = generator.getComposite();
        List<Field> values = new ArrayList<>(composite.size());
        double opacity = 0.0;
        for (int index = 0; index < composite.size(); index++) {
            IrisNoiseGenerator layer = composite.get(index);
            values.add(layer(layer, root, path + "/l" + index + "_" + layer.getSeed(), resource, name + "/l" + index));
            opacity += layer.getOpacity();
        }
        if (generator.isMultiplicitive()) {
            Field product = values.getFirst();
            for (int index = 1; index < values.size(); index++) {
                product = FieldMacros.product(FieldMacros.share(name + "/product_" + index, product), values.get(index));
            }
            return FieldMacros.scale(product, generator.getOpacity());
        }
        if (opacity == 0.0) {
            return new Field.Const(0.0);
        }
        return FieldMacros.scale(FieldMacros.sum(values), generator.getOpacity() / opacity);
    }

    private Field layer(IrisNoiseGenerator layer, Domain domain, String path, String resource, String name) {
        if (!layer.isEnabled()) {
            return new Field.Const(layer.getOffsetY());
        }
        if (layer.getOctaves() > 1) {
            report(resource, "Noise layer octave multiplier " + layer.getOctaves() + " is not exported; the style's own octaves are used");
        }
        Domain fractured = fractured(layer, domain, path, resource, name);
        double layerZoom = positive(layer.getZoom(), "layer zoom");
        double zoom = domain.zoom() * layerZoom;
        Field noise = style(layer.getStyle(), zoom, path);
        Field placed = place(noise, fractured.offsetX() + layer.getOffsetX() * zoom, fractured.offsetZ() + layer.getOffsetZ() * zoom,
                fractured.warpX(), fractured.warpZ(), path);
        return shape(layer, placed, name);
    }

    private Domain fractured(IrisNoiseGenerator layer, Domain domain, String path, String resource, String name) {
        List<IrisNoiseGenerator> fractures = layer.getFracture();
        if (fractures == null || fractures.isEmpty()) {
            return domain;
        }
        List<Field> warpX = new ArrayList<>();
        List<Field> warpZ = new ArrayList<>();
        int salt = 33;
        for (IrisNoiseGenerator child : fractures) {
            if (child.isEnabled()) {
                Field center = new Field.Const(-child.getOpacity() / 2.0);
                String childPath = path + "/f" + salt + "_" + child.getSeed();
                String childName = name + "/f" + salt;
                warpX.add(FieldMacros.scale(FieldMacros.sum(List.of(layer(child, domain, childPath + "x", resource, childName + "x"), center)), domain.zoom()));
                warpZ.add(FieldMacros.scale(FieldMacros.sum(List.of(layer(child, domain, childPath + "z", resource, childName + "z"), center)), domain.zoom()));
            }
            salt += 819;
        }
        if (warpX.isEmpty()) {
            return domain;
        }
        return new Domain(domain.zoom(), domain.offsetX(), domain.offsetZ(), add(domain.warpX(), FieldMacros.sum(warpX)), add(domain.warpZ(), FieldMacros.sum(warpZ)));
    }

    private Field place(Field noise, double offsetX, double offsetZ, Field warpX, Field warpZ, String path) {
        if (warpX == null && warpZ == null) {
            return FieldMacros.offset(noise, offsetX, offsetZ);
        }
        Field dx = add(warpX, new Field.Const(offsetX));
        Field dz = add(warpZ, new Field.Const(offsetZ));
        String tag = "warp_" + Long.toHexString(salt(path));
        return FieldMacros.displace(noise, dx, dz, tag);
    }

    private Field shape(IrisNoiseGenerator layer, Field noise, String name) {
        double opacity = layer.getOpacity();
        Field value = FieldMacros.scale(noise, opacity);
        if (layer.isNegative()) {
            value = new Field.Binary(Field.BinaryOp.SUB, new Field.Const(opacity), value);
        }
        if (layer.getExponent() != 1.0) {
            value = signedPower(value, layer.getExponent(), name);
        }
        if (layer.getOffsetY() != 0.0) {
            value = FieldMacros.sum(List.of(value, new Field.Const(layer.getOffsetY())));
        }
        if (layer.isParametric()) {
            value = curve(value, GeneratorCompiler::parametric, GeneratorCompiler::parametricSlope);
        }
        if (layer.isBezier()) {
            value = curve(value, GeneratorCompiler::bezier, GeneratorCompiler::bezierSlope);
        }
        if (layer.isSinCentered()) {
            value = curve(value, input -> Math.sin(input * Math.PI), input -> Math.PI * Math.cos(input * Math.PI));
        }
        return value;
    }

    private Field cliffStep(IrisGenerator generator, Domain root, String path, String resource, String name) {
        double minimum = generator.getCliffHeightMin();
        double maximum = generator.getCliffHeightMax();
        IrisNoiseGenerator cliffs = generator.getCliffHeightGenerator();
        if (minimum == maximum || cliffs == null) {
            return new Field.Const(maximum / CLIFF_RESOLUTION);
        }
        Field noise = layer(cliffs, root, path + "/cliff", resource, name + "/cliff");
        Field height = FieldMacros.sum(List.of(new Field.Const(minimum), FieldMacros.scale(noise, maximum - minimum)));
        return FieldMacros.scale(height, 1.0 / CLIFF_RESOLUTION);
    }

    private Field surfaceDetail(Field value, double detail, String name) {
        if (!Double.isFinite(detail) || detail < 0.0 || detail > 1.0) {
            throw new IllegalArgumentException("Generator surfaceDetail must be finite and between 0 and 1, got " + detail);
        }
        if (detail == 1.0 || value.cost() == CostClass.CHEAP) {
            return value;
        }
        if (detail == 0.0) {
            return new Field.Grid(value, SURFACE_GRID, 1);
        }
        Field raw = FieldMacros.share(name + "/raw", value);
        Field smooth = new Field.Grid(raw, SURFACE_GRID, 1);
        return FieldMacros.sum(List.of(FieldMacros.scale(smooth, 1.0 - detail), FieldMacros.scale(raw, detail)));
    }

    private Field signedPower(Field input, double exponent, String name) {
        ValueRange range = input.range();
        Field value = range.min() < 0.0 && exponent != 3.0 ? FieldMacros.share(name + "/power", input) : input;
        if (exponent == 2.0 && range.min() >= 0.0) {
            return new Field.Unary(Field.UnaryOp.SQUARE, value);
        }
        if (exponent == 3.0) {
            return new Field.Unary(Field.UnaryOp.CUBE, value);
        }
        Field power = new Field.Const(exponent);
        Field positive = new Field.Binary(Field.BinaryOp.POW, new Field.Binary(Field.BinaryOp.MAX, value, new Field.Const(0.0)), power);
        if (range.min() >= 0.0) {
            return positive;
        }
        Field negated = new Field.Unary(Field.UnaryOp.NEG, value);
        Field negative = new Field.Binary(Field.BinaryOp.POW, new Field.Binary(Field.BinaryOp.MAX, negated, new Field.Const(0.0)), power);
        return new Field.Binary(Field.BinaryOp.SUB, positive, negative);
    }

    private Field curve(Field value, DoubleUnaryOperator function, DoubleUnaryOperator slope) {
        ValueRange range = value.range();
        double from = Math.max(-CURVE_REACH, Math.min(0.0, Math.floor(range.min())));
        double to = Math.min(CURVE_REACH + 1.0, Math.max(1.0, Math.ceil(range.max())));
        int segments = (int) Math.round((to - from) * SEGMENTS_PER_UNIT);
        return FieldMacros.lookup(value, function, slope, from, to, segments);
    }

    private void report(String resource, String message) {
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.NOISE_STYLE, resource, message));
    }

    private String name(String scope, String key) {
        String base = SHARED_PREFIX + scope + "/" + VanillaResourceIdentifier.sanitizePath(key);
        String candidate = base;
        int suffix = 2;
        while (!names.add(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String key(IrisGenerator generator) {
        String key = generator.getLoadKey();
        return key == null || key.isBlank() ? DEFAULT_KEY : key;
    }

    private static Field add(Field first, Field second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return FieldMacros.sum(List.of(first, second));
    }

    private static double positive(double value, String role) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("The " + role + " must be finite and positive, got " + value);
        }
        return value;
    }

    private static double bezier(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double bezierSlope(double value) {
        return 6.0 * value - 6.0 * value * value;
    }

    private static double parametric(double value) {
        double forward = Math.pow(value, PARAMETRIC_ALPHA);
        double backward = Math.pow(1.0 - value, PARAMETRIC_ALPHA);
        return forward / (forward + backward);
    }

    private static double parametricSlope(double value) {
        double forward = value * value;
        double backward = (1.0 - value) * (1.0 - value);
        double total = forward + backward;
        return (2.0 * value * backward + 2.0 * (1.0 - value) * forward) / (total * total);
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    private record Domain(double zoom, double offsetX, double offsetZ, Field warpX, Field warpZ) {
    }
}
