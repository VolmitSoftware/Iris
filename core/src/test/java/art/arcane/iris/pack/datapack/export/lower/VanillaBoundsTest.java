package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VanillaBoundsTest {
    private static final String NAMESPACE = "iris_export";
    private static final Field NOISE = new Field.Noise("iris_export:base", 0.25, 0.0, null, null, null);
    private static final Field UNREGISTERED = new Field.Noise("iris_export:missing", 0.25, 0.0, null, null, null);
    private static final Field BAND = new Field.Clamp(NOISE, 0.25, 0.75);
    private static final VanillaBounds BOUNDS = VanillaBounds.of(NAMESPACE, List.of(new NoiseDef("base", -4, List.of(1.0, 0.5))));

    @Test
    public void noiseRangeIsTwiceTheAmplitudeSum() {
        assertEquals(Optional.of(new ValueRange(-2.5, 2.5)), BOUNDS.range(NOISE));
    }

    @Test
    public void unregisteredNoiseHasNoRange() {
        assertEquals(Optional.empty(), BOUNDS.range(UNREGISTERED));
        assertEquals(Optional.empty(), BOUNDS.range(new Field.Binary(Field.BinaryOp.ADD, UNREGISTERED, new Field.Const(1.0))));
    }

    @Test
    public void splineRangeIncludesTheHermiteAllowance() {
        Field curve = new Field.Curve(new Field.Clamp(NOISE, 0.0, 1.0), List.of(
                new Field.Knot(0.0, new Field.Const(0.0), 2.0), new Field.Knot(1.0, new Field.Const(1.0), 2.0)));
        assertEquals(Optional.of(new ValueRange(-0.25, 1.25)), BOUNDS.range(curve));
    }

    @Test
    public void constantsUseTheirFloatValue() {
        assertEquals(Optional.of(ValueRange.point((float) 0.1)), BOUNDS.range(new Field.Const(0.1)));
    }

    @Test
    public void disjointMaxFoldsToTheInputThatAlwaysWins() {
        Field.Binary floor = new Field.Binary(Field.BinaryOp.MAX, BAND, new Field.Const(0.0));
        Field.Binary ceiling = new Field.Binary(Field.BinaryOp.MAX, new Field.Const(1.0), BAND);
        assertSame(BAND, BOUNDS.fold(floor));
        assertEquals(1.0, ((Field.Const) BOUNDS.fold(ceiling)).value(), 0.0);
        assertEquals(BOUNDS.range(BAND), BOUNDS.range(floor));
    }

    @Test
    public void disjointMinFoldsToTheInputThatAlwaysWins() {
        Field scaled = new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.3), BAND);
        Field.Binary capped = new Field.Binary(Field.BinaryOp.MIN, new Field.Const(2.0), scaled);
        Field.Binary lifted = new Field.Binary(Field.BinaryOp.MIN, BAND, new Field.Const(-1.0));
        assertSame(scaled, BOUNDS.fold(capped));
        assertEquals(-1.0, ((Field.Const) BOUNDS.fold(lifted)).value(), 0.0);
    }

    @Test
    public void touchingOrOverlappingInputsStay() {
        Field.Binary touching = new Field.Binary(Field.BinaryOp.MAX, new Field.Const(0.25), BAND);
        Field.Binary overlapping = new Field.Binary(Field.BinaryOp.MIN, new Field.Const(0.5), BAND);
        assertSame(touching, BOUNDS.fold(touching));
        assertSame(overlapping, BOUNDS.fold(overlapping));
    }

    @Test
    public void foldUsesMinecraftBoundsRatherThanTheFieldEstimate() {
        Field.Binary capped = new Field.Binary(Field.BinaryOp.MIN, NOISE, new Field.Const(1.5));
        assertTrue(NOISE.range().max() < 1.5);
        assertSame(capped, BOUNDS.fold(capped));
    }

    @Test
    public void unknownInputsNeverFold() {
        Field.Binary unknown = new Field.Binary(Field.BinaryOp.MAX, new Field.Clamp(UNREGISTERED, 0.25, 0.75), new Field.Const(0.0));
        Field.Binary external = new Field.Binary(Field.BinaryOp.MAX, new Field.Ref("minecraft:overworld/continents"), new Field.Const(1.0e6));
        assertSame(unknown, BOUNDS.fold(unknown));
        assertSame(external, BOUNDS.fold(external));
    }

    @Test
    public void otherOperatorsNeverFold() {
        Field.Binary sum = new Field.Binary(Field.BinaryOp.ADD, BAND, new Field.Const(5.0));
        assertSame(sum, BOUNDS.fold(sum));
    }
}
