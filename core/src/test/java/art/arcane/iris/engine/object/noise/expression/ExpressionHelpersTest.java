package art.arcane.iris.engine.object.noise.expression;

import com.dfsek.paralithic.eval.parser.Parser;
import com.dfsek.paralithic.eval.parser.Scope;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExpressionHelpersTest {
    private static double evaluate(String expression, double n) throws Exception {
        Parser parser = new Parser();
        ExpressionHelpers.registerOn(parser);
        Scope scope = new Scope();
        scope.addInvocationVariable("n");
        return parser.parse(expression, scope).evaluate(n);
    }

    @Test
    public void packExpressionsCanCallTheHelpers() {
        IrisExpression expression = new IrisExpression().setExpression("smoothstep(0, 10, x) + clamp(z, 0, 1)");

        assertEquals(0.5D + 1D, expression.evaluate(new RNG(1), 5, 0, 7), 1e-9);
    }

    @Test
    public void clampKeepsValuesInsideTheBounds() throws Exception {
        assertEquals(0.2D, evaluate("clamp(n, 0.2, 0.8)", -3D), 1e-9);
        assertEquals(0.5D, evaluate("clamp(n, 0.2, 0.8)", 0.5D), 1e-9);
        assertEquals(0.8D, evaluate("clamp(n, 0.2, 0.8)", 3D), 1e-9);
    }

    @Test
    public void linearstepRisesInAStraightLineBetweenItsEdges() throws Exception {
        assertEquals(0D, evaluate("linearstep(0.4, 0.6, n)", 0.3D), 1e-9);
        assertEquals(0.5D, evaluate("linearstep(0.4, 0.6, n)", 0.5D), 1e-9);
        assertEquals(1D, evaluate("linearstep(0.4, 0.6, n)", 0.7D), 1e-9);
    }

    @Test
    public void linearstepFallsWhenItsEdgesAreReversed() throws Exception {
        assertEquals(1D, evaluate("linearstep(0.42, 0.17, n)", 0.1D), 1e-9);
        assertEquals(0D, evaluate("linearstep(0.42, 0.17, n)", 0.5D), 1e-9);
    }

    @Test
    public void smoothstepEasesInAndOut() throws Exception {
        assertEquals(0.5D, evaluate("smoothstep(0, 1, n)", 0.5D), 1e-9);
        assertEquals(0.028D, evaluate("smoothstep(0, 1, n)", 0.1D), 1e-9);
        assertEquals(1D, evaluate("smoothstep(0, 1, n)", 2D), 1e-9);
    }

    @Test
    public void ridgePeaksAtItsCrestAndFadesAway() throws Exception {
        assertEquals(1D, evaluate("ridge(n)", 0.44D), 1e-9);
        assertEquals(Math.pow(1 - 2.2 * 0.2, 2), evaluate("ridge(n)", 0.64D), 1e-9);
        assertEquals(0D, evaluate("ridge(n)", 0.95D), 1e-9);
    }
}
