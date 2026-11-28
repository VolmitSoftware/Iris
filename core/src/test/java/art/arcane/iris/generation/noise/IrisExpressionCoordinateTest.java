package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisExpressionCoordinateTest {
    @Test
    public void planarEvaluationBindsHorizontalAxesAndAbsentHeight() {
        RNG rng = new RNG(17L);
        assertEquals(23.5D, runtimeOf(expression("x")).evaluate(rng, 23.5D, -94.75D), 0D);
        assertEquals(-94.75D, runtimeOf(expression("y")).evaluate(rng, 23.5D, -94.75D), 0D);
        assertEquals(-1D, runtimeOf(expression("z")).evaluate(rng, 23.5D, -94.75D), 0D);
    }

    @Test
    public void volumetricEvaluationBindsEachAxis() {
        RNG rng = new RNG(17L);
        assertEquals(23.5D, runtimeOf(expression("x")).evaluate(rng, 23.5D, 61D, -94.75D), 0D);
        assertEquals(61D, runtimeOf(expression("y")).evaluate(rng, 23.5D, 61D, -94.75D), 0D);
        assertEquals(-94.75D, runtimeOf(expression("z")).evaluate(rng, 23.5D, 61D, -94.75D), 0D);
    }

    @Test
    public void loadedVariablesPreserveCoordinateBindings() {
        IrisExpression expression = expression("value + x + 10 * y + 100 * z")
                .setVariables(new KList<>(new IrisExpressionLoad().setName("value").setStaticValue(7D)));
        RNG rng = new RNG(17L);
        assertEquals(-1017D, runtimeOf(expression).evaluate(rng, 23.5D, -94.75D), 0D);
        assertEquals(-8834.5D, runtimeOf(expression).evaluate(rng, 23.5D, 61D, -94.75D), 0D);
    }

    @Test
    public void expressionStreamsAndNoiseUsePlanarCoordinates() {
        IrisExpression expression = expression("x + 100 * y");
        RNG rng = new RNG(17L);
        assertEquals(-9451.5D, runtimeOf(expression).stream(rng).getDouble(23.5D, -94.75D), 0D);
        assertEquals(-9451.5D, new ExpressionNoise(rng, runtimeOf(expression)).noise(23.5D, -94.75D), 0D);
    }

    private static ExpressionRuntime runtimeOf(IrisExpression expression) {
        return PackRuntime.standalone(null).expression(expression);
    }

    private static IrisExpression expression(String source) {
        return new IrisExpression().setExpression(source);
    }
}
