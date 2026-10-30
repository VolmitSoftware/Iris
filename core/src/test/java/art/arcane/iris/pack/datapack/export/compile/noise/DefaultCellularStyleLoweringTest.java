package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DefaultCellularStyleLoweringTest {
    private static final String NAMESPACE = "iris_export";
    private static final long QUADRANT_SEED = 1337L;
    private static final ExportCapabilities MODERN = ExportCapabilities.forTarget(DataVersion.V26_3);
    private static final CellularStyleLowering NO_NESTED_STYLES = (style, salt, registry, capabilities) -> {
        throw new AssertionError("Unexpected nested style " + style.getStyle());
    };

    @Test
    public void cellularUsesLibraryOn26_3() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = lowering(new ArrayList<>()).lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR), 7L, registry, MODERN);
        assertFalse(FieldWalk.nodesOf(field, Field.Coord.class).isEmpty());
        assertEquals(9, noisesNamed(field, CellHashNoise.NOISE_ID).size());
        assertTrue(FieldWalk.nodesOf(field, Field.Partition.class).isEmpty());
    }

    @Test
    public void quadrantModeLowersCellValuesFromFourCandidatesUnderTheirOwnNames() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE);
        Field grid = lowering(new ArrayList<>()).lower(style, 7L, registry, MODERN);
        Field quadrant = new DefaultCellularStyleLowering(new ArrayList<>(), NO_NESTED_STYLES, CellularMode.QUADRANT_2X2).lower(style, 7L, registry, MODERN);

        assertEquals(9, noisesNamed(grid, CellHashNoise.NOISE_ID).size());
        assertEquals(4, noisesNamed(quadrant, CellHashNoise.NOISE_ID).size());
        FieldWalk.assertTwoDimensional(quadrant);
        Map<String, Field> gridBodies = new HashMap<>();
        for (Field.Shared shared : FieldWalk.nodesOf(grid, Field.Shared.class)) {
            gridBodies.put(shared.name(), shared.expr());
        }
        for (Field.Shared shared : FieldWalk.nodesOf(quadrant, Field.Shared.class)) {
            Field gridBody = gridBodies.get(shared.name());
            assertTrue(shared.name(), gridBody == null || gridBody.equals(shared.expr()));
        }
    }

    @Test
    public void quadrantCellValuesDrawTheGridCells() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE).setZoom(0.5D);
        Field grid = lowering(new ArrayList<>()).lower(style, 7L, registry, MODERN);
        Field quadrant = new DefaultCellularStyleLowering(new ArrayList<>(), NO_NESTED_STYLES, CellularMode.QUADRANT_2X2).lower(style, 7L, registry, MODERN);
        Map<String, NoiseDef> noises = FieldWalk.noiseDefinitions(registry);

        int points = 0;
        int agreeing = 0;
        for (int x = -300; x < 300; x += 13) {
            for (int z = -300; z < 300; z += 17) {
                points++;
                double expected = FieldEvaluator.sample(grid, noises, QUADRANT_SEED, DataVersion.V26_3, x, 0, z);
                double actual = FieldEvaluator.sample(quadrant, noises, QUADRANT_SEED, DataVersion.V26_3, x, 0, z);
                if (Math.abs(expected - actual) < 1.0e-6D) {
                    agreeing++;
                }
            }
        }

        assertTrue(agreeing + " of " + points, agreeing >= 0.9D * points);
    }

    @Test
    public void volumeModeIsRejectedForPlanarCellValues() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultCellularStyleLowering(new ArrayList<>(), NO_NESTED_STYLES, CellularMode.GRID_3X3X3));
    }

    @Test
    public void exoticStyleFallsBackWithDiagnostic() {
        ExportCapabilities capabilities = MODERN;
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DefaultCellularStyleLowering lowering = lowering(diagnostics);
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field first = lowering.lower(new IrisGeneratorStyle(NoiseStyle.GYROID), 3L, registry, capabilities);
        lowering.lower(new IrisGeneratorStyle(NoiseStyle.GYROID), 4L, registry, capabilities);
        assertEquals(1, diagnostics.size());
        ExportDiagnostic diagnostic = diagnostics.getFirst();
        assertEquals(ExportLimit.NOISE_STYLE, diagnostic.limit());
        assertEquals(ExportDiagnostic.Severity.WARNING, diagnostic.severity());
        assertEquals("noise style GYROID", diagnostic.resource());
        FieldWalk.assertTwoDimensional(first);
        assertEquals(1, noisesNamed(first, LeafNoise.NOISE_ID).size());
        assertTrue(FieldWalk.nodesOf(first, Field.Coord.class).isEmpty());
    }

    @Test
    public void everySupportedStyleLowers() {
        ExportCapabilities capabilities = MODERN;
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        DefaultCellularStyleLowering lowering = lowering(new ArrayList<>());
        List<Field> roots = new ArrayList<>();
        for (NoiseStyle style : NoiseStyle.values()) {
            if (!DefaultCellularStyleLowering.supports(style)) {
                continue;
            }
            Field field = lowering.lower(new IrisGeneratorStyle(style), style.ordinal(), registry, capabilities);
            FieldWalk.assertTwoDimensional(field);
            FieldWalk.assertConstantsWithinLimit(field);
            FieldWalk.assertNoisesRegistered(field, registry);
            roots.add(field);
        }
        FieldWalk.sharedBodies(roots);
    }

    @Test
    public void smoothStylesAreNotCellular() {
        assertFalse(DefaultCellularStyleLowering.supports(NoiseStyle.SIMPLEX));
        assertFalse(DefaultCellularStyleLowering.supports(NoiseStyle.FLAT));
        assertFalse(DefaultCellularStyleLowering.supports(NoiseStyle.CLOVER));
        assertTrue(DefaultCellularStyleLowering.supports(NoiseStyle.CELLULAR_IRIS_DOUBLE));
        assertTrue(DefaultCellularStyleLowering.supports(NoiseStyle.STATIC));
        assertTrue(DefaultCellularStyleLowering.supports(NoiseStyle.HEXAGON));
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        assertThrows(IllegalArgumentException.class, () -> lowering(new ArrayList<>()).lower(new IrisGeneratorStyle(NoiseStyle.SIMPLEX), 1L, registry, MODERN));
        IrisGeneratorStyle expression = new IrisGeneratorStyle(NoiseStyle.CELLULAR).setExpression("example");
        assertThrows(IllegalArgumentException.class, () -> lowering(new ArrayList<>()).lower(expression, 1L, registry, MODERN));
    }

    @Test
    public void smoothStyleUnderCellularFractureDropsTheCellularParts() {
        List<IrisGeneratorStyle> nestedStyles = new ArrayList<>();
        Field marker = new Field.Const(0.25D);
        CellularStyleLowering nested = (style, salt, registry, capabilities) -> {
            nestedStyles.add(style);
            return marker;
        };
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisGeneratorStyle jitter = new IrisGeneratorStyle(NoiseStyle.STATIC).setMultiplier(1.0D);
        IrisGeneratorStyle smooth = new IrisGeneratorStyle(NoiseStyle.FRACTAL_WATER).setZoom(0.15D).setMultiplier(9.0D).setFracture(jitter);
        smooth.setCellularFrequency(2.0D);

        Field field = new DefaultCellularStyleLowering(diagnostics, nested, CellularMode.GRID_3X3).lower(smooth, 5L, new NoiseRegistry(NAMESPACE), MODERN);

        assertSame(marker, field);
        assertEquals(1, nestedStyles.size());
        assertSame(NoiseStyle.FRACTAL_WATER, nestedStyles.getFirst().getStyle());
        assertEquals(0.15D, nestedStyles.getFirst().getZoom(), 0.0D);
        assertNull(nestedStyles.getFirst().getFracture());
        assertEquals(0.0D, nestedStyles.getFirst().getCellularFrequency(), 0.0D);
        assertEquals(1, diagnostics.size());
        assertSame(ExportLimit.NOISE_STYLE, diagnostics.getFirst().limit());
    }

    @Test
    public void signatureVariantsCarryTheirWarp() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        DefaultCellularStyleLowering lowering = lowering(new ArrayList<>());
        assertEquals(0, noisesNamed(lowering.lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR), 1L, registry, MODERN), LeafNoise.NOISE_ID).size());
        assertEquals(6, noisesNamed(lowering.lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS), 1L, registry, MODERN), LeafNoise.NOISE_ID).size());
        assertEquals(8, noisesNamed(lowering.lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE), 1L, registry, MODERN), LeafNoise.NOISE_ID).size());
        assertEquals(2, noisesNamed(lowering.lower(new IrisGeneratorStyle(NoiseStyle.NOWHERE_CELLULAR), 1L, registry, MODERN), LeafNoise.NOISE_ID).size());
        assertEquals(6, noisesNamed(lowering.lower(new IrisGeneratorStyle(NoiseStyle.GLOB_IRIS_THICK), 1L, registry, MODERN), LeafNoise.NOISE_ID).size());
    }

    @Test
    public void styleFractureReplacesSignatureWarp() {
        AtomicInteger calls = new AtomicInteger();
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        String fractureNoise = registry.register(new NoiseDef("test/fracture", -6, List.of(1.0D)));
        CellularStyleLowering nested = (style, salt, nestedRegistry, capabilities) -> {
            calls.incrementAndGet();
            assertEquals(6.0D, style.getZoom(), 1.0e-12D);
            return new Field.Noise(fractureNoise, 1.0D, 0.0D, null, null, null);
        };
        IrisGeneratorStyle fracture = new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setZoom(3.0D).setMultiplier(9.0D);
        IrisGeneratorStyle style = new IrisGeneratorStyle(NoiseStyle.CELLULAR_IRIS_DOUBLE).setZoom(2.0D).setFracture(fracture);
        Field field = new DefaultCellularStyleLowering(new ArrayList<>(), nested, CellularMode.GRID_3X3).lower(style, 5L, registry, MODERN);
        assertEquals(2, calls.get());
        assertEquals(0, noisesNamed(field, LeafNoise.NOISE_ID).size());
        assertEquals(2, noisesNamed(field, "test/fracture").size());
        FieldWalk.assertTwoDimensional(field);
    }

    @Test
    public void zoomScalesCellSize() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = lowering(new ArrayList<>()).lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR).setZoom(2.0D), 1L, registry, MODERN);
        Field.Shared xs = sharedEndingWith(field, "/xs");
        Field.Binary scaled = (Field.Binary) xs.expr();
        assertEquals(Field.BinaryOp.MUL, scaled.op());
        assertEquals(new Field.Const(0.5D), scaled.right());
    }

    @Test
    public void exponentAppliesPower() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        DefaultCellularStyleLowering lowering = lowering(new ArrayList<>());
        Field squared = lowering.lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR).setExponent(2.0D), 1L, registry, MODERN);
        Field.Binary power = (Field.Binary) ((Field.Shared) squared).expr();
        assertEquals(Field.BinaryOp.POW, power.op());
        assertTrue(power.left() instanceof Field.Clamp);
        assertEquals(new Field.Const(2.0D), power.right());
        Field thin = lowering.lower(new IrisGeneratorStyle(NoiseStyle.VASCULAR_THIN), 1L, registry, MODERN);
        Field.Binary thinPower = (Field.Binary) ((Field.Shared) thin).expr();
        assertEquals(new Field.Const(1.0D / 0.65D), thinPower.right());
    }

    @Test
    public void loweringIsDeterministicPerSalt() {
        DefaultCellularStyleLowering lowering = lowering(new ArrayList<>());
        Field first = lowering.lower(new IrisGeneratorStyle(NoiseStyle.SIMPLEX_VASCULAR), 11L, new NoiseRegistry(NAMESPACE), MODERN);
        Field second = lowering.lower(new IrisGeneratorStyle(NoiseStyle.SIMPLEX_VASCULAR), 11L, new NoiseRegistry(NAMESPACE), MODERN);
        Field other = lowering.lower(new IrisGeneratorStyle(NoiseStyle.SIMPLEX_VASCULAR), 12L, new NoiseRegistry(NAMESPACE), MODERN);
        assertEquals(first, second);
        assertEquals(FieldWalk.sharedBodies(List.of(first)).keySet(), FieldWalk.sharedBodies(List.of(second)).keySet());
        assertNotEquals(FieldWalk.sharedBodies(List.of(first)).keySet(), FieldWalk.sharedBodies(List.of(other)).keySet());
    }

    @Test
    public void staticUsesBlockHash() {
        ExportCapabilities capabilities = MODERN;
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = lowering(new ArrayList<>()).lower(new IrisGeneratorStyle(NoiseStyle.STATIC), 1L, registry, capabilities);
        List<Field.Noise> hashes = noisesNamed(field, CellHashNoise.NOISE_ID);
        assertEquals(1, hashes.size());
        assertEquals(CellHashNoise.LATTICE_SPACING, hashes.getFirst().xzScale(), 0.0D);
        assertTrue(FieldWalk.nodesOf(field, Field.Grid.class).isEmpty());
    }

    @Test
    public void bilinearCellularInterpolatesOnTheIrisLattice() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field field = lowering(new ArrayList<>()).lower(new IrisGeneratorStyle(NoiseStyle.CELLULAR_BILINEAR).setZoom(1.5D), 1L, registry, MODERN);
        Field.Grid grid = (Field.Grid) ((Field.Shared) field).expr();
        assertEquals(48, grid.cellXZ());
    }

    @Test
    public void hexagonUsesLattice() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DefaultCellularStyleLowering lowering = lowering(diagnostics);
        Field modern = lowering.lower(new IrisGeneratorStyle(NoiseStyle.HEXAGON), 1L, new NoiseRegistry(NAMESPACE), MODERN);
        assertEquals(4, FieldWalk.nodesOf(modern, Field.Round.class).size());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void cellCracksSelectRaisedCells() {
        DefaultCellularStyleLowering lowering = lowering(new ArrayList<>());
        Field value = new Field.Const(0.25D);
        IrisGenerator flat = new IrisGenerator();
        assertSame(value, lowering.cellCracks(flat, value, 1L, new NoiseRegistry(NAMESPACE), MODERN));
        IrisGenerator cracked = new IrisGenerator().setCellFractureHeight(3.0D).setCellPercentSize(0.6D);
        ExportCapabilities capabilities = MODERN;
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field.RangeIf crack = (Field.RangeIf) lowering.cellCracks(cracked, value, 1L, registry, capabilities);
        assertEquals(0.6D, crack.min(), 0.0D);
        assertSame(value, crack.outOfRange());
        assertEquals(new Field.Binary(Field.BinaryOp.MUL, value, new Field.Const(3.0D)), crack.inRange());
        assertFalse(crack.axes().contains(Axis.Y));
        FieldWalk.assertConstantsWithinLimit(crack);
        FieldWalk.assertNoisesRegistered(crack, registry);
    }

    private static DefaultCellularStyleLowering lowering(List<ExportDiagnostic> diagnostics) {
        return new DefaultCellularStyleLowering(diagnostics, NO_NESTED_STYLES, CellularMode.GRID_3X3);
    }

    private static List<Field.Noise> noisesNamed(Field root, String localId) {
        List<Field.Noise> matches = new ArrayList<>();
        for (Field.Noise noise : FieldWalk.nodesOf(root, Field.Noise.class)) {
            if (noise.noiseId().endsWith(":" + localId)) {
                matches.add(noise);
            }
        }
        return matches;
    }

    private static Field.Shared sharedEndingWith(Field root, String suffix) {
        for (Field.Shared shared : FieldWalk.nodesOf(root, Field.Shared.class)) {
            if (shared.name().endsWith(suffix) && !shared.name().contains("/coord/")) {
                return shared;
            }
        }
        throw new AssertionError("No shared field ending with " + suffix);
    }
}
