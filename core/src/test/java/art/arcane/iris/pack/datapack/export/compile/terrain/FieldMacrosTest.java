package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FieldMacrosTest {
    private static final double EPSILON = 1.0e-5;
    private static final Field X = new Field.Coord(Axis.X, Field.Tiling.CLAMP, -4096, 4096, -40.96, 40.96);
    private static final Field Y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -64, 320, -64, 320);

    @Test
    public void offsetEvaluatesTheExpressionAtTheShiftedPosition() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        String id = registry.register(new NoiseDef("macro/base", -4, List.of(1.0, 0.5)));
        String warpId = registry.register(new NoiseDef("macro/warp", -6, List.of(1.0)));
        Field warp = new Field.Noise(warpId, 0.5, 0.0, null, null, null);
        Field noise = new Field.Noise(id, 0.75, 0.0, new Field.Binary(Field.BinaryOp.MUL, new Field.Const(8.0), warp), null, null);
        Field expr = new Field.Binary(Field.BinaryOp.ADD, noise, X);

        Field shifted = FieldMacros.offset(expr, 7.0, -3.0);

        for (int x = -40; x <= 40; x += 9) {
            for (int z = -30; z <= 30; z += 11) {
                assertEquals(TerrainTestSupport.sample(expr, registry, DataVersion.V26_3, x + 7, 0, z - 3),
                        TerrainTestSupport.sample(shifted, registry, DataVersion.V26_3, x, 0, z), EPSILON);
            }
        }
    }

    @Test
    public void offsetRenamesChangedSharedSubtreesOnly() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        String id = registry.register(new NoiseDef("macro/base", -4, List.of(1.0)));
        Field vertical = new Field.Shared("vertical", Y);
        Field horizontal = new Field.Shared("horizontal", new Field.Noise(id, 1.0, 0.0, null, null, null));

        Field shifted = FieldMacros.offset(new Field.Binary(Field.BinaryOp.ADD, vertical, horizontal), 4.0, 0.0);

        Field.Binary sum = (Field.Binary) shifted;
        assertSame(vertical, sum.left());
        assertEquals("horizontal/offset_x4_z0", ((Field.Shared) sum.right()).name());
    }

    @Test
    public void offsetOfReferenceFails() {
        assertThrows(IllegalArgumentException.class, () -> FieldMacros.offset(new Field.Ref("minecraft:overworld/continents"), 1.0, 0.0));
    }

    @Test
    public void signedDistanceOfALinearRampIsTheBlockDistance() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        DataVersion target = DataVersion.V26_3;
        Field distance = FieldMacros.signedDistance(X, 0.5, 4.0);

        assertEquals(-20.0, TerrainTestSupport.sample(distance, registry, target, 30, 0, 0), 0.01);
        assertEquals(15.0, TerrainTestSupport.sample(distance, registry, target, 65, 0, 9), 0.01);
    }

    @Test
    public void substituteSwapsTheTargetAndRenamesChangedSharedSubtrees() {
        Field target = new Field.Shared("target", X);
        Field replacement = new Field.Const(5.0);
        Field untouched = new Field.Shared("untouched", new Field.Unary(Field.UnaryOp.ABS, X));
        Field root = new Field.Binary(Field.BinaryOp.ADD, new Field.Shared("wrapper", new Field.Binary(Field.BinaryOp.MUL, target, target)), untouched);

        Field.Binary result = (Field.Binary) FieldMacros.substitute(root, target, replacement, "swap");

        assertEquals(new Field.Shared("wrapper/swap", new Field.Binary(Field.BinaryOp.MUL, replacement, replacement)), result.left());
        assertTrue(result.right() == untouched);
        assertTrue(FieldMacros.substitute(root, target, target, "swap") == root);
    }

    @Test
    public void productGuardsAnExpensivePayloadBehindTheMask() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        String first = registry.register(new NoiseDef("macro/first", -4, List.of(1.0)));
        String second = registry.register(new NoiseDef("macro/second", -5, List.of(1.0)));
        Field mask = new Field.Noise(first, 1.0, 0.0, null, null, null);
        Field payload = new Field.Binary(Field.BinaryOp.ADD, mask, new Field.Noise(second, 1.0, 0.0, null, null, null));

        Field product = FieldMacros.product(mask, payload);

        Field.Binary multiply = (Field.Binary) product;
        assertSame(mask, multiply.left());
        assertTrue(multiply.right() instanceof Field.Gate);
        assertTrue(TerrainTestSupport.unguardedExpensiveBranches(product).isEmpty());
        for (int x = -50; x <= 50; x += 17) {
            double expected = TerrainTestSupport.sample(mask, registry, DataVersion.V26_3, x, 0, 3)
                    * TerrainTestSupport.sample(payload, registry, DataVersion.V26_3, x, 0, 3);
            assertEquals(expected, TerrainTestSupport.sample(product, registry, DataVersion.V26_3, x, 0, 3), 1.0e-6);
        }
    }

    @Test
    public void unguardedExpensiveBranchesAreDetected() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field first = new Field.Noise(registry.register(new NoiseDef("macro/first", -4, List.of(1.0))), 1.0, 0.0, null, null, null);
        Field second = new Field.Noise(registry.register(new NoiseDef("macro/second", -5, List.of(1.0))), 1.0, 0.0, null, null, null);
        Field expensive = new Field.Binary(Field.BinaryOp.ADD, first, second);

        assertEquals(1, TerrainTestSupport.unguardedExpensiveBranches(new Field.Binary(Field.BinaryOp.MUL, first, expensive)).size());
        assertEquals(1, TerrainTestSupport.unguardedExpensiveBranches(new Field.Lerp(first, expensive, second)).size());
        assertEquals(1, TerrainTestSupport.unguardedExpensiveBranches(new Field.RangeIf(first, 0.0, 1.0, second, expensive)).size());
        assertTrue(TerrainTestSupport.unguardedExpensiveBranches(new Field.Binary(Field.BinaryOp.MUL, new Field.Const(2.0), expensive)).isEmpty());
        assertTrue(TerrainTestSupport.unguardedExpensiveBranches(new Field.Gate(first, new Field.Binary(Field.BinaryOp.ADD, expensive, X))).isEmpty());
    }

    @Test
    public void liftGivesHorizontalNoiseAVerticalScale() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        String id = registry.register(new NoiseDef("macro/base", -4, List.of(1.0)));
        Field flat = new Field.Noise(id, 0.5, 0.0, null, null, null);

        Field lifted = FieldMacros.lift(flat, 3.0);

        assertEquals(new Field.Noise(id, 0.5, 1.5, null, null, null), lifted);
        assertTrue(lifted.axes().contains(Axis.Y));
    }

    @Test
    public void smoothstepIsTheZeroDerivativeHermite() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field ramp = FieldMacros.smoothstep(X, 0.0, 0.4);

        assertEquals(0.0, TerrainTestSupport.sample(ramp, registry, DataVersion.V26_3, -5, 0, 0), 0.0);
        assertEquals(0.5, TerrainTestSupport.sample(ramp, registry, DataVersion.V26_3, 20, 0, 0), 1.0e-5);
        assertEquals(0.15625, TerrainTestSupport.sample(ramp, registry, DataVersion.V26_3, 10, 0, 0), 1.0e-5);
        assertEquals(1.0, TerrainTestSupport.sample(ramp, registry, DataVersion.V26_3, 90, 0, 0), 0.0);
    }

    @Test
    public void lookupFollowsTheFunction() {
        NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
        Field input = FieldMacros.scale(X, 10.0);
        Field sine = FieldMacros.lookup(input, value -> Math.sin(Math.PI * value), value -> Math.PI * Math.cos(Math.PI * value), 0.0, 1.0, 16);

        for (int x = 0; x <= 10; x++) {
            assertEquals(Math.sin(Math.PI * x / 10.0), TerrainTestSupport.sample(sine, registry, DataVersion.V26_3, x, 0, 0), 1.0e-4);
        }
    }

    @Test
    public void sumFoldsConstantsAndBalancesTerms() {
        Field sum = FieldMacros.sum(List.of(new Field.Const(1.0), X, X, new Field.Const(2.0), X, X));

        Field.Binary root = (Field.Binary) sum;
        assertEquals(new Field.Const(3.0), root.right());
        assertEquals(new Field.Binary(Field.BinaryOp.ADD,
                new Field.Binary(Field.BinaryOp.ADD, X, X),
                new Field.Binary(Field.BinaryOp.ADD, X, X)), root.left());
    }
}
