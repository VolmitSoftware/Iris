package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class IrisNoiseGeneratorFractureTest {
    @Test
    public void deepChainsMatchRecursiveSamplingBits() {
        Random coordinates = new Random(718311L);
        for (NoiseStyle style : List.of(NoiseStyle.SIMPLEX, NoiseStyle.PERLIN, NoiseStyle.IRIS,
                NoiseStyle.FRACTAL_BILLOW_SIMPLEX, NoiseStyle.FRACTAL_FBM_SIMPLEX)) {
            for (int depth : new int[]{0, 1, 3, 4, 5, 8}) {
                IrisNoiseGenerator root = chain(depth, style).getFirst();
                for (long seed : new long[]{0L, -4193L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                    assertParity(root, seed, 0D, -0D, null, null);
                    assertParity(root, seed, -0D, 0D, null, null);
                    assertParity(root, seed, -30_000_000D, 29_999_999.75D, null, null);
                    assertParity(root, seed, Math.nextDown(-64D), Math.nextUp(64D), null, null);
                    for (int index = 0; index < 8; index++) {
                        assertParity(root, seed, coordinates.nextDouble() * 4096D - 2048D,
                                coordinates.nextDouble() * 4096D - 2048D, null, null);
                    }
                }
            }
        }
    }

    @Test
    public void fourFracturesReducePrimitiveSamplesFromThirtyOneToNine() {
        List<IrisNoiseGenerator> chain = chain(4, NoiseStyle.SIMPLEX);
        List<NoiseGenerator> bases = spyBases(chain, 71L);
        reference(chain.getFirst(), 71L, 13.25D, -21.75D, null, null);
        for (int index = 0; index < bases.size(); index++) {
            verify(bases.get(index), times(1 << index)).noise(anyDouble(), anyDouble());
            clearInvocations(bases.get(index));
        }

        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        for (int index = 0; index < bases.size(); index++) {
            verify(bases.get(index), times(index == 0 ? 1 : 2)).noise(anyDouble(), anyDouble());
        }
    }

    @Test
    public void shallowChainsKeepTheirSamplingCounts() {
        List<IrisNoiseGenerator> chain = chain(3, NoiseStyle.SIMPLEX);
        List<NoiseGenerator> bases = spyBases(chain, 71L);
        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        for (int index = 0; index < bases.size(); index++) {
            verify(bases.get(index), times(1 << index)).noise(anyDouble(), anyDouble());
        }
    }

    @Test
    public void changedParametersAndFracturesRemainVisible() {
        List<IrisNoiseGenerator> chain = chain(5, NoiseStyle.IRIS);
        IrisNoiseGenerator root = chain.getFirst();
        assertParity(root, 19L, 31D, -79D, null, null);
        chain.get(3).setOffsetX(-319D).setOffsetZ(17D).setOffsetY(-0.7D)
                .setSeed(Long.MAX_VALUE).setOpacity(0.35D).setZoom(17D);
        assertParity(root, 19L, 31D, -79D, null, null);
        chain.get(4).setEnabled(false);
        assertParity(root, 19L, 31D, -79D, null, null);
        chain.get(4).setEnabled(true);
        chain.get(2).getFracture().add(new IrisNoiseGenerator().setStyle(NoiseStyle.PERLIN.style()));
        assertParity(root, 19L, 31D, -79D, null, null);
        chain.get(2).getFracture().removeLast();
        chain.getLast().getFracture().add(new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()));
        assertParity(root, 19L, 31D, -79D, null, null);
    }

    @Test
    public void statefulRuntimeGeneratorsKeepAllEvaluations() {
        List<IrisNoiseGenerator> chain = chain(4, NoiseStyle.SIMPLEX);
        long leafSeed = childSeed(chain, 23L, chain.size() - 1);
        StatefulNoise stateful = new StatefulNoise();
        chain.getLast().getGenerator(leafSeed, null, null).setGenerator(stateful);
        double expected = reference(chain.getFirst(), 23L, 17D, -31D, null, null);
        assertEquals(16, stateful.samples);
        stateful.samples = 0;
        double actual = chain.getFirst().getNoise(23L, 17D, -31D, null);
        assertBits(expected, actual);
        assertEquals(16, stateful.samples);
    }

    @Test
    public void expressionConfigurationDoesNotReuseEvenPreviouslyCachedPureNoise() {
        List<IrisNoiseGenerator> chain = chain(4, NoiseStyle.SIMPLEX);
        List<NoiseGenerator> bases = spyBases(chain, 71L);
        chain.getLast().getStyle().setExpression("custom");
        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        verify(bases.getLast(), times(16)).noise(anyDouble(), anyDouble());
        chain.getLast().getStyle().setExpression(null).setImageMap("map");
        clearInvocations(bases.getLast());
        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        verify(bases.getLast(), times(16)).noise(anyDouble(), anyDouble());
    }

    @Test
    public void customStylesKeepTheScalarPath() {
        List<IrisNoiseGenerator> chain = chain(4, NoiseStyle.SIMPLEX);
        List<NoiseGenerator> bases = spyBases(chain, 71L);
        chain.getLast().setStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX) {
        });
        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        verify(bases.getLast(), times(16)).noise(anyDouble(), anyDouble());
    }

    @Test
    public void failedSamplingReleasesTheThreadScratch() {
        List<IrisNoiseGenerator> chain = chain(4, NoiseStyle.SIMPLEX);
        List<NoiseGenerator> bases = spyBases(chain, 71L);
        doThrow(new IllegalStateException("sample failed")).doCallRealMethod()
                .when(bases.getLast()).noise(anyDouble(), anyDouble());
        assertThrows(IllegalStateException.class,
                () -> chain.getFirst().getNoise(71L, 13.25D, -21.75D, null));
        clearInvocations(bases.getLast());
        chain.getFirst().getNoise(71L, 13.25D, -21.75D, null);
        verify(bases.getLast(), times(2)).noise(anyDouble(), anyDouble());
    }

    @Test
    public void threadsAndEngineIdentitiesRemainIndependent() throws Exception {
        IrisNoiseGenerator root = chain(5, NoiseStyle.IRIS).getFirst();
        IrisData data = mock(IrisData.class);
        Engine firstEngine = mock(Engine.class);
        Engine secondEngine = mock(Engine.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> sampleConcurrently(root, data, firstEngine, 47L, start));
            Future<?> second = executor.submit(() -> sampleConcurrently(root, data, secondEngine, -131L, start));
            start.countDown();
            first.get(15L, TimeUnit.SECONDS);
            second.get(15L, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertParity(root, 47L, 17D, 19D, data, secondEngine);
        assertParity(root, 47L, 17D, 19D, data, firstEngine);
    }

    private static void sampleConcurrently(IrisNoiseGenerator root, IrisData data, Engine engine,
                                           long seed, CountDownLatch start) {
        try {
            assertTrue(start.await(5L, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
        for (int index = 0; index < 64; index++) {
            assertParity(root, seed, index * 13.75D, -index * 2.25D, data, engine);
        }
    }

    private static List<IrisNoiseGenerator> chain(int depth, NoiseStyle style) {
        List<IrisNoiseGenerator> nodes = new ArrayList<>(depth + 1);
        for (int index = 0; index <= depth; index++) {
            IrisNoiseGenerator node = new IrisNoiseGenerator().setStyle(style.style())
                    .setSeed(index * 713L).setZoom(0.75D + index).setOpacity(0.63D + index * 0.01D)
                    .setOffsetX(index * -0.2D).setOffsetY(index * -0.03D).setOffsetZ(index * 0.4D)
                    .setNegative(index % 2 == 0).setExponent(index % 3 == 0 ? 1.25D : 1D)
                    .setParametric(index % 3 == 1).setBezier(index % 3 == 2)
                    .setSinCentered(index % 4 == 1);
            if (!nodes.isEmpty()) {
                nodes.getLast().setFracture(new KList<>(node));
            }
            nodes.add(node);
        }
        return nodes;
    }

    private static List<NoiseGenerator> spyBases(List<IrisNoiseGenerator> nodes, long superSeed) {
        List<NoiseGenerator> bases = new ArrayList<>(nodes.size());
        for (IrisNoiseGenerator node : nodes) {
            CNG generator = node.getGenerator(superSeed, null, null);
            NoiseGenerator base = spy(generator.getGenerator());
            generator.setGenerator(base);
            bases.add(base);
            superSeed += node.getSeed() + 33;
        }
        return bases;
    }

    private static long childSeed(List<IrisNoiseGenerator> nodes, long seed, int index) {
        for (int child = 0; child < index; child++) {
            seed += nodes.get(child).getSeed() + 33;
        }
        return seed;
    }

    private static void assertParity(IrisNoiseGenerator node, long seed, double x, double z,
                                     IrisData data, Engine engine) {
        assertBits(reference(node, seed, x, z, data, engine), node.getNoise(seed, x, z, data, engine));
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static double reference(IrisNoiseGenerator node, long superSeed, double xv, double zv,
                                    IrisData data, Engine engine) {
        if (!node.isEnabled()) {
            return node.getOffsetY();
        }
        double x = xv;
        double z = zv;
        int salt = 33;
        for (IrisNoiseGenerator child : node.getFracture()) {
            if (child.isEnabled()) {
                double offset = child.getOpacity() / 2D;
                x += reference(child, superSeed + node.getSeed() + salt, xv, zv, data, engine) - offset;
                z += reference(child, superSeed + node.getSeed() + salt, zv, xv, data, engine) - offset;
            }
            salt += 819;
        }
        double sampleX = (x / node.getZoom()) + node.getOffsetX();
        double sampleZ = (z / node.getZoom()) + node.getOffsetZ();
        double result = node.getGenerator(superSeed, data, engine).noiseFast2D(sampleX, sampleZ) * node.getOpacity();
        result = node.isNegative() ? -result + node.getOpacity() : result;
        result = (node.getExponent() != 1D ? result < 0D ? -Math.pow(-result, node.getExponent())
                : Math.pow(result, node.getExponent()) : result) + node.getOffsetY();
        result = node.isParametric() ? IrisInterpolation.parametric(result, 2D) : result;
        result = node.isBezier() ? IrisInterpolation.bezier(result) : result;
        return node.isSinCentered() ? IrisInterpolation.sinCenter(result) : result;
    }

    private static final class StatefulNoise implements NoiseGenerator {
        private int samples;

        @Override
        public double noise(double x) {
            return ++samples * 0.01D;
        }

        @Override
        public double noise(double x, double z) {
            return ++samples * 0.01D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return ++samples * 0.01D;
        }
    }
}
