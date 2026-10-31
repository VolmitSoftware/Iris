package art.arcane.iris.pack.datapack.export.ir;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FieldTypingTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    public void noiseWithZeroYScaleIsTwoDimensional() {
        Field noise = noise(0.25, 0.0);
        assertEquals(EnumSet.of(Axis.X, Axis.Z), noise.axes());
        assertSame(CostClass.NOISE, noise.cost());
    }

    @Test
    public void noiseWithYScaleIsThreeDimensional() {
        assertEquals(EnumSet.allOf(Axis.class), noise(0.25, 0.5).axes());
    }

    @Test
    public void noiseAxesIncludeShiftAxes() {
        Field shiftY = coord(Axis.Y);
        Field noise = new Field.Noise("iris_export:base", 0.25, 0.0, null, shiftY, null);
        assertEquals(EnumSet.allOf(Axis.class), noise.axes());
    }

    @Test
    public void latticeNoiseTakesAxesOnlyFromShifts() {
        Field noise = new Field.Noise("iris_export:cell_hash", 0.0, 0.0, coord(Axis.X), null, null);
        assertEquals(EnumSet.of(Axis.X), noise.axes());
    }

    @Test
    public void noiseShiftedByNoiseIsExpensive() {
        Field warped = new Field.Noise("iris_export:base", 0.25, 0.0, noise(0.1, 0.0), null, null);
        assertSame(CostClass.EXPENSIVE, warped.cost());
    }

    @Test
    public void binaryUnionsAxes() {
        Field sum = new Field.Binary(Field.BinaryOp.ADD, coord(Axis.X), coord(Axis.Y));
        assertEquals(EnumSet.of(Axis.X, Axis.Y), sum.axes());
        assertSame(CostClass.CHEAP, sum.cost());
    }

    @Test
    public void constIsCheapAndAxisFree() {
        Field constant = new Field.Const(3.0);
        assertTrue(constant.axes().isEmpty());
        assertSame(CostClass.CHEAP, constant.cost());
        assertEquals(new ValueRange(3.0, 3.0), constant.range());
    }

    @Test
    public void refIsCheapAndConservative() {
        Field ref = new Field.Ref("minecraft:overworld/continents");
        assertSame(CostClass.CHEAP, ref.cost());
        assertEquals(EnumSet.allOf(Axis.class), ref.axes());
        assertEquals(ValueRange.UNBOUNDED, ref.range());
    }

    @Test
    public void combiningTwoNoisesIsExpensive() {
        Field sum = new Field.Binary(Field.BinaryOp.ADD, noise(0.1, 0.0), noise(0.2, 0.0));
        assertSame(CostClass.EXPENSIVE, sum.cost());
        Field scaled = new Field.Binary(Field.BinaryOp.MUL, noise(0.1, 0.0), new Field.Const(2.0));
        assertSame(CostClass.NOISE, scaled.cost());
    }

    @Test
    public void muxOfExpensiveCasesIsExpensive() {
        Field expensive = new Field.Binary(Field.BinaryOp.ADD, noise(0.1, 0.0), noise(0.2, 0.0));
        Field index = partition(coord(Axis.X));
        Field mux = new Field.Mux(index, List.of(expensive, expensive));
        assertSame(CostClass.EXPENSIVE, mux.cost());
    }

    @Test
    public void muxOfNoiseCasesInheritsMaxNotSum() {
        Field index = partition(coord(Axis.X));
        Field mux = new Field.Mux(index, List.of(noise(0.1, 0.0), noise(0.2, 0.0), new Field.Const(0.0)));
        assertSame(CostClass.NOISE, mux.cost());
        assertEquals(new ValueRange(-1.0, 1.0), mux.range());
    }

    @Test
    public void gateRangeIncludesZero() {
        Field gate = new Field.Gate(coord(Axis.X), new Field.Const(5.0));
        assertEquals(new ValueRange(0.0, 5.0), gate.range());
    }

    @Test
    public void gridAndSharedInheritTraits() {
        Field base = noise(0.1, 0.5);
        Field grid = new Field.Grid(base, 4, 8);
        Field shared = new Field.Shared("terrain/base", grid);
        assertEquals(base.axes(), shared.axes());
        assertEquals(base.range(), shared.range());
        assertSame(base.cost(), shared.cost());
    }

    @Test
    public void partitionRejectsNonCheapLeaves() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Partition(coord(Axis.X), List.of(0.0), List.of(new Field.Const(1.0), noise(0.1, 0.0))));
    }

    @Test
    public void partitionRequiresOneMoreLeafThanThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Partition(coord(Axis.X), List.of(0.0, 1.0), List.of(new Field.Const(1.0), new Field.Const(2.0))));
    }

    @Test
    public void partitionRejectsDescendingThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Partition(coord(Axis.X), List.of(1.0, 0.0), List.of(new Field.Const(1.0), new Field.Const(2.0), new Field.Const(3.0))));
    }

    @Test
    public void partitionRangeCoversReachableLeavesOnly() {
        Field input = new Field.Clamp(coord(Axis.X), 0.0, 0.4);
        Field partition = new Field.Partition(input, List.of(0.5, 1.0), List.of(new Field.Const(1.0), new Field.Const(2.0), new Field.Const(3.0)));
        assertEquals(new ValueRange(1.0, 1.0), partition.range());
    }

    @Test
    public void clampNarrowsRange() {
        Field clamp = new Field.Clamp(noise(0.1, 0.0), -0.5, 0.25);
        assertEquals(new ValueRange(-0.5, 0.25), clamp.range());
        Field constant = new Field.Clamp(new Field.Const(3.0), 0.0, 1.0);
        assertEquals(new ValueRange(1.0, 1.0), constant.range());
    }

    @Test
    public void clampRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Clamp(coord(Axis.X), 1.0, 0.0));
    }

    @Test
    public void arithmeticRangesFollowIntervalRules() {
        Field a = new Field.Clamp(coord(Axis.X), -1.0, 2.0);
        Field b = new Field.Clamp(coord(Axis.Z), 3.0, 4.0);
        assertEquals(new ValueRange(2.0, 6.0), new Field.Binary(Field.BinaryOp.ADD, a, b).range());
        assertEquals(new ValueRange(-5.0, -1.0), new Field.Binary(Field.BinaryOp.SUB, a, b).range());
        assertEquals(new ValueRange(-4.0, 8.0), new Field.Binary(Field.BinaryOp.MUL, a, b).range());
        assertEquals(new ValueRange(-1.0 / 3.0, 2.0 / 3.0), new Field.Binary(Field.BinaryOp.DIV, a, b).range());
        assertEquals(ValueRange.UNBOUNDED, new Field.Binary(Field.BinaryOp.DIV, b, a).range());
        assertEquals(new ValueRange(0.0, 2.0), new Field.Unary(Field.UnaryOp.ABS, a).range());
        assertEquals(new ValueRange(0.0, 4.0), new Field.Unary(Field.UnaryOp.SQUARE, a).range());
        assertEquals(new ValueRange(-0.5, 2.0), new Field.Unary(Field.UnaryOp.HALF_NEG, a).range());
    }

    @Test
    public void lerpWithUnitAlphaStaysBetweenEnds() {
        Field alpha = new Field.Clamp(coord(Axis.X), 0.0, 1.0);
        Field lerp = new Field.Lerp(alpha, new Field.Const(-2.0), new Field.Const(6.0));
        assertEquals(new ValueRange(-2.0, 6.0), lerp.range());
    }

    @Test
    public void roundByConstantMultipleQuantisesBounds() {
        Field input = new Field.Clamp(coord(Axis.X), 0.2, 9.7);
        Field round = new Field.Round(Field.RoundMode.FLOOR, input, new Field.Const(2.0));
        assertEquals(new ValueRange(0.0, 8.0), round.range());
    }

    @Test
    public void curveKnotValueMustBeConstOrCurve() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Knot(0.0, coord(Axis.X), 0.0));
        Field.Curve nested = curve(coord(Axis.Z));
        Field.Knot constantKnot = new Field.Knot(0.0, new Field.Const(1.0), 0.0);
        Field.Knot nestedKnot = new Field.Knot(1.0, nested, 0.0);
        Field.Curve outer = new Field.Curve(coord(Axis.X), List.of(constantKnot, nestedKnot));
        assertEquals(EnumSet.of(Axis.X, Axis.Z), outer.axes());
    }

    @Test
    public void curveRejectsUnorderedKnots() {
        Field.Knot first = new Field.Knot(1.0, new Field.Const(0.0), 0.0);
        Field.Knot second = new Field.Knot(0.0, new Field.Const(1.0), 0.0);
        assertThrows(IllegalArgumentException.class, () -> new Field.Curve(coord(Axis.X), List.of(first, second)));
    }

    @Test
    public void curveRangeCoversHermiteOvershoot() {
        Field input = new Field.Clamp(coord(Axis.X), 0.0, 1.0);
        Field.Knot first = new Field.Knot(0.0, new Field.Const(0.0), 3.0);
        Field.Knot second = new Field.Knot(1.0, new Field.Const(0.0), 3.0);
        ValueRange range = new Field.Curve(input, List.of(first, second)).range();
        double peak = Math.sqrt(3.0) / 6.0;
        assertTrue(range.min() <= -peak + EPSILON);
        assertTrue(range.max() >= peak - EPSILON);
    }

    @Test
    public void curveRangeExtendsBeyondEndKnots() {
        Field input = new Field.Clamp(coord(Axis.X), -2.0, 0.0);
        Field.Knot first = new Field.Knot(-1.0, new Field.Const(1.0), 2.0);
        Field.Knot second = new Field.Knot(0.0, new Field.Const(1.0), 0.0);
        ValueRange range = new Field.Curve(input, List.of(first, second)).range();
        assertEquals(-1.0, range.min(), EPSILON);
    }

    @Test
    public void sliceRemovesAxis() {
        Field slice = new Field.Slice(noise(0.1, 0.5), Axis.Y, 64);
        assertEquals(EnumSet.of(Axis.X, Axis.Z), slice.axes());
    }

    @Test
    public void topSurfaceIsColumnFieldWithinBounds() {
        Field density = new Field.Binary(Field.BinaryOp.SUB, noise(0.1, 0.0), coord(Axis.Y));
        Field top = new Field.TopSurface(density, 320, -64, 8);
        assertEquals(EnumSet.of(Axis.X, Axis.Z), top.axes());
        assertEquals(new ValueRange(-64.0, 320.0), top.range());
        assertSame(CostClass.EXPENSIVE, top.cost());
    }

    @Test
    public void rangeIfPicksBranchWhenInputIsDecided() {
        Field input = new Field.Clamp(coord(Axis.X), 0.0, 0.5);
        Field inside = new Field.RangeIf(input, 0.0, 1.0, new Field.Const(7.0), new Field.Const(9.0));
        assertEquals(new ValueRange(7.0, 7.0), inside.range());
        Field straddling = new Field.RangeIf(input, 0.25, 1.0, new Field.Const(7.0), new Field.Const(9.0));
        assertEquals(new ValueRange(7.0, 9.0), straddling.range());
    }

    @Test
    public void sharedDiamondIsAnalysedConsistently() {
        Field level = noise(0.1, 0.0);
        for (int index = 0; index < 64; index++) {
            Field shared = new Field.Shared("chain/" + index, level);
            level = new Field.Binary(Field.BinaryOp.MAX, shared, new Field.Unary(Field.UnaryOp.NEG, shared));
        }
        assertEquals(EnumSet.of(Axis.X, Axis.Z), level.axes());
        assertSame(CostClass.EXPENSIVE, level.cost());
    }

    @Test
    public void identifiersAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new Field.Ref("Minecraft:Bad"));
        assertThrows(IllegalArgumentException.class, () -> new Field.Noise("no_namespace", 1.0, 1.0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Field.Shared("bad name", new Field.Const(0.0)));
        assertThrows(IllegalArgumentException.class, () -> new Field.Grid(new Field.Const(0.0), 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new Field.Coord(Axis.X, Field.Tiling.CLAMP, 4.0, 4.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Field.Const(Double.NaN));
    }

    @Test
    public void requireTwoDimensionalRejectsYDependence() {
        Field threeDimensional = new Field.Binary(Field.BinaryOp.ADD, noise(0.1, 0.0), coord(Axis.Y));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> FieldValidator.requireTwoDimensional(threeDimensional, "height"));
        assertEquals("2D field 'height' depends on Y", error.getMessage());
        FieldValidator.requireTwoDimensional(noise(0.1, 0.0), "height");
    }

    @Test
    public void diagnosticNormalisesMissingResource() {
        ExportDiagnostic diagnostic = new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.IMAGE_MAP, null, "Image map dropped");
        assertEquals("", diagnostic.resource());
        assertThrows(NullPointerException.class, () -> new ExportDiagnostic(ExportDiagnostic.Severity.INFO, null, "x", "y"));
    }

    private static Field noise(double xzScale, double yScale) {
        return new Field.Noise("iris_export:base", xzScale, yScale, null, null, null);
    }

    private static Field coord(Axis axis) {
        return new Field.Coord(axis, Field.Tiling.CLAMP, -4064.0, 4062.0, -4064.0, 4062.0);
    }

    private static Field partition(Field input) {
        return new Field.Partition(input, List.of(0.5), List.of(new Field.Const(0.0), new Field.Const(1.0)));
    }

    private static Field.Curve curve(Field input) {
        Field.Knot first = new Field.Knot(-1.0, new Field.Const(0.0), 0.0);
        Field.Knot second = new Field.Knot(1.0, new Field.Const(1.0), 0.0);
        return new Field.Curve(input, List.of(first, second));
    }
}
