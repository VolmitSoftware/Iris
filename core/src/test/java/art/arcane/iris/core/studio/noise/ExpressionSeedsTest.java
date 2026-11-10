package art.arcane.iris.core.studio.noise;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.noise.expression.IrisExpressionLoad;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExpressionSeedsTest {
    private static final long VIEW_SEED = 777L;
    private static final long WORLD_SEED = 424242L;
    private static final int REGION_STYLE_STREAM = 883;
    private static final double EXACT = 0D;
    private static final double[][] SPOTS = {{0, 0}, {37.5, -12.25}, {-901, 4444}, {123456, -654321}};

    private IrisData data;
    private Engine engine;
    private IrisComplex complex;
    private IrisDimension dimension;
    private RNG worldRandom;

    @Before
    public void world() {
        data = mock(IrisData.class);
        engine = mock(Engine.class);
        complex = mock(IrisComplex.class);
        dimension = mock(IrisDimension.class);
        worldRandom = new RNG(WORLD_SEED).nextParallelRNG(REGION_STYLE_STREAM);
        when(engine.getData()).thenReturn(data);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(dimension);
    }

    @Test
    public void anExpressionUnderTheRegionStylePreviewsLikeTheEngineStream() {
        IrisExpression ridges = expression("ridges", "x * 0.01 + z * 0.02");
        packHas(ridges);
        worldSamples(styleOf("ridges"));

        NoiseSources sources = NoiseSources.of(engine);

        assertFalse(source(sources, "ridges").usesSeed());
        IrisGeneratorStyle engineStyle = styleOf("ridges");
        var engineNoise = engineStyle.createNoCache(worldRandom, data);
        for (double[] spot : SPOTS) {
            double preview = sources.sampler("expression:ridges", VIEW_SEED).noise(spot[0], spot[1]);
            assertEquals(engineNoise.noise(spot[0], spot[1]), preview, EXACT);
        }
    }

    @Test
    public void anExpressionBehindAVariableSharesTheRandomOfTheExpressionThatReadsIt() {
        IrisExpression warp = expression("warp", "x * 0.01 + z * 0.02");
        IrisExpression ridges = expression("ridges", "x + warp * 100");
        ridges.getVariables().add(new IrisExpressionLoad().setName("warp").setStyleValue(styleOf("warp")));
        packHas(ridges, warp);
        worldSamples(styleOf("ridges"));

        Map<String, RNG> randoms = ExpressionSeeds.of(engine);

        assertSame(worldRandom, randoms.get("ridges"));
        assertSame(worldRandom, randoms.get("warp"));
    }

    @Test
    public void anExpressionTheWorldDoesNotReachKeepsTheViewSeed() {
        IrisExpression ridges = expression("ridges", "x * 0.01");
        IrisExpression unused = expression("unused", "x * 0.02");
        packHas(ridges, unused);
        worldSamples(styleOf("ridges"));

        NoiseSources sources = NoiseSources.of(engine);

        assertTrue(source(sources, "unused").usesSeed());
        assertEquals(unused.evaluate(new RNG(VIEW_SEED), 5, 9), sources.sampler("expression:unused", VIEW_SEED).noise(5, 9), EXACT);
    }

    @Test
    public void aStyleWithoutAnExpressionReachesNothing() {
        packHas();
        worldSamples(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));

        assertTrue(ExpressionSeeds.of(engine).isEmpty());
    }

    private NoiseSources.Source source(NoiseSources sources, String key) {
        return sources.list().stream().filter(source -> source.id().equals("expression:" + key)).findFirst().orElseThrow();
    }

    private IrisGeneratorStyle styleOf(String expressionKey) {
        return new IrisGeneratorStyle().setExpression(expressionKey);
    }

    private IrisExpression expression(String key, String formula) {
        IrisExpression expression = new IrisExpression();
        expression.setExpression(formula);
        expression.setLoadKey(key);
        expression.setLoader(data);
        return expression;
    }

    @SuppressWarnings("unchecked")
    private void packHas(IrisExpression... expressions) {
        ResourceLoader<IrisExpression> loader = mock(ResourceLoader.class);
        String[] keys = new String[expressions.length];
        for (int index = 0; index < expressions.length; index++) {
            keys[index] = expressions[index].getLoadKey();
            when(loader.load(keys[index])).thenReturn(expressions[index]);
        }
        when(loader.getPossibleKeys()).thenReturn(keys);
        when(data.getExpressionLoader()).thenReturn(loader);
        when(data.getGeneratorLoader()).thenReturn(mock(ResourceLoader.class));
        when(dimension.getOverlayNoise()).thenReturn(new KList<>());
    }

    private void worldSamples(IrisGeneratorStyle regionStyle) {
        when(complex.seededNoiseStyles(dimension)).thenReturn(List.of(new IrisComplex.SeededStyle(regionStyle, worldRandom)));
    }
}
