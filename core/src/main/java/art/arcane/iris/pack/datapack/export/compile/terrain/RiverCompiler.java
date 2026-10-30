package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RiverCompiler {
    public static final String CARVED_HEIGHT = "height/carved";
    private static final String RESOURCE_PREFIX = "dimensions/";
    private static final String RESOURCE_SUFFIX = "/hydrology";
    private static final String TIER_PREFIX = "river/tier_";
    private static final String BED_VARIATION = "river/bed_variation";
    private static final NoiseDef WARP_X = new NoiseDef("river/warp_x", 0, List.of(1.0));
    private static final NoiseDef WARP_Z = new NoiseDef("river/warp_z", 0, List.of(1.0));
    private static final NoiseDef BED = new NoiseDef("river/bed", -5, List.of(1.0, 0.0, 2.0));
    private static final NoiseDef BANK = new NoiseDef("river/bank", 1, List.of(1.0));
    private static final int NETWORK_OCTAVE = 0;
    private static final List<Double> NETWORK_AMPLITUDES = List.of(1.0);
    private static final int NETWORK_CELL = 4;
    private static final double GRADIENT_STEP = 4.0;
    private static final double ISOLINE_DENSITY = 1.36;
    private static final double MEAN_GRADIENT = 1.12;
    private static final double GRADIENT_FLOOR_SHARE = 0.25;
    private static final double GRADIENT_CEILING_SHARE = 4.0;
    private static final double MIN_WAVELENGTH = 16.0;
    private static final double WARP_FREQUENCY_RATIO = 0.5;
    private static final double WARP_AMPLITUDE_SHARE = 0.6;
    private static final double VALLEY_RELIEF_PER_WIDTH = 4.0 / 3.0;
    private static final double MIN_VALLEY_RELIEF = 4.0;
    private static final double MIN_DEPTH = 1.0;
    private static final double BED_VARIATION_SHARE = 0.3;
    private static final double MAX_BED_VARIATION = 2.0;
    private static final double MAX_CONE_SLOPE = 2.0;
    private static final double CONE_EXPONENT = 0.8;
    private static final int CONE_SEGMENTS = 16;
    private static final double TERRACE = 3.0;
    private static final double CLIFF_BENCH = 0.5;
    private static final double CLIFF_WALL = 0.2;
    private static final Field ZERO = new Field.Const(0.0);
    private static final Field ONE = new Field.Const(1.0);

    private final List<ExportDiagnostic> diagnostics;

    public RiverCompiler(List<ExportDiagnostic> diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public Field compile(Field height, IrisDimension dimension, NoiseRegistry registry, ExportCapabilities capabilities) {
        Objects.requireNonNull(height, "height");
        IrisHydrology hydrology = dimension.getHydrology();
        if (hydrology == null || !hydrology.isEnabled()) {
            return height;
        }
        int fluidHeight = dimension.getMinHeight() + dimension.getFluidHeight();
        HydrologySettings settings = HydrologySettings.compile(hydrology, 0L, fluidHeight, dimension.getMinHeight(), dimension.getMaxHeight());
        String resource = RESOURCE_PREFIX + dimension.getLoadKey() + RESOURCE_SUFFIX;
        report(ExportLimit.ELEVATED_WATER, resource, "Water above sea level is not exported: channels above the lowlands stay dry and ponds, lakes and waterfalls are dropped");
        if (settings.density() <= 0.0F) {
            return height;
        }
        report(ExportLimit.RIVER_DRAINAGE, resource, "Rivers follow seeded noise channels instead of drainage: tributaries cross instead of joining and widths follow elevation");
        Field elevation = FieldMacros.sum(List.of(height, new Field.Const(-fluidHeight)));
        Carving carving = new Carving(elevation, variation(registry), bankNoise(settings.deformation(), registry), settings.deformation(), registry, capabilities);
        List<HydrologyTier> tiers = settings.tiers();
        List<Field> cuts = new ArrayList<>(tiers.size());
        for (int index = 0; index < tiers.size(); index++) {
            cuts.add(cut(carving, tiers.get(index), index, settings.density()));
        }
        Field carved = new Field.Binary(Field.BinaryOp.SUB, height, deepest(cuts, 0, cuts.size()));
        FieldValidator.requireTwoDimensional(carved, CARVED_HEIGHT);
        return new Field.Shared(CARVED_HEIGHT, carved);
    }

    private void report(ExportLimit limit, String resource, String message) {
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, limit, resource, message));
    }

    private static Field cut(Carving carving, HydrologyTier tier, int index, double density) {
        String prefix = TIER_PREFIX + index;
        double wavelength = Math.max(MIN_WAVELENGTH, Math.sqrt(tier.minCatchment() / density));
        double scale = 1.0 / (wavelength * ISOLINE_DENSITY);
        Field distance = new Field.Shared(prefix + "/distance", distance(network(carving, prefix, scale, wavelength), scale, carving.capabilities()));
        double valley = Math.max(Math.max(MIN_VALLEY_RELIEF, VALLEY_RELIEF_PER_WIDTH * tier.maxHalfWidth()), tier.inset() + 1.0);
        Field halfWidth = halfWidth(tier, carving.elevation(), valley);
        Field depth = depth(halfWidth, tier.depthRatio());
        Field ratio = new Field.Clamp(halfWidth instanceof Field.Const constant
                ? FieldMacros.scale(distance, 1.0 / constant.value())
                : new Field.Binary(Field.BinaryOp.DIV, distance, halfWidth), 0.0, 1.0);
        Field relief = relief(carving.elevation(), valley, tier.inset());
        Field bank = bank(carving, tier, new Field.Binary(Field.BinaryOp.SUB, distance, halfWidth), relief, valley);
        Field total = FieldMacros.sum(List.of(bed(ratio, depth, carving.variation()), min(ZERO, carving.elevation()), bank));
        return new Field.Shared(prefix + "/cut", max(ZERO, total));
    }

    private static Field network(Carving carving, String prefix, double scale, double wavelength) {
        NoiseRegistry registry = carving.registry();
        String noise = registry.register(new NoiseDef(prefix, NETWORK_OCTAVE, NETWORK_AMPLITUDES));
        double warpScale = scale * WARP_FREQUENCY_RATIO;
        double warpShift = WARP_AMPLITUDE_SHARE * wavelength * scale;
        Field shiftX = FieldMacros.scale(new Field.Noise(registry.register(WARP_X), warpScale, 0.0, null, null, null), warpShift);
        Field shiftZ = FieldMacros.scale(new Field.Noise(registry.register(WARP_Z), warpScale, 0.0, null, null, null), warpShift);
        Field warped = new Field.Noise(noise, scale, 0.0, shiftX, null, shiftZ);
        return new Field.Shared(prefix + "/network", new Field.Grid(warped, NETWORK_CELL, 1));
    }

    private static Field distance(Field network, double scale, ExportCapabilities capabilities) {
        Field east = FieldMacros.offset(network, GRADIENT_STEP, 0.0);
        Field south = FieldMacros.offset(network, 0.0, GRADIENT_STEP);
        Field diagonal = FieldMacros.offset(network, GRADIENT_STEP, GRADIENT_STEP);
        Field x = stencil(List.of(east, diagonal), List.of(network, south), 0.5 / GRADIENT_STEP);
        Field z = stencil(List.of(south, diagonal), List.of(network, east), 0.5 / GRADIENT_STEP);
        Field center = stencil(List.of(network, east, south, diagonal), List.of(), 0.25);
        Field slope = FieldMacros.norm(new FieldMacros.Gradient(x, z));
        double mean = MEAN_GRADIENT * scale;
        Field bounded = new Field.Clamp(slope, GRADIENT_FLOOR_SHARE * mean, GRADIENT_CEILING_SHARE * mean);
        return new Field.Binary(Field.BinaryOp.DIV, new Field.Unary(Field.UnaryOp.ABS, center), bounded);
    }

    private static Field stencil(List<Field> added, List<Field> subtracted, double weight) {
        List<Field> terms = new ArrayList<>(added.size() + subtracted.size());
        terms.addAll(added);
        for (Field field : subtracted) {
            terms.add(FieldMacros.scale(field, -1.0));
        }
        return FieldMacros.scale(FieldMacros.sum(terms), weight);
    }

    private static Field halfWidth(HydrologyTier tier, Field elevation, double valley) {
        double narrow = tier.minHalfWidth();
        double wide = tier.maxHalfWidth();
        if (narrow == wide) {
            return new Field.Const(wide);
        }
        Field upland = new Field.Clamp(FieldMacros.scale(elevation, 1.0 / valley), 0.0, 1.0);
        return FieldMacros.sum(List.of(new Field.Const(wide), FieldMacros.scale(upland, narrow - wide)));
    }

    private static Field depth(Field halfWidth, double depthRatio) {
        if (halfWidth instanceof Field.Const constant) {
            return new Field.Const(Math.max(MIN_DEPTH, depthRatio * constant.value()));
        }
        return max(new Field.Const(MIN_DEPTH), FieldMacros.scale(halfWidth, depthRatio));
    }

    private static Field relief(Field elevation, double valley, int inset) {
        double gully = Math.max(0, inset);
        Field floorRise = new Field.Curve(elevation, List.of(
                new Field.Knot(valley, ZERO, 0.0),
                new Field.Knot(2.0 * valley, new Field.Const(2.0 * valley - gully), 1.0)));
        return max(ZERO, new Field.Binary(Field.BinaryOp.SUB, elevation, floorRise));
    }

    private static Field bed(Field ratio, Field depth, Field variation) {
        Field bowl = FieldMacros.sum(List.of(ONE, FieldMacros.scale(new Field.Unary(Field.UnaryOp.SQUARE, ratio), -1.0)));
        Field amplitude = depth instanceof Field.Const constant
                ? new Field.Const(Math.min(MAX_BED_VARIATION, BED_VARIATION_SHARE * constant.value()))
                : min(new Field.Const(MAX_BED_VARIATION), FieldMacros.scale(depth, BED_VARIATION_SHARE));
        Field deepened = FieldMacros.sum(List.of(depth, weigh(variation, amplitude)));
        Field channel = new Field.Partition(ratio, List.of(1.0), List.of(ONE, ZERO));
        return min(depth, max(channel, weigh(bowl, deepened)));
    }

    private static Field bank(Carving carving, HydrologyTier tier, Field edge, Field relief, double valley) {
        int band = tier.bankBand();
        if (band <= 0) {
            return weigh(new Field.Partition(edge, List.of(0.0), List.of(ONE, ZERO)), relief);
        }
        Field across = FieldMacros.scale(edge, 1.0 / band);
        IrisBankStyle style = tier.bankStyle() == null ? IrisBankStyle.SMOOTH : tier.bankStyle();
        return switch (style) {
            case SMOOTH, LINEAR, CONCAVE -> sloped(carving, shape(style, across), edge, across, relief, band);
            case TERRACED -> terraced(carving, shape(style, across), edge, across, relief, band, valley);
            case CLIFF -> weigh(FieldMacros.sum(List.of(ONE, FieldMacros.scale(shape(style, across), -1.0))), relief);
        };
    }

    private static Field sloped(Carving carving, Field shape, Field edge, Field across, Field relief, int band) {
        Field rise = max(weigh(shape, relief), weigh(cone(relief, band, carving.deformation()), edge));
        if (carving.bankNoise() != null) {
            Field outside = new Field.Partition(across, List.of(0.0), List.of(ZERO, ONE));
            rise = FieldMacros.sum(List.of(rise, weigh(outside, carving.bankNoise())));
        }
        return max(ZERO, new Field.Binary(Field.BinaryOp.SUB, relief, max(ZERO, rise)));
    }

    private static Field terraced(Carving carving, Field shape, Field edge, Field across, Field relief, int band, double valley) {
        Field rise = max(weigh(shape, relief), weigh(cone(relief, band, carving.deformation()), edge));
        Field bounded = new Field.Clamp(min(relief, max(ZERO, rise)), 0.0, 2.0 * valley);
        Field stepped = new Field.Round(Field.RoundMode.FLOOR, bounded, new Field.Const(TERRACE));
        Field inside = new Field.Partition(across, List.of(1.0), List.of(ONE, ZERO));
        return weigh(inside, new Field.Binary(Field.BinaryOp.SUB, relief, stepped));
    }

    private static Field shape(IrisBankStyle style, Field across) {
        return switch (style) {
            case SMOOTH, TERRACED -> FieldMacros.smoothstep(across, 0.0, 1.0);
            case LINEAR -> new Field.Clamp(across, 0.0, 1.0);
            case CONCAVE -> new Field.Curve(new Field.Clamp(across, 0.0, 1.0), List.of(
                    new Field.Knot(0.0, ZERO, 2.0),
                    new Field.Knot(1.0, ONE, 0.0)));
            case CLIFF -> new Field.Clamp(FieldMacros.scale(FieldMacros.sum(List.of(across, new Field.Const(-CLIFF_BENCH))), 1.0 / CLIFF_WALL), 0.0, 1.0);
        };
    }

    private static Field cone(Field relief, int band, HydrologySettings.Deformation deformation) {
        double base = Math.max(0.0, deformation.bankSlope());
        double steep = deformation.bankSteepFactor();
        if (steep <= 0.0 || base >= MAX_CONE_SLOPE) {
            return new Field.Const(Math.min(MAX_CONE_SLOPE, base));
        }
        double saturation = Math.pow((MAX_CONE_SLOPE - base) / steep, 1.0 / CONE_EXPONENT);
        double half = saturation / CONE_SEGMENTS / 2.0;
        Field slope = new Field.Clamp(FieldMacros.scale(relief, 1.0 / band), 0.0, saturation);
        return FieldMacros.lookup(slope,
                value -> coneSlope(value, base, steep),
                value -> coneDerivative(value, half, saturation, base, steep),
                0.0, saturation, CONE_SEGMENTS);
    }

    private static double coneSlope(double slope, double base, double steep) {
        return Math.min(MAX_CONE_SLOPE, base + steep * Math.pow(slope, CONE_EXPONENT));
    }

    private static double coneDerivative(double slope, double half, double saturation, double base, double steep) {
        double low = Math.max(0.0, slope - half);
        double high = Math.min(saturation, slope + half);
        return (coneSlope(high, base, steep) - coneSlope(low, base, steep)) / (high - low);
    }

    private static Field variation(NoiseRegistry registry) {
        Field noise = new Field.Noise(registry.register(BED), 1.0, 0.0, null, null, null);
        return new Field.Shared(BED_VARIATION, new Field.Grid(noise, NETWORK_CELL, 1));
    }

    private static Field bankNoise(HydrologySettings.Deformation deformation, NoiseRegistry registry) {
        double strength = deformation.bankNoise();
        if (strength <= 0.0) {
            return null;
        }
        return FieldMacros.scale(new Field.Noise(registry.register(BANK), 1.0, 0.0, null, null, null), strength / 2.0);
    }

    private static Field weigh(Field weight, Field amount) {
        if (weight instanceof Field.Const || amount instanceof Field.Const) {
            return FieldMacros.product(weight, amount);
        }
        if (weight.cost() != CostClass.EXPENSIVE && amount.cost() != CostClass.EXPENSIVE) {
            return new Field.Binary(Field.BinaryOp.MUL, weight, amount);
        }
        Field magnitude = weight.range().min() >= 0.0 ? weight : new Field.Unary(Field.UnaryOp.ABS, weight);
        return new Field.Binary(Field.BinaryOp.MUL, weight, new Field.Gate(magnitude, amount));
    }

    private static Field deepest(List<Field> cuts, int from, int to) {
        if (to - from == 1) {
            return cuts.get(from);
        }
        int middle = (from + to) >>> 1;
        return max(deepest(cuts, from, middle), deepest(cuts, middle, to));
    }

    private static Field min(Field first, Field second) {
        if (first instanceof Field.Const left && second instanceof Field.Const right) {
            return new Field.Const(Math.min(left.value(), right.value()));
        }
        return new Field.Binary(Field.BinaryOp.MIN, first, second);
    }

    private static Field max(Field first, Field second) {
        if (first instanceof Field.Const left && second instanceof Field.Const right) {
            return new Field.Const(Math.max(left.value(), right.value()));
        }
        return new Field.Binary(Field.BinaryOp.MAX, first, second);
    }

    private record Carving(Field elevation, Field variation, Field bankNoise, HydrologySettings.Deformation deformation,
                           NoiseRegistry registry, ExportCapabilities capabilities) {
    }
}
