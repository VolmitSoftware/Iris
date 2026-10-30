package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GeneratorCompilerTest {
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(DataVersion.V26_3);

    @Test
    public void cliffStepsAreExact() {
        IrisGenerator cliffs = generator("cliffs", layer(NoiseStyle.SIMPLEX, 1.0));
        cliffs.setCliffHeightMin(40);
        cliffs.setCliffHeightMax(40);
        IrisGenerator smooth = generator("cliffs", layer(NoiseStyle.SIMPLEX, 1.0));
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        GeneratorCompiler compiler = compiler(TerrainTestSupport.noiseStyles(), registry);
        Field stepped = compiler.compile(cliffs, "steps");
        Field raw = new GeneratorCompiler(TerrainTestSupport.noiseStyles(), registry, CAPABILITIES, new ArrayList<>()).compile(smooth, "steps");
        double step = 40.0 / 255.0;
        int distinct = 0;
        double previous = Double.NaN;

        for (int x = -400; x <= 400; x += 7) {
            double value = TerrainTestSupport.sample(stepped, registry, DataVersion.V26_3, x, 0, 13);
            double steps = value / step;
            assertEquals("cliff value " + value + " at x " + x, Math.rint(steps), steps, 1.0e-3);
            double expected = Math.round(TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, x, 0, 13) / step) * step;
            assertEquals(expected, value, 1.0e-5);
            if (value != previous) {
                distinct++;
                previous = value;
            }
        }
        assertTrue("cliffs should produce several terraces, got " + distinct, distinct > 3);
    }

    @Test
    public void variableCliffHeightQuantisesByTheLocalStep() {
        IrisGenerator cliffs = generator("variable", layer(NoiseStyle.SIMPLEX, 1.0));
        cliffs.setCliffHeightMin(20);
        cliffs.setCliffHeightMax(80);
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

        Field.Shared compiled = (Field.Shared) compiler(TerrainTestSupport.noiseStyles(), registry).compile(cliffs, "steps");

        Field.Round round = (Field.Round) compiled.expr();
        assertEquals(Field.RoundMode.ROUND, round.mode());
        for (int x = -300; x <= 300; x += 23) {
            double multiple = TerrainTestSupport.sample(round.multiple(), registry, DataVersion.V26_3, x, 0, -5);
            double value = TerrainTestSupport.sample(compiled, registry, DataVersion.V26_3, x, 0, -5);
            assertTrue(multiple >= 20.0 / 255.0 - 1.0e-6 && multiple <= 80.0 / 255.0 + 1.0e-6);
            assertEquals(Math.rint(value / multiple), value / multiple, 1.0e-3);
        }
    }

    @Test
    public void bezierLayerMatchesTheIrisCurve() {
        IrisNoiseGenerator curved = layer(NoiseStyle.SIMPLEX, 1.0);
        curved.setBezier(true);
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field shaped = compiler(TerrainTestSupport.noiseStyles(), registry).compile(generator("curve", curved), "curve");
        Field raw = compiler(TerrainTestSupport.noiseStyles(), registry).compile(generator("curve", layer(NoiseStyle.SIMPLEX, 1.0)), "curve");

        for (int x = -200; x <= 200; x += 19) {
            double source = TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, x, 0, 4);
            assertEquals(IrisInterpolation.bezier(source), TerrainTestSupport.sample(shaped, registry, DataVersion.V26_3, x, 0, 4), 1.0e-6);
        }
    }

    @Test
    public void layersAverageByOpacity() {
        IrisNoiseGenerator heavy = layer(NoiseStyle.SIMPLEX, 1.0);
        IrisNoiseGenerator light = layer(NoiseStyle.PERLIN, 0.5);
        light.setSeed(9);
        IrisGenerator blended = generator("blend", heavy, light);
        blended.setOpacity(0.8);
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        GeneratorCompiler compiler = compiler(TerrainTestSupport.constantStyles(0.25), registry);

        Field value = compiler.compile(blended, "blend");

        assertEquals((0.25 + 0.25 * 0.5) / 1.5 * 0.8, TerrainTestSupport.sample(value, registry, DataVersion.V26_3, 3, 0, 3), 1.0e-6);
    }

    @Test
    public void zoomsCombineIntoTheCompiledStyle() {
        List<Double> zooms = new ArrayList<>();
        StyleCompiler recording = (style, seedSalt, registry, capabilities) -> {
            zooms.add(style.getZoom());
            return new Field.Const(0.5);
        };
        IrisNoiseGenerator zoomed = layer(NoiseStyle.SIMPLEX, 1.0);
        zoomed.setZoom(3.0);
        zoomed.getStyle().setZoom(2.0);
        IrisGenerator generator = generator("zoom", zoomed);
        generator.setZoom(5.0);

        compiler(recording, new NoiseRegistry(TerrainTestSupport.NAMESPACE)).compile(generator, "zoom");

        assertEquals(List.of(30.0), zooms);
        assertEquals(2.0, zoomed.getStyle().getZoom(), 0.0);
    }

    @Test
    public void surfaceDetailBlendsTheSixBlockGrid() {
        IrisGenerator detailed = generator("detail", layer(NoiseStyle.SIMPLEX, 1.0));
        detailed.setSurfaceDetail(0.5);
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field blended = compiler(TerrainTestSupport.noiseStyles(), registry).compile(detailed, "detail");
        Field raw = compiler(TerrainTestSupport.noiseStyles(), registry).compile(generator("detail", layer(NoiseStyle.SIMPLEX, 1.0)), "detail");

        double low = TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, 12, 0, 18);
        double high = TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, 18, 0, 18);
        double middle = TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, 15, 0, 18);
        assertEquals(low, TerrainTestSupport.sample(blended, registry, DataVersion.V26_3, 12, 0, 18), 1.0e-6);
        assertEquals(0.5 * middle + 0.25 * (low + high), TerrainTestSupport.sample(blended, registry, DataVersion.V26_3, 15, 0, 18), 1.0e-6);
    }

    @Test
    public void rawGeneratorSkipsTheSurfaceGridAndSharesTheBlendInput() {
        IrisGenerator detailed = generator("detail", layer(NoiseStyle.SIMPLEX, 1.0));
        detailed.setSurfaceDetail(0.5);
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field blended = compiler(TerrainTestSupport.noiseStyles(), registry).compile(detailed, "detail");
        GeneratorCompiler compiler = compiler(TerrainTestSupport.noiseStyles(), registry);

        Field raw = compiler.compileRaw(detailed, "detail");

        assertSame(raw, compiler.compileRaw(detailed, "detail"));
        assertTrue(TerrainTestSupport.contains(blended, raw));
        assertFalse(containsGrid(raw));
        assertTrue(containsGrid(blended));
        assertEquals(TerrainTestSupport.sample(blended, registry, DataVersion.V26_3, 12, 0, 18),
                TerrainTestSupport.sample(raw, registry, DataVersion.V26_3, 12, 0, 18), 1.0e-12);
    }

    @Test
    public void generatorFieldsAreTwoDimensionalAndShared() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

        Field compiled = compiler(TerrainTestSupport.noiseStyles(), registry).compile(generator("Mixed Case/hills", layer(NoiseStyle.SIMPLEX, 1.0)), "height");

        assertEquals("terrain/generator/height/mixed_case/hills", ((Field.Shared) compiled).name());
        assertFalse(compiled.axes().contains(Axis.Y));
    }

    @Test
    public void unsupportedLayerFeaturesReportDiagnostics() {
        IrisNoiseGenerator octaves = layer(NoiseStyle.SIMPLEX, 1.0);
        octaves.setOctaves(3);
        IrisGenerator generator = generator("octaves", octaves);
        generator.setCellFractureHeight(4.0);
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        new GeneratorCompiler(TerrainTestSupport.noiseStyles(), new NoiseRegistry(TerrainTestSupport.NAMESPACE), CAPABILITIES, diagnostics)
                .compile(generator, "height");

        assertEquals(2, diagnostics.size());
        for (ExportDiagnostic diagnostic : diagnostics) {
            assertEquals(ExportLimit.NOISE_STYLE, diagnostic.limit());
            assertEquals("generators/octaves", diagnostic.resource());
        }
    }

    @Test
    public void compileIsDeterministic() {
        IrisGenerator first = generator("same", layer(NoiseStyle.SIMPLEX, 1.0), layer(NoiseStyle.PERLIN, 0.5));
        IrisGenerator second = generator("same", layer(NoiseStyle.SIMPLEX, 1.0), layer(NoiseStyle.PERLIN, 0.5));
        NoiseRegistry firstRegistry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        NoiseRegistry secondRegistry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);

        Field one = compiler(TerrainTestSupport.noiseStyles(), firstRegistry).compile(first, "height");
        Field two = compiler(TerrainTestSupport.noiseStyles(), secondRegistry).compile(second, "height");

        assertEquals(one, two);
        assertEquals(firstRegistry.definitions(), secondRegistry.definitions());
    }

    private static GeneratorCompiler compiler(StyleCompiler styles, NoiseRegistry registry) {
        return new GeneratorCompiler(styles, registry, CAPABILITIES, new ArrayList<>());
    }

    private static IrisGenerator generator(String key, IrisNoiseGenerator... layers) {
        IrisGenerator generator = new IrisGenerator();
        generator.setLoadKey(key);
        generator.setComposite(new KList<>(List.of(layers)));
        return generator;
    }

    private static IrisNoiseGenerator layer(NoiseStyle style, double opacity) {
        IrisNoiseGenerator layer = new IrisNoiseGenerator();
        layer.setStyle(new IrisGeneratorStyle(style));
        layer.setOpacity(opacity);
        return layer;
    }

    private static boolean containsGrid(Field root) {
        if (root instanceof Field.Grid) {
            return true;
        }
        for (Field child : FieldMacros.children(root)) {
            if (containsGrid(child)) {
                return true;
            }
        }
        return false;
    }
}
