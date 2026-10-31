package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class NoiseStyleCompilerTest {
    private static final DataVersion TARGET = DataVersion.V26_3;
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(TARGET);
    private static final long SEED = 1_234L;
    private static final long SUPER_SEED = 98_765L;
    private static final double FLOAT_TOLERANCE = 1.0e-6;
    private static final CellularStyleLowering REJECTING = (style, salt, registry, capabilities) -> {
        throw new AssertionError("Smooth style " + style.getStyle() + " reached the cellular seam");
    };
    private static final NoiseStyleCompiler SMOOTH = new NoiseStyleCompiler(REJECTING);

    @Test
    public void cellularFamilyStylesGoThroughTheSeam() {
        Field marker = new Field.Const(0.25);
        List<NoiseStyle> seen = new ArrayList<>();
        NoiseStyleCompiler compiler = new NoiseStyleCompiler((style, salt, registry, capabilities) -> {
            seen.add(style.getStyle());
            return marker;
        });
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        List<NoiseStyle> cellular = List.of(NoiseStyle.CELLULAR, NoiseStyle.CELLULAR_IRIS_DOUBLE, NoiseStyle.NOWHERE_CELLULAR,
                NoiseStyle.SIMPLEX_CELLS, NoiseStyle.SIMPLEX_VASCULAR, NoiseStyle.STATIC, NoiseStyle.HEXAGON, NoiseStyle.VASCULAR_THIN,
                NoiseStyle.GLOB_IRIS_THICK, NoiseStyle.CELLULAR_BILINEAR, NoiseStyle.CELLULAR_HEIGHT);
        for (NoiseStyle style : cellular) {
            assertSame(marker, compiler.compile(new IrisGeneratorStyle(style), 7L, registry, CAPABILITIES));
        }
        assertEquals(cellular, seen);
        assertTrue(registry.definitions().isEmpty());
    }

    @Test
    public void createdCompilerLowersCellularWithItsOwnNestedStyles() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        IrisGeneratorStyle fracture = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(3.0).setMultiplier(9.0);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE).setZoom(2.0).setFracture(fracture);
        style.setCellularFrequency(2.0);

        Field field = NoiseStyleCompiler.create(diagnostics).compile(style, 7L, registry, ExportCapabilities.forTarget(DataVersion.V26_3));

        FieldValidator.requireTwoDimensional(field, "cellular style");
        assertTrue(registry.definitions().toString(), registry.definitions().stream().anyMatch(definition -> definition.id().startsWith("style/")));
        assertTrue(diagnostics.toString(), diagnostics.stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.NOISE_STYLE));
    }

    @Test
    public void selectorStylesLowerCellValuesInQuadrants() {
        NoiseStyleCompiler compiler = NoiseStyleCompiler.create(new ArrayList<>());
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        ExportCapabilities current = ExportCapabilities.forTarget(DataVersion.V26_3);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE);

        Field terrain = compiler.compile(style, 7L, registry, current);
        Field selector = compiler.selectors().compile(style, 7L, registry, current);

        assertEquals(9, hashNoises(terrain));
        assertEquals(4, hashNoises(selector));
        assertSame(compiler.selectors(), compiler.selectors().selectors());
        StyleCompiler plain = (ignored, salt, ignoredRegistry, capabilities) -> new Field.Const(0.0);
        assertSame(plain, plain.selectors());
        assertSame(SMOOTH, SMOOTH.selectors());
    }

    @Test
    public void cellularizedStylesGoThroughTheSeam() {
        Field marker = new Field.Const(0.75);
        NoiseStyleCompiler compiler = new NoiseStyleCompiler((style, salt, registry, capabilities) -> marker);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        style.setCellularFrequency(2.0);
        assertSame(marker, compiler.compile(style, 7L, new NoiseRegistry("iris_export"), CAPABILITIES));
    }

    @Test
    public void smoothStylesStayTwoDimensional() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Field field = SMOOTH.compile(new IrisGeneratorStyle(NoiseStyle.NOWHERE), 7L, registry, CAPABILITIES);
        FieldValidator.requireTwoDimensional(field, "nowhere");
        assertEquals(3, registry.definitions().size());
    }

    @Test
    public void expressionStylesAreRejected() {
        IrisGeneratorStyle expression = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        expression.setExpression("strata");
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        assertThrows(IllegalArgumentException.class, () -> SMOOTH.compile(expression, 7L, registry, CAPABILITIES));
    }

    @Test
    public void imageMapStylesCompileTheirBaseNoise() {
        IrisGeneratorStyle image = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        image.setImageMap("heights");
        NoiseRegistry imageRegistry = new NoiseRegistry("iris_export");
        NoiseRegistry baseRegistry = new NoiseRegistry("iris_export");

        Field imageField = SMOOTH.compile(image, 7L, imageRegistry, CAPABILITIES);
        Field baseField = SMOOTH.compile(new IrisGeneratorStyle(NoiseStyle.SIMPLEX), 7L, baseRegistry, CAPABILITIES);

        assertEquals(baseField, imageField);
        assertEquals(baseRegistry.definitions(), imageRegistry.definitions());
    }

    @Test
    public void flatStyleIsConstant() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Field field = SMOOTH.compile(new IrisGeneratorStyle(NoiseStyle.FLAT), 7L, registry, CAPABILITIES);
        assertEquals(1.0, sample(field, registry, 31, -17), 0.0);
    }

    @Test
    public void layerExponentOffsetAndBezierMatchIris() {
        IrisNoiseGenerator layer = flatLayer(0.8, -0.1);
        layer.setExponent(1.7);
        layer.setBezier(true);
        assertLayerMatchesIris(layer, FLOAT_TOLERANCE);
    }

    @Test
    public void layerNegativeAndParametricMatchIris() {
        IrisNoiseGenerator layer = flatLayer(0.6, 0.35);
        layer.setNegative(true);
        layer.setParametric(true);
        assertLayerMatchesIris(layer, FLOAT_TOLERANCE);
    }

    @Test
    public void layerSignedExponentMatchesIris() {
        IrisNoiseGenerator layer = flatLayer(-0.5, -0.4);
        layer.setExponent(2.5);
        assertLayerMatchesIris(layer, FLOAT_TOLERANCE);
    }

    @Test
    public void layerSineCentringMatchesIrisWithinSplineTolerance() {
        IrisNoiseGenerator layer = flatLayer(0.3, 0.05);
        layer.setSinCentered(true);
        assertLayerMatchesIris(layer, 1.0e-4);
    }

    @Test
    public void disabledLayerIsItsOffset() {
        IrisNoiseGenerator layer = flatLayer(0.5, 0.2);
        layer.setEnabled(false);
        assertLayerMatchesIris(layer, FLOAT_TOLERANCE);
    }

    @Test
    public void additiveCompositeMatchesIris() {
        IrisGenerator generator = generator(false);
        assertCompositeMatchesIris(generator);
    }

    @Test
    public void multiplicativeCompositeMatchesIris() {
        IrisGenerator generator = generator(true);
        assertCompositeMatchesIris(generator);
    }

    @Test
    public void zeroOpacityCompositeIsRejected() {
        IrisGenerator generator = new IrisGenerator();
        generator.setComposite(new KList<>(List.of(flatLayer(0.0, 0.1))));
        assertThrows(IllegalArgumentException.class, () -> SMOOTH.compileComposite(generator, SEED, new NoiseRegistry("iris_export"), CAPABILITIES));
    }

    @Test
    public void layerOffsetsTranslateTheSampledNoise() {
        IrisNoiseGenerator plain = new IrisNoiseGenerator();
        plain.setStyle(new IrisGeneratorStyle(NoiseStyle.PERLIN));
        plain.setZoom(2.0);
        IrisNoiseGenerator shifted = new IrisNoiseGenerator();
        shifted.setStyle(new IrisGeneratorStyle(NoiseStyle.PERLIN));
        shifted.setZoom(2.0);
        shifted.setOffsetX(3.0);
        shifted.setOffsetZ(-2.0);
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Field plainField = SMOOTH.compileLayer(plain, SEED, registry, CAPABILITIES);
        Field shiftedField = SMOOTH.compileLayer(shifted, SEED, registry, CAPABILITIES);
        for (int x = -40; x <= 40; x += 13) {
            for (int z = -40; z <= 40; z += 17) {
                assertEquals(sample(plainField, registry, x + 6, z - 4), sample(shiftedField, registry, x, z), 1.0e-9);
            }
        }
    }

    @Test
    public void styledRangeLerpsBetweenItsBounds() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        IrisStyledRange flat = new IrisStyledRange(10.0, 20.0, new IrisGeneratorStyle(NoiseStyle.FLAT));
        assertEquals(15.0, sample(SMOOTH.compileRange(flat, SEED, registry, CAPABILITIES), registry, 0, 0), 0.0);
        IrisStyledRange perlin = new IrisStyledRange(10.0, 20.0, new IrisGeneratorStyle(NoiseStyle.PERLIN));
        Field range = SMOOTH.compileRange(perlin, SEED, registry, CAPABILITIES);
        Field unit = SMOOTH.compile(perlin.getStyle(), SEED, registry, CAPABILITIES);
        for (int x = 0; x < 64; x += 9) {
            assertEquals(10.0 + 10.0 * sample(unit, registry, x, 3 * x), sample(range, registry, x, 3 * x), 20.0 * FLOAT_TOLERANCE);
        }
    }

    @Test
    public void rootWarpExistsOnlyForFracturedStyles() {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Optional<NoiseStyleCompiler.Warp> warp = SMOOTH.compileRootWarp(new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS), SEED, registry, CAPABILITIES);
        assertTrue(warp.isPresent());
        FieldValidator.requireTwoDimensional(warp.get().x(), "warp x");
        FieldValidator.requireTwoDimensional(warp.get().z(), "warp z");
        assertTrue(SMOOTH.compileRootWarp(new IrisGeneratorStyle(NoiseStyle.PERLIN), SEED, registry, CAPABILITIES).isEmpty());
    }

    @Test
    public void layerFractureUnderCellularStyleIsRejected() {
        IrisNoiseGenerator layer = new IrisNoiseGenerator();
        layer.setStyle(new IrisGeneratorStyle(NoiseStyle.CELLULAR));
        layer.setFracture(new KList<>(List.of(flatLayer(1.0, 0.0))));
        NoiseStyleCompiler compiler = new NoiseStyleCompiler((style, salt, registry, capabilities) -> new Field.Const(0.5));
        assertThrows(IllegalArgumentException.class, () -> compiler.compileLayer(layer, SEED, new NoiseRegistry("iris_export"), CAPABILITIES));
    }

    private static void assertLayerMatchesIris(IrisNoiseGenerator layer, double tolerance) {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Field field = SMOOTH.compileLayer(layer, SEED, registry, CAPABILITIES);
        for (int x = -20; x <= 20; x += 10) {
            assertEquals(layer.getNoise(SUPER_SEED, x, -x, null), sample(field, registry, x, -x), tolerance);
        }
    }

    private static void assertCompositeMatchesIris(IrisGenerator generator) {
        NoiseRegistry registry = new NoiseRegistry("iris_export");
        Field field = SMOOTH.compileComposite(generator, SEED, registry, CAPABILITIES);
        for (int x = -30; x <= 30; x += 15) {
            assertEquals(generator.getHeight(x, 2 * x, SUPER_SEED), sample(field, registry, x, 2 * x), FLOAT_TOLERANCE);
        }
    }

    private static IrisGenerator generator(boolean multiplicative) {
        IrisNoiseGenerator first = flatLayer(0.5, 0.1);
        first.setExponent(2.0);
        IrisNoiseGenerator second = flatLayer(1.5, -0.3);
        second.setNegative(true);
        second.setOffsetY(0.25);
        IrisGenerator generator = new IrisGenerator();
        generator.setComposite(new KList<>(List.of(first, second)));
        generator.setOpacity(2.0);
        generator.setZoom(1.5);
        generator.setOffsetX(12.0);
        generator.setMultiplicitive(multiplicative);
        return generator;
    }

    private static IrisNoiseGenerator flatLayer(double opacity, double offsetY) {
        IrisNoiseGenerator layer = new IrisNoiseGenerator();
        layer.setStyle(new IrisGeneratorStyle(NoiseStyle.FLAT));
        layer.setOpacity(opacity);
        layer.setOffsetY(offsetY);
        return layer;
    }

    private static double sample(Field field, NoiseRegistry registry, int x, int z) {
        return FieldEvaluator.sample(field, registry.byReference(), SEED, TARGET, x, 0, z);
    }

    private static int hashNoises(Field root) {
        int count = 0;
        for (Field.Noise noise : FieldWalk.nodesOf(root, Field.Noise.class)) {
            if (noise.noiseId().endsWith(":" + CellHashNoise.NOISE_ID)) {
                count++;
            }
        }
        return count;
    }
}
