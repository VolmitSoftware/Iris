package art.arcane.iris.pack.datapack.export.ir;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class FieldEvaluatorTest {
    private static final double EPSILON = 1.0e-6;
    private static final long SEED = 31_337L;
    private static final String NOISE_ID = "iris_export:evaluator/base";
    private static final NoiseDef NOISE = new NoiseDef("evaluator/base", -3, List.of(1.0, 0.5));
    private static final Map<String, NoiseDef> NOISES = Map.of(NOISE_ID, NOISE);
    private static final Field X = new Field.Coord(Axis.X, Field.Tiling.CLAMP, -4064, 4064, -4064, 4064);
    private static final Field Y = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, -4064, 4064, -4064, 4064);
    private static final Field Z = new Field.Coord(Axis.Z, Field.Tiling.CLAMP, -4064, 4064, -4064, 4064);

    @Test
    public void constReturnsItsValue() {
        assertEquals(2.5, sample(new Field.Const(2.5), 0, 0, 0), 0.0);
    }

    @Test
    public void evaluatesInSinglePrecision() {
        Field tenth = new Field.Const(0.1);
        assertEquals((double) 0.1F, sample(tenth, 0, 0, 0), 0.0);
    }

    @Test
    public void unsupportedTargetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> FieldEvaluator.sample(new Field.Const(1.0), NOISES, SEED, DataVersion.V26_1_2, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> FieldEvaluator.sample(new Field.Const(1.0), NOISES, SEED, DataVersion.V26_2, 0, 0, 0));
    }

    @Test
    public void coordClampTilingClampsOutsideRange() {
        Field gradient = new Field.Coord(Axis.X, Field.Tiling.CLAMP, 0, 64, 0, 1);
        assertEquals(0.0, sample(gradient, -5, 0, 0), 0.0);
        assertEquals(0.5, sample(gradient, 32, 0, 0), 0.0);
        assertEquals(1.0, sample(gradient, 100, 0, 0), 0.0);
    }

    @Test
    public void coordRepeatTilingWrapsWithFloorMod() {
        Field gradient = new Field.Coord(Axis.Z, Field.Tiling.REPEAT, 0, 64, 0, 64);
        assertEquals(63.0, sample(gradient, 0, 0, -1), 0.0);
        assertEquals(2.0, sample(gradient, 0, 0, 130), 0.0);
    }

    @Test
    public void coordMirroredRepeatTilingReflectsOddPeriods() {
        Field gradient = new Field.Coord(Axis.X, Field.Tiling.MIRRORED_REPEAT, 0, 64, 0, 64);
        assertEquals(10.0, sample(gradient, 10, 0, 0), 0.0);
        assertEquals(58.0, sample(gradient, 70, 0, 0), 0.0);
        assertEquals(1.0, sample(gradient, -1, 0, 0), 0.0);
    }

    @Test
    public void coordFollowsItsAxis() {
        assertEquals(-12.0, sample(Y, 3, -12, 7), 0.0);
        assertEquals(7.0, sample(Z, 3, -12, 7), 0.0);
    }

    @Test
    public void coordWithFractionalEndpointThrows() {
        Field gradient = new Field.Coord(Axis.X, Field.Tiling.CLAMP, 0.5, 64, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> sample(gradient, 0, 0, 0));
    }

    @Test
    public void noiseSamplesTheVanillaModelAtScaledCoordinates() {
        Field noise = new Field.Noise(NOISE_ID, 0.25, 0.5, null, null, null);
        VanillaNoiseModel model = VanillaNoiseModel.create(NOISE_ID, NOISE.firstOctave(), NOISE.amplitudes(), SEED, DataVersion.V26_3);
        double expected = model.sample(13 * 0.25, -7 * 0.5, 91 * 0.25);
        assertEquals((float) expected, sample(noise, 13, -7, 91), 0.0);
    }

    @Test
    public void noiseShiftsAddAfterScaling() {
        Field noise = new Field.Noise(NOISE_ID, 0.25, 0.0, new Field.Const(10.0), new Field.Const(3.0), X);
        VanillaNoiseModel model = VanillaNoiseModel.create(NOISE_ID, NOISE.firstOctave(), NOISE.amplitudes(), SEED, DataVersion.V26_3);
        double expected = model.sample(5 * 0.25 + 10.0, 3.0, 9 * 0.25 + 5.0);
        assertEquals((float) expected, sample(noise, 5, 40, 9), 0.0);
    }

    @Test
    public void undefinedNoiseThrows() {
        Field noise = new Field.Noise("iris_export:missing", 1.0, 0.0, null, null, null);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> sample(noise, 0, 0, 0));
        assertTrue(failure.getMessage().contains("iris_export:missing"));
    }

    @Test
    public void binaryOperatorsFollowVanilla() {
        Field three = new Field.Const(3.0);
        Field two = new Field.Const(2.0);
        assertEquals(5.0, sample(binary(Field.BinaryOp.ADD, three, two), 0, 0, 0), 0.0);
        assertEquals(1.0, sample(binary(Field.BinaryOp.SUB, three, two), 0, 0, 0), 0.0);
        assertEquals(6.0, sample(binary(Field.BinaryOp.MUL, three, two), 0, 0, 0), 0.0);
        assertEquals(1.5, sample(binary(Field.BinaryOp.DIV, three, two), 0, 0, 0), 0.0);
        assertEquals(2.0, sample(binary(Field.BinaryOp.MIN, three, two), 0, 0, 0), 0.0);
        assertEquals(3.0, sample(binary(Field.BinaryOp.MAX, three, two), 0, 0, 0), 0.0);
        assertEquals(9.0, sample(binary(Field.BinaryOp.POW, three, two), 0, 0, 0), 0.0);
    }

    @Test
    public void divisionWithZeroNumeratorIsZeroWithoutEvaluatingTheDenominator() {
        Field division = binary(Field.BinaryOp.DIV, new Field.Const(0.0), missingNoise());
        assertEquals(0.0, sample(division, 0, 0, 0), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, sample(binary(Field.BinaryOp.DIV, new Field.Const(1.0), new Field.Const(0.0)), 0, 0, 0), 0.0);
    }

    @Test
    public void unaryOperatorsFollowVanilla() {
        assertEquals(-2.0, unary(Field.UnaryOp.NEG, 2.0), 0.0);
        assertEquals(2.0, unary(Field.UnaryOp.ABS, -2.0), 0.0);
        assertEquals(4.0, unary(Field.UnaryOp.SQUARE, -2.0), 0.0);
        assertEquals(-8.0, unary(Field.UnaryOp.CUBE, -2.0), 0.0);
        assertEquals(3.0, unary(Field.UnaryOp.SQRT, 9.0), 0.0);
        assertEquals((float) Math.log(5.0), unary(Field.UnaryOp.LOG, 5.0), 0.0);
        assertEquals(-1.0, unary(Field.UnaryOp.SIGN, -0.25), 0.0);
        assertEquals(0.0, unary(Field.UnaryOp.SIGN, 0.0), 0.0);
        assertEquals(-1.0, unary(Field.UnaryOp.HALF_NEG, -2.0), 0.0);
        assertEquals(2.0, unary(Field.UnaryOp.HALF_NEG, 2.0), 0.0);
        assertEquals(-0.5, unary(Field.UnaryOp.QUARTER_NEG, -2.0), 0.0);
        assertEquals(2.0, unary(Field.UnaryOp.QUARTER_NEG, 2.0), 0.0);
    }

    @Test
    public void squeezeClampsThenAppliesTheCubicDefinition() {
        double inside = 0.5;
        assertEquals(inside / 2.0 - inside * inside * inside / 24.0, unary(Field.UnaryOp.SQUEEZE, inside), 1.0e-6);
        assertEquals(0.5 - 1.0 / 24.0, unary(Field.UnaryOp.SQUEEZE, 4.0), 1.0e-6);
        assertEquals(-0.5 + 1.0 / 24.0, unary(Field.UnaryOp.SQUEEZE, -4.0), 1.0e-6);
    }

    @Test
    public void clampBoundsTheInput() {
        Field clamp = new Field.Clamp(X, -2.0, 3.0);
        assertEquals(-2.0, sample(clamp, -10, 0, 0), 0.0);
        assertEquals(1.0, sample(clamp, 1, 0, 0), 0.0);
        assertEquals(3.0, sample(clamp, 10, 0, 0), 0.0);
    }

    @Test
    public void lerpBlendsAndSkipsTheUnusedEndpoint() {
        assertEquals(2.5, sample(new Field.Lerp(new Field.Const(0.25), new Field.Const(2.0), new Field.Const(4.0)), 0, 0, 0), 0.0);
        assertEquals(2.0, sample(new Field.Lerp(new Field.Const(0.0), new Field.Const(2.0), missingNoise()), 0, 0, 0), 0.0);
        assertEquals(4.0, sample(new Field.Lerp(new Field.Const(1.0), missingNoise(), new Field.Const(4.0)), 0, 0, 0), 0.0);
    }

    @Test
    public void roundModesQuantiseToTheMultiple() {
        Field four = new Field.Const(4.0);
        assertEquals(12.0, sample(new Field.Round(Field.RoundMode.ROUND, new Field.Const(10.0), four), 0, 0, 0), 0.0);
        assertEquals(8.0, sample(new Field.Round(Field.RoundMode.FLOOR, new Field.Const(10.0), four), 0, 0, 0), 0.0);
        assertEquals(12.0, sample(new Field.Round(Field.RoundMode.CEIL, new Field.Const(9.0), four), 0, 0, 0), 0.0);
        assertEquals(-8.0, sample(new Field.Round(Field.RoundMode.TRUNCATE, new Field.Const(-10.0), four), 0, 0, 0), 0.0);
        assertEquals(8.0, sample(new Field.Round(Field.RoundMode.TRUNCATE, new Field.Const(10.0), four), 0, 0, 0), 0.0);
    }

    @Test
    public void roundHalvesGoUpLikeMathRound() {
        Field one = new Field.Const(1.0);
        assertEquals(3.0, sample(new Field.Round(Field.RoundMode.ROUND, new Field.Const(2.5), one), 0, 0, 0), 0.0);
        assertEquals(-2.0, sample(new Field.Round(Field.RoundMode.ROUND, new Field.Const(-2.5), one), 0, 0, 0), 0.0);
    }

    @Test
    public void roundWithZeroMultipleReturnsTheInput() {
        assertEquals(2.75, sample(new Field.Round(Field.RoundMode.FLOOR, new Field.Const(2.75), new Field.Const(0.0)), 0, 0, 0), 0.0);
    }

    @Test
    public void curveIsCubicHermiteBetweenKnots() {
        Field curve = new Field.Curve(X, List.of(knot(0.0, 0.0, 2.0), knot(2.0, 1.0, 0.0)));
        float t = 0.5F;
        float hermite = lerp(t, 0.0F, 1.0F) + t * (1.0F - t) * lerp(t, 2.0F * 2.0F - 1.0F, 1.0F);
        assertEquals(hermite, sample(curve, 1, 0, 0), 0.0);
        assertEquals(0.0, sample(curve, 0, 0, 0), 0.0);
        assertEquals(1.0, sample(curve, 2, 0, 0), 0.0);
    }

    @Test
    public void curveExtendsLinearlyFromTheEndDerivatives() {
        Field curve = new Field.Curve(X, List.of(knot(0.0, 0.0, 2.0), knot(2.0, 1.0, 0.0)));
        assertEquals(-6.0, sample(curve, -3, 0, 0), 0.0);
        assertEquals(1.0, sample(curve, 9, 0, 0), 0.0);
    }

    @Test
    public void curveWithOneKnotIsLinear() {
        Field curve = new Field.Curve(X, List.of(knot(1.0, 5.0, 0.5)));
        assertEquals(6.0, sample(curve, 3, 0, 0), 0.0);
        assertEquals(4.0, sample(curve, -1, 0, 0), 0.0);
    }

    @Test
    public void curveEvaluatesInSinglePrecision() {
        Field curve = new Field.Curve(X, List.of(knot(0.0, 0.0, 0.0), knot(3.0, 0.1, 0.0)));
        double value = sample(curve, 3, 0, 0);
        assertEquals((double) 0.1F, value, 0.0);
    }

    @Test
    public void nestedCurveKnotsEvaluateOverTheirOwnInput() {
        Field inner = new Field.Curve(Z, List.of(knot(0.0, 0.0, 1.0), knot(10.0, 10.0, 1.0)));
        Field outer = new Field.Curve(X, List.of(
                new Field.Knot(0.0, new Field.Const(0.0), 0.0),
                new Field.Knot(1.0, inner, 0.0)));
        assertEquals(4.0, sample(outer, 1, 0, 4), 0.0);
        assertEquals(4.0, sample(outer, 7, 0, 4), 0.0);
    }

    @Test
    public void partitionSelectsLikeIntervalSelect() {
        Field partition = new Field.Partition(X, List.of(0.0, 1.0), List.of(new Field.Const(10.0), new Field.Const(20.0), new Field.Const(30.0)));
        assertEquals(10.0, sample(partition, -1, 0, 0), 0.0);
        assertEquals(20.0, sample(partition, 0, 0, 0), 0.0);
        assertEquals(30.0, sample(partition, 1, 0, 0), 0.0);
        assertEquals(30.0, sample(partition, 5, 0, 0), 0.0);
    }

    @Test
    public void rangeIfIncludesMinimumAndExcludesMaximum() {
        Field choice = new Field.RangeIf(X, 0.0, 2.0, new Field.Const(1.0), new Field.Const(-1.0));
        assertEquals(-1.0, sample(choice, -1, 0, 0), 0.0);
        assertEquals(1.0, sample(choice, 0, 0, 0), 0.0);
        assertEquals(1.0, sample(choice, 1, 0, 0), 0.0);
        assertEquals(-1.0, sample(choice, 2, 0, 0), 0.0);
    }

    @Test
    public void rangeIfEvaluatesOnlyTheChosenBranch() {
        Field choice = new Field.RangeIf(X, 0.0, 2.0, new Field.Const(1.0), missingNoise());
        assertEquals(1.0, sample(choice, 1, 0, 0), 0.0);
    }

    @Test
    public void muxSelectsTheCaseAtTheNearestIndex() {
        Field mux = new Field.Mux(X, List.of(new Field.Const(10.0), new Field.Const(20.0), missingNoise()));
        assertEquals(10.0, sample(mux, 0, 0, 0), 0.0);
        assertEquals(20.0, sample(mux, 1, 0, 0), 0.0);
        assertEquals(10.0, sample(mux, -3, 0, 0), 0.0);
    }

    @Test
    public void gatePassesTheExpressionOnlyForPositiveConditions() {
        Field gate = new Field.Gate(X, new Field.Const(7.0));
        assertEquals(7.0, sample(gate, 1, 0, 0), 0.0);
        assertEquals(0.0, sample(gate, 0, 0, 0), 0.0);
        assertEquals(0.0, sample(new Field.Gate(new Field.Const(0.0), missingNoise()), 0, 0, 0), 0.0);
    }

    @Test
    public void gridInterpolatesBetweenAbsoluteLatticeCorners() {
        Field squared = new Field.Unary(Field.UnaryOp.SQUARE, X);
        Field grid = new Field.Grid(squared, 4, 8);
        assertEquals(8.0, sample(grid, 2, 0, 0), 0.0);
        assertEquals(16.0, sample(grid, 4, 0, 0), 0.0);
        assertEquals(8.0, sample(grid, -2, 0, 0), 0.0);
        assertEquals(16.0 + 0.25 * (64.0 - 16.0), sample(grid, 5, 3, 0), 0.0);
    }

    @Test
    public void gridInterpolatesAlongY() {
        Field grid = new Field.Grid(new Field.Unary(Field.UnaryOp.SQUARE, Y), 4, 8);
        assertEquals(32.0, sample(grid, 1, 4, 1), 0.0);
        assertEquals(64.0, sample(grid, 1, -8, 1), 0.0);
    }

    @Test
    public void gridMatchesTrilinearInterpolationOfNoise() {
        Field noise = new Field.Noise(NOISE_ID, 0.5, 0.25, null, null, null);
        Field grid = new Field.Grid(noise, 4, 8);
        double expected = trilinear(noise, 5, 13, -6, 4, 8);
        assertEquals(expected, sample(grid, 5, 13, -6), 1.0e-6);
    }

    @Test
    public void sharedIsTransparent() {
        Field noise = new Field.Noise(NOISE_ID, 0.5, 0.0, null, null, null);
        Field shared = new Field.Shared("evaluator/shared", noise);
        Field doubled = binary(Field.BinaryOp.ADD, shared, shared);
        assertEquals(sample(noise, 3, 0, 9), sample(shared, 3, 0, 9), 0.0);
        assertEquals((float) (2.0 * sample(noise, 3, 0, 9)), sample(doubled, 3, 0, 9), 0.0);
    }

    @Test
    public void slicePinsTheAxis() {
        Field slice = new Field.Slice(binary(Field.BinaryOp.ADD, X, Y), Axis.Y, 10);
        assertEquals(13.0, sample(slice, 3, 50, 0), 0.0);
    }

    @Test
    public void topSurfaceScansDownFromTheUpperBound() {
        Field density = binary(Field.BinaryOp.SUB, new Field.Const(64.0), Y);
        assertEquals(60.0, sample(new Field.TopSurface(density, 100, 0, 4), 0, 0, 0), 0.0);
        assertEquals(70.0, sample(new Field.TopSurface(new Field.Const(-1.0), 103, 70, 4), 0, 0, 0), 0.0);
        assertEquals(30.0, sample(new Field.TopSurface(density, 30, 30, 4), 0, 0, 0), 0.0);
    }

    @Test
    public void refIsUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> sample(new Field.Ref("minecraft:overworld/continents"), 0, 0, 0));
    }

    @Test
    public void differentSeedsSampleDifferentNoise() {
        Field noise = new Field.Noise(NOISE_ID, 0.37, 0.0, null, null, null);
        double first = FieldEvaluator.sample(noise, NOISES, SEED, DataVersion.V26_3, 11, 0, 17);
        double second = FieldEvaluator.sample(noise, NOISES, SEED + 1L, DataVersion.V26_3, 11, 0, 17);
        assertNotEquals(first, second, EPSILON);
    }

    private static double trilinear(Field field, int x, int y, int z, int cellXz, int cellY) {
        int baseX = Math.floorDiv(x, cellXz) * cellXz;
        int baseY = Math.floorDiv(y, cellY) * cellY;
        int baseZ = Math.floorDiv(z, cellXz) * cellXz;
        double fx = (x - baseX) / (double) cellXz;
        double fy = (y - baseY) / (double) cellY;
        double fz = (z - baseZ) / (double) cellXz;
        double[] corners = new double[8];
        for (int index = 0; index < 8; index++) {
            int dx = index & 1;
            int dy = index >> 1 & 1;
            int dz = index >> 2 & 1;
            corners[index] = sample(field, baseX + dx * cellXz, baseY + dy * cellY, baseZ + dz * cellXz);
        }
        double bottom = lerpDouble(fy, lerpDouble(fx, corners[0], corners[1]), lerpDouble(fx, corners[2], corners[3]));
        double top = lerpDouble(fy, lerpDouble(fx, corners[4], corners[5]), lerpDouble(fx, corners[6], corners[7]));
        return lerpDouble(fz, bottom, top);
    }

    private static double lerpDouble(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static Field.Knot knot(double location, double value, double derivative) {
        return new Field.Knot(location, new Field.Const(value), derivative);
    }

    private static Field binary(Field.BinaryOp op, Field left, Field right) {
        return new Field.Binary(op, left, right);
    }

    private static double unary(Field.UnaryOp op, double input) {
        return sample(new Field.Unary(op, new Field.Const(input)), 0, 0, 0);
    }

    private static Field missingNoise() {
        return new Field.Noise("iris_export:missing", 1.0, 0.0, null, null, null);
    }

    private static double sample(Field field, int x, int y, int z) {
        return FieldEvaluator.sample(field, NOISES, SEED, DataVersion.V26_3, x, y, z);
    }

}
