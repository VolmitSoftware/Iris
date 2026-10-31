package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DefaultCellularStyleLowering implements CellularStyleLowering {
    private static final double CELL_BLOCKS = 64.0D;
    private static final double SIMPLEX_CELL_BLOCKS = 100.0D;
    private static final double SIMPLEX_CELL_FRACTURE = 200.0D;
    private static final double HEX_BLOCKS = 32.0D;
    private static final double SMOOTHING_BLOCKS = 32.0D;
    private static final int SMOOTHING_CELL_Y = 8;
    private static final double THIN_POWER = 1.0D / 0.65D;
    private static final double IRIS_CELL_FREQUENCY = 0.01D;
    private static final double CRACK_CELL_SCALE = 0.73D;
    private static final double CRACK_SIGNATURE_ZOOM = 1.5625D;
    private static final int SECOND_VALUE_TURNS = 5;
    private static final int SECOND_VALUE_INTERVALS = 120;
    private static final double RANGE_LIMIT = 1.0e6D;
    private static final String NAMESPACE_PREFIX = "cell/";
    private static final String FRACTURE_X = "fracture_x";
    private static final String FRACTURE_Z = "fracture_z";

    private final List<ExportDiagnostic> diagnostics;
    private final CellularStyleLowering nestedStyles;
    private final CellularMode cellMode;

    public DefaultCellularStyleLowering(List<ExportDiagnostic> diagnostics, CellularStyleLowering nestedStyles, CellularMode cellMode) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.nestedStyles = Objects.requireNonNull(nestedStyles, "nestedStyles");
        this.cellMode = Objects.requireNonNull(cellMode, "cellMode");
        if (cellMode == CellularMode.GRID_3X3X3) {
            throw new IllegalArgumentException("Planar cell values lower in GRID_3X3 or QUADRANT_2X2 mode, got " + cellMode);
        }
    }

    public static boolean supports(NoiseStyle style) {
        return style != null && shape(style) != null;
    }

    @Override
    public Field lower(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(capabilities, "capabilities");
        if (style.getExpression() != null || style.getImageMap() != null) {
            throw new IllegalArgumentException("Cellular lowering needs a plain noise style, got an expression or image map style");
        }
        NoiseStyle noiseStyle = style.getStyle() == null ? NoiseStyle.FLAT : style.getStyle();
        Shape shape = shape(noiseStyle);
        if (shape == null) {
            return smoothCore(style, noiseStyle, seedSalt, registry, capabilities);
        }
        if (style.getCellularFrequency() > 0.0D) {
            report(noiseStyle, "Noise style " + noiseStyle.name() + " exports without its cellularFrequency cell lookup");
        }
        Request request = new Request(instanceId(style, seedSalt, capabilities.target()), cellMode, style, noiseStyle, shape, seedSalt, registry, capabilities);
        Field value = switch (shape.family()) {
            case CELL_VALUE -> cellValue(request);
            case VASCULAR, GLOB, CELL_HEIGHT -> distanceStyle(request);
            case SIMPLEX_CELLS -> simplexCells(request);
            case SIMPLEX_VASCULAR -> simplexVascular(request);
            case WHITE -> white(request);
            case HEXAGON -> hexagon(request);
            case PATTERN -> pattern(request);
        };
        return power(request, value, shape.power() * style.getExponent());
    }

    public Field cellCracks(IrisGenerator generator, Field value, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(capabilities, "capabilities");
        double height = generator.getCellFractureHeight();
        if (height == 0.0D) {
            return value;
        }
        double zoom = generator.getCellFractureZoom();
        DataVersion target = capabilities.target();
        String description = "cracks|" + seedSalt + "|" + zoom + "|" + generator.getCellFractureShuffle() + "|" + target.name();
        String instanceId = "cracks/" + Long.toUnsignedString(CellHashNoise.stableHash(description), 36);
        String prefix = NAMESPACE_PREFIX + instanceId + "/";
        CellularWarp.Offsets chain = CellularWarp.signature(prefix, CellularWarp.Signature.IRIS, zoom, registry, target);
        double shuffle = generator.getCellFractureShuffle() * zoom / CRACK_CELL_SCALE;
        Field warpX = shuffleWarp(prefix + "shuffle_x", instanceId + "/shuffle_x", chain, zoom, shuffle, registry, target);
        Field warpZ = shuffleWarp(prefix + "shuffle_z", instanceId + "/shuffle_z", chain, zoom, shuffle, registry, target);
        double cell = zoom / (IRIS_CELL_FREQUENCY * CRACK_CELL_SCALE);
        Field distance = halfGap(CellularLibrary.build(instanceId, cell, warpX, warpZ, CellularMode.GRID_3X3, registry));
        Field crack = new Field.Shared(prefix + "distance", distance);
        Field raised = new Field.Binary(Field.BinaryOp.MUL, value, new Field.Const(height));
        return new Field.RangeIf(crack, generator.getCellPercentSize(), RANGE_LIMIT, raised, value);
    }

    private Field cellValue(Request request) {
        CellularWarp.Offsets warp = warp(request);
        double zoom = request.zoom();
        Field value = CellularLibrary.build(request.instanceId(), CELL_BLOCKS * zoom, warp.x(), warp.z(), cellMode, request.registry()).cellValue();
        if (!request.shape().smoothed()) {
            return value;
        }
        return new Field.Shared(request.modePrefix() + "smoothed", new Field.Grid(value, smoothingCell(zoom), SMOOTHING_CELL_Y));
    }

    private Field distanceStyle(Request request) {
        Family family = request.shape().family();
        CellularWarp.Offsets warp = warp(request);
        double cell = CELL_BLOCKS * request.zoom();
        CellularOutputs outputs = CellularLibrary.build(request.instanceId(), cell, warp.x(), warp.z(), CellularMode.GRID_3X3, request.registry());
        Field distance = switch (family) {
            case VASCULAR -> vascular(outputs);
            case GLOB -> new Field.Binary(Field.BinaryOp.DIV, outputs.f1(), outputs.f2());
            case CELL_HEIGHT -> new Field.Clamp(halfGap(outputs), 0.0D, 1.0D);
            default -> throw new IllegalStateException("Family " + family + " is not a distance style");
        };
        return new Field.Shared(request.prefix() + "distance", distance);
    }

    private Field simplexCells(Request request) {
        if (request.style().getFracture() != null) {
            return smooth(request, NoiseStyle.SIMPLEX, userWarp(request));
        }
        double cell = SIMPLEX_CELL_BLOCKS * request.zoom();
        Field first = CellularLibrary.build(request.instanceId() + "/cells", cell, null, null, cellMode, request.registry()).cellValue();
        Field second = new Field.Shared(request.modePrefix() + "cells_second", secondValue(first));
        CellularWarp.Offsets fracture = CellularWarp.fracture(request.modePrefix(), first, second, SIMPLEX_CELL_FRACTURE * request.zoom());
        return CellularLookalike.smoothStyle(request.modeId(), NoiseStyle.SIMPLEX, request.zoom(), fracture.x(), fracture.z(), request.registry(), request.target());
    }

    private Field simplexVascular(Request request) {
        if (request.style().getFracture() != null) {
            return smooth(request, NoiseStyle.SIMPLEX, userWarp(request));
        }
        double cell = SIMPLEX_CELL_BLOCKS * request.zoom();
        Field first = veinValue(request, request.instanceId() + "/veins_x", cell);
        Field second = veinValue(request, request.instanceId() + "/veins_z", cell);
        return smooth(request, NoiseStyle.SIMPLEX, CellularWarp.fracture(request.prefix(), first, second, SIMPLEX_CELL_FRACTURE * request.zoom()));
    }

    private Field white(Request request) {
        if (!request.shape().smoothed()) {
            return new Field.Shared(request.prefix() + "white", CellHashNoise.white(request.instanceId(), request.registry(), request.target()));
        }
        Field white = CellHashNoise.white(request.instanceId(), request.registry(), request.target());
        return new Field.Shared(request.prefix() + "smoothed", new Field.Grid(white, smoothingCell(request.zoom()), SMOOTHING_CELL_Y));
    }

    private Field hexagon(Request request) {
        CellularWarp.Offsets warp = warp(request);
        return HexLattice.centerValue(request.instanceId(), HEX_BLOCKS * request.zoom(), warp.x(), warp.z(), request.registry());
    }

    private Field pattern(Request request) {
        report(request.noiseStyle(), "Noise style " + request.noiseStyle().name() + " exports as fitted smooth noise");
        return smooth(request, request.noiseStyle(), userWarp(request));
    }

    private Field veinValue(Request request, String instanceId, double cell) {
        CellularOutputs outputs = CellularLibrary.build(instanceId, cell, null, null, CellularMode.GRID_3X3, request.registry());
        return new Field.Shared(NAMESPACE_PREFIX + instanceId + "/vascular", vascular(outputs));
    }

    private Field smooth(Request request, NoiseStyle style, CellularWarp.Offsets warp) {
        return CellularLookalike.smoothStyle(request.instanceId(), style, request.zoom(), warp.x(), warp.z(), request.registry(), request.target());
    }

    private CellularWarp.Offsets warp(Request request) {
        if (request.style().getFracture() != null) {
            return userWarp(request);
        }
        CellularWarp.Signature signature = request.shape().warp();
        if (signature == null) {
            return CellularWarp.NONE;
        }
        return CellularWarp.signature(request.prefix(), signature, request.zoom(), request.registry(), request.target());
    }

    private CellularWarp.Offsets userWarp(Request request) {
        IrisGeneratorStyle fracture = request.style().getFracture();
        if (fracture == null) {
            return CellularWarp.NONE;
        }
        IrisGeneratorStyle zoomed = zoomedCopy(fracture, fracture.getZoom() * request.zoom());
        Field x = nestedStyles.lower(zoomed, salt(request.seedSalt(), FRACTURE_X), request.registry(), request.capabilities());
        Field z = nestedStyles.lower(zoomed, salt(request.seedSalt(), FRACTURE_Z), request.registry(), request.capabilities());
        return CellularWarp.fracture(request.modePrefix(), x, z, fracture.getMultiplier() * request.zoom());
    }

    private Field power(Request request, Field value, double exponent) {
        if (exponent == 1.0D) {
            return value;
        }
        Field constant = new Field.Const(exponent);
        if (request.shape().family() != Family.PATTERN) {
            return new Field.Shared(request.modePrefix() + "style", new Field.Binary(Field.BinaryOp.POW, new Field.Clamp(value, 0.0D, 1.0D), constant));
        }
        Field magnitude = new Field.Binary(Field.BinaryOp.POW, new Field.Unary(Field.UnaryOp.ABS, value), constant);
        return new Field.Shared(request.modePrefix() + "style", new Field.Binary(Field.BinaryOp.MUL, new Field.Unary(Field.UnaryOp.SIGN, value), magnitude));
    }

    private Field shuffleWarp(String name, String instanceId, CellularWarp.Offsets chain, double zoom, double shuffle, NoiseRegistry registry, DataVersion target) {
        Field value = CellularLookalike.smoothStyle(instanceId, NoiseStyle.SIMPLEX, CRACK_SIGNATURE_ZOOM * zoom, chain.x(), chain.z(), registry, target);
        return new Field.Shared(name, new Field.Binary(Field.BinaryOp.MUL, value, new Field.Const(shuffle)));
    }

    private void report(NoiseStyle style, String message) {
        ExportDiagnostic diagnostic = new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.NOISE_STYLE, "noise style " + style.name(), message);
        if (!diagnostics.contains(diagnostic)) {
            diagnostics.add(diagnostic);
        }
    }

    private static Field vascular(CellularOutputs outputs) {
        Field gap = new Field.Binary(Field.BinaryOp.SUB, outputs.f2(), outputs.f1());
        Field value = new Field.Binary(Field.BinaryOp.ADD, new Field.Const(1.0D), new Field.Binary(Field.BinaryOp.MUL, gap, new Field.Const(-0.5D)));
        return new Field.Clamp(value, 0.0D, 1.0D);
    }

    private static Field halfGap(CellularOutputs outputs) {
        return new Field.Binary(Field.BinaryOp.MUL, new Field.Binary(Field.BinaryOp.SUB, outputs.f2(), outputs.f1()), new Field.Const(0.5D));
    }

    private static Field secondValue(Field cellValue) {
        double[] locations = new double[SECOND_VALUE_INTERVALS + 1];
        double[] values = new double[SECOND_VALUE_INTERVALS + 1];
        for (int index = 0; index <= SECOND_VALUE_INTERVALS; index++) {
            double fraction = (double) index / SECOND_VALUE_INTERVALS;
            double phase = SECOND_VALUE_TURNS * fraction - Math.floor(SECOND_VALUE_TURNS * fraction);
            locations[index] = fraction;
            values[index] = 1.0D - Math.abs(2.0D * phase - 1.0D);
        }
        return CurveKnots.smooth(cellValue, locations, values);
    }

    private static int smoothingCell(double zoom) {
        return Math.max(1, (int) Math.round(SMOOTHING_BLOCKS * zoom));
    }

    private static long salt(long seedSalt, String role) {
        return CellHashNoise.stableHash(seedSalt + "|" + role);
    }

    private static String instanceId(IrisGeneratorStyle style, long seedSalt, DataVersion target) {
        String name = (style.getStyle() == null ? NoiseStyle.FLAT : style.getStyle()).name().toLowerCase(Locale.ROOT);
        long hash = CellHashNoise.stableHash(describe(style) + "|" + seedSalt + "|" + target.name());
        return name + "/" + Long.toUnsignedString(hash, 36);
    }

    private static String describe(IrisGeneratorStyle style) {
        if (style == null) {
            return "-";
        }
        String name = style.getStyle() == null ? NoiseStyle.FLAT.name() : style.getStyle().name();
        return name + "(" + style.getZoom() + "," + style.getExponent() + "," + style.getMultiplier() + "," + style.getCellularFrequency()
                + "," + style.getCellularZoom() + "," + style.getExpression() + "," + style.getImageMap() + "," + describe(style.getFracture()) + ")";
    }

    private Field smoothCore(IrisGeneratorStyle style, NoiseStyle noiseStyle, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
        IrisGeneratorStyle core = withoutCellularParts(style);
        if (describe(core).equals(describe(style))) {
            throw new IllegalArgumentException("Noise style " + noiseStyle.name() + " is not a cellular-family style");
        }
        report(noiseStyle, "Noise style " + noiseStyle.name() + " exports without its cellular fracture and cell lookup");
        return nestedStyles.lower(core, seedSalt, registry, capabilities);
    }

    private static IrisGeneratorStyle withoutCellularParts(IrisGeneratorStyle style) {
        IrisGeneratorStyle fracture = style.getFracture();
        IrisGeneratorStyle smoothFracture = fracture == null || supports(fracture.getStyle()) ? null : withoutCellularParts(fracture);
        return zoomedCopy(style, style.getZoom()).setCellularFrequency(0.0D).setFracture(smoothFracture);
    }

    private static IrisGeneratorStyle zoomedCopy(IrisGeneratorStyle style, double zoom) {
        return new IrisGeneratorStyle(style.getStyle())
                .setCellularFrequency(style.getCellularFrequency())
                .setCellularZoom(style.getCellularZoom())
                .setZoom(zoom)
                .setExpression(style.getExpression())
                .setImageMap(style.getImageMap())
                .setMultiplier(style.getMultiplier())
                .setFracture(style.getFracture())
                .setExponent(style.getExponent())
                .setCacheSize(style.getCacheSize());
    }

    private static Shape shape(NoiseStyle style) {
        return switch (style) {
            case CELLULAR -> plain(Family.CELL_VALUE);
            case CELLULAR_IRIS -> warped(Family.CELL_VALUE, CellularWarp.Signature.IRIS);
            case CELLULAR_IRIS_THICK -> warped(Family.CELL_VALUE, CellularWarp.Signature.THICK);
            case CELLULAR_IRIS_DOUBLE -> warped(Family.CELL_VALUE, CellularWarp.Signature.DOUBLE);
            case CELLULAR_IRIS_HALF -> warped(Family.CELL_VALUE, CellularWarp.Signature.HALF);
            case NOWHERE_CELLULAR -> warped(Family.CELL_VALUE, CellularWarp.Signature.PERLIN);
            case CELLULAR_BILINEAR, CELLULAR_BICUBIC, CELLULAR_HERMITE,
                 CELLULAR_STARCAST_3, CELLULAR_STARCAST_6, CELLULAR_STARCAST_9, CELLULAR_STARCAST_12,
                 CELLULAR_BILINEAR_STARCAST_3, CELLULAR_BILINEAR_STARCAST_6, CELLULAR_BILINEAR_STARCAST_9, CELLULAR_BILINEAR_STARCAST_12,
                 CELLULAR_HERMITE_STARCAST_3, CELLULAR_HERMITE_STARCAST_6, CELLULAR_HERMITE_STARCAST_9, CELLULAR_HERMITE_STARCAST_12 -> smoothed(Family.CELL_VALUE);
            case VASCULAR -> plain(Family.VASCULAR);
            case VASCULAR_THIN -> new Shape(Family.VASCULAR, null, false, THIN_POWER);
            case VASCULAR_IRIS -> warped(Family.VASCULAR, CellularWarp.Signature.IRIS);
            case VASCULAR_IRIS_THICK -> warped(Family.VASCULAR, CellularWarp.Signature.THICK);
            case VASCULAR_IRIS_DOUBLE -> warped(Family.VASCULAR, CellularWarp.Signature.DOUBLE);
            case VASCULAR_IRIS_HALF -> warped(Family.VASCULAR, CellularWarp.Signature.HALF);
            case NOWHERE_VASCULAR -> warped(Family.VASCULAR, CellularWarp.Signature.PERLIN);
            case GLOB -> plain(Family.GLOB);
            case GLOB_IRIS -> warped(Family.GLOB, CellularWarp.Signature.IRIS);
            case GLOB_IRIS_THICK -> warped(Family.GLOB, CellularWarp.Signature.THICK);
            case GLOB_IRIS_DOUBLE -> warped(Family.GLOB, CellularWarp.Signature.DOUBLE);
            case GLOB_IRIS_HALF -> warped(Family.GLOB, CellularWarp.Signature.HALF);
            case NOWHERE_GLOB -> warped(Family.GLOB, CellularWarp.Signature.PERLIN);
            case CELLULAR_HEIGHT -> plain(Family.CELL_HEIGHT);
            case CELLULAR_HEIGHT_IRIS -> warped(Family.CELL_HEIGHT, CellularWarp.Signature.IRIS);
            case CELLULAR_HEIGHT_IRIS_THICK -> warped(Family.CELL_HEIGHT, CellularWarp.Signature.THICK);
            case CELLULAR_HEIGHT_IRIS_DOUBLE -> warped(Family.CELL_HEIGHT, CellularWarp.Signature.DOUBLE);
            case CELLULAR_HEIGHT_IRIS_HALF -> warped(Family.CELL_HEIGHT, CellularWarp.Signature.HALF);
            case SIMPLEX_CELLS -> plain(Family.SIMPLEX_CELLS);
            case SIMPLEX_VASCULAR -> plain(Family.SIMPLEX_VASCULAR);
            case STATIC -> plain(Family.WHITE);
            case STATIC_BILINEAR, STATIC_BICUBIC, STATIC_HERMITE -> smoothed(Family.WHITE);
            case HEXAGON, FRACTAL_HEXAGON -> plain(Family.HEXAGON);
            case HEXAGON_IRIS -> warped(Family.HEXAGON, CellularWarp.Signature.IRIS);
            case HEXAGON_IRIS_THICK -> warped(Family.HEXAGON, CellularWarp.Signature.THICK);
            case HEXAGON_IRIS_DOUBLE -> warped(Family.HEXAGON, CellularWarp.Signature.DOUBLE);
            case HEXAGON_IRIS_HALF -> warped(Family.HEXAGON, CellularWarp.Signature.HALF);
            case NOWHERE_HEXAGON -> warped(Family.HEXAGON, CellularWarp.Signature.PERLIN);
            case HEX_JAMES, HEX_SIMPLEX, HEX_RANDOM_SIZE, SIERPINSKI_TRIANGLE,
                 NOWHERE_HEX_JAMES, NOWHERE_HEX_SIMPLEX, NOWHERE_HEX_RANDOM_SIZE, NOWHERE_SIERPINSKI_TRIANGLE,
                 FRACTAL_HEX_JAMES, FRACTAL_HEX_SIMPLEX, FRACTAL_HEX_RANDOM_SIZE, FRACTAL_SIERPINSKI_TRIANGLE,
                 HEX_JAMES_IRIS, HEX_JAMES_IRIS_DOUBLE, HEX_JAMES_IRIS_THICK, HEX_JAMES_IRIS_HALF,
                 HEX_SIMPLEX_IRIS, HEX_SIMPLEX_IRIS_DOUBLE, HEX_SIMPLEX_IRIS_THICK, HEX_SIMPLEX_IRIS_HALF,
                 HEX_RANDOM_SIZE_IRIS, HEX_RANDOM_SIZE_IRIS_DOUBLE, HEX_RANDOM_SIZE_IRIS_THICK, HEX_RANDOM_SIZE_IRIS_HALF,
                 GYROID, QUASICRYSTAL, TRUCHET, CRATER, VORTEX, DUNE, STRATA, WOOD, GABOR, MARBLE, SCALES, CHLADNI,
                 KALEIDOSCOPE, MENGER_SPONGE, CIRCUIT -> plain(Family.PATTERN);
            default -> null;
        };
    }

    private static Shape plain(Family family) {
        return new Shape(family, null, false, 1.0D);
    }

    private static Shape warped(Family family, CellularWarp.Signature warp) {
        return new Shape(family, warp, false, 1.0D);
    }

    private static Shape smoothed(Family family) {
        return new Shape(family, null, true, 1.0D);
    }

    private enum Family {
        CELL_VALUE,
        VASCULAR,
        GLOB,
        CELL_HEIGHT,
        SIMPLEX_CELLS,
        SIMPLEX_VASCULAR,
        WHITE,
        HEXAGON,
        PATTERN
    }

    private record Shape(Family family, CellularWarp.Signature warp, boolean smoothed, double power) {
    }

    private record Request(String instanceId, CellularMode cellMode, IrisGeneratorStyle style, NoiseStyle noiseStyle, Shape shape, long seedSalt,
                           NoiseRegistry registry, ExportCapabilities capabilities) {
        private double zoom() {
            return style.getZoom();
        }

        private DataVersion target() {
            return capabilities.target();
        }

        private String prefix() {
            return NAMESPACE_PREFIX + instanceId + "/";
        }

        private String modeId() {
            return instanceId + CellularLibrary.modeSegment(cellMode);
        }

        private String modePrefix() {
            return NAMESPACE_PREFIX + modeId() + "/";
        }
    }
}
