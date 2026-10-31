package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BandTreeCompilerTest {
    private static final Map<String, Double> VALUES = Map.of(TerrainTestSupport.NAMESPACE + ":a", 10.0, TerrainTestSupport.NAMESPACE + ":b", 80.0);

    @Test
    public void hardBandFollowsTheLayoutCodes() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        BandTreeCompiler bands = new BandTreeCompiler(codes, "test");

        Field hard = bands.hard(VALUES);

        for (int x = -2000; x <= 2000; x += 97) {
            double code = TerrainTestSupport.sample(codes.surfaceCode(), registry, DataVersion.V26_3, x, 0, 31);
            double expected = code < 0.2 ? 10.0 : 80.0;
            assertEquals(expected, TerrainTestSupport.sample(hard, registry, DataVersion.V26_3, x, 0, 31), 0.0);
        }
    }

    @Test
    public void blendMatchesTheLeavesAwayFromTheThreshold() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        BandTreeCompiler bands = new BandTreeCompiler(codes, "test");
        Field blend = bands.band(VALUES, interpolator(InterpolationMethod.BILINEAR_STARCAST_9, 12.0));
        Field hard = bands.hard(VALUES);
        int blended = 0;

        for (int x = -3000; x <= 3000; x += 13) {
            double value = TerrainTestSupport.sample(blend, registry, DataVersion.V26_3, x, 0, 7);
            double exact = TerrainTestSupport.sample(hard, registry, DataVersion.V26_3, x, 0, 7);
            assertTrue(value >= 10.0 - 1.0e-3 && value <= 80.0 + 1.0e-3);
            if (Math.abs(value - exact) > 1.0e-3) {
                blended++;
            }
        }
        assertTrue("blend should soften some columns, got " + blended, blended > 0);
        assertTrue("blend should leave most columns at their leaf value, got " + blended, blended < 300);
        assertFalse(blend.axes().contains(Axis.Y));
    }

    @Test
    public void starcastBlendsAverageThreeLadderLattices() {
        assertEquals(List.of(23, 32, 45), BandTreeCompiler.lattices(interpolator(InterpolationMethod.BILINEAR_STARCAST_9, 12.0)));
        assertEquals(List.of(362, 512, 724), BandTreeCompiler.lattices(interpolator(InterpolationMethod.BILINEAR_STARCAST_12, 200.0)));
        assertEquals(List.of(64, 91, 128), BandTreeCompiler.lattices(interpolator(InterpolationMethod.STARCAST_12, 42.0)));
        assertEquals(List.of(30), BandTreeCompiler.lattices(interpolator(InterpolationMethod.BILINEAR, 30.0)));
        assertEquals(List.of(), BandTreeCompiler.lattices(interpolator(InterpolationMethod.NONE, 30.0)));
    }

    @Test
    public void blendSamplesTheSelectorWithoutOffsetCopies() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        BandTreeCompiler bands = new BandTreeCompiler(codes, "test");
        Map<String, Double> other = Map.of(TerrainTestSupport.NAMESPACE + ":a", 3.0, TerrainTestSupport.NAMESPACE + ":b", 40.0);

        Field first = bands.band(VALUES, interpolator(InterpolationMethod.BILINEAR_STARCAST_12, 200.0));
        Field second = bands.band(other, interpolator(InterpolationMethod.BILINEAR_STARCAST_9, 26.0));

        Set<Field> noises = new HashSet<>(nodes(first, Field.Noise.class));
        noises.addAll(nodes(second, Field.Noise.class));
        assertEquals(new HashSet<>(nodes(codes.surfaceCode(), Field.Noise.class)), noises);
        Set<String> lattices = new TreeSet<>();
        for (Field shared : nodes(new Field.Binary(Field.BinaryOp.ADD, first, second), Field.Shared.class)) {
            String name = ((Field.Shared) shared).name();
            if (name.startsWith("terrain/test/blend/lattice/")) {
                lattices.add(name);
            }
        }
        assertEquals(Set.of("terrain/test/blend/lattice/362", "terrain/test/blend/lattice/512", "terrain/test/blend/lattice/724",
                "terrain/test/blend/lattice/45", "terrain/test/blend/lattice/64", "terrain/test/blend/lattice/91"), lattices);
    }

    @Test
    public void noneInterpolationIsTheHardBand() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BandTreeCompiler bands = new BandTreeCompiler(TerrainTestSupport.twoBiomeCodes(registry, 0.0), "test");

        assertEquals(bands.hard(VALUES), bands.band(VALUES, interpolator(InterpolationMethod.NONE, 30.0)));
    }

    @Test
    public void equalValuesCollapseToAConstant() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BandTreeCompiler bands = new BandTreeCompiler(TerrainTestSupport.twoBiomeCodes(registry, 0.0), "test");
        Map<String, Double> flat = Map.of(TerrainTestSupport.NAMESPACE + ":a", 5.0, TerrainTestSupport.NAMESPACE + ":b", 5.0);

        assertEquals(new Field.Const(5.0), bands.band(flat, interpolator(InterpolationMethod.BILINEAR_STARCAST_9, 12.0)));
    }

    @Test
    public void baseCodeSubtreeDrivesTheBands() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BiomeCodes plain = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        Field base = new Field.Shared(BiomeCodes.BASE_CODE, plain.surfaceCode());
        Field relabelled = new Field.RangeIf(base, 0.25, 0.35, base, new Field.Const(0.1));
        BiomeCodes codes = new BiomeCodes(relabelled, plain.caveCode(), plain.class3d(), plain.layout(), plain.loadKeyByBiomeId());

        Field selector = BandTreeCompiler.selector(codes);

        assertEquals(base, selector);
    }

    @Test
    public void heightDependentCodeWithoutBaseCodeIsRejected() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        BiomeCodes plain = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
        Field height = new Field.Shared(HeightCompiler.HEIGHT, new Field.Const(63.0));
        Field relabelled = new Field.RangeIf(height, 60.0, 70.0, new Field.Const(0.3), plain.surfaceCode());
        BiomeCodes codes = new BiomeCodes(relabelled, plain.caveCode(), plain.class3d(), plain.layout(), plain.loadKeyByBiomeId());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> BandTreeCompiler.selector(codes));

        assertTrue(failure.getMessage(), failure.getMessage().contains(BiomeCodes.BASE_CODE));
    }

    private static IrisInterpolator interpolator(InterpolationMethod method, double radius) {
        IrisInterpolator interpolator = new IrisInterpolator();
        interpolator.setFunction(method);
        interpolator.setHorizontalScale(radius);
        return interpolator;
    }

    private static List<Field> nodes(Field root, Class<? extends Field> type) {
        List<Field> found = new ArrayList<>();
        for (Field field : TerrainTestSupport.nodes(root)) {
            if (type.isInstance(field)) {
                found.add(field);
            }
        }
        return found;
    }
}
