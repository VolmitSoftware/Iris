package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCarveModifierCaveInputsTest {
    @Test
    public void capturedColumnsMatchScalarAcrossDepthStackAndCoordinateCollisions() {
        try (Fixture fixture = new Fixture()) {
            DimensionStackContext stack = mock(DimensionStackContext.class);
            when(fixture.engine.getDimensionStackContext()).thenReturn(stack);
            when(fixture.scalar.getDimensionStackContext()).thenReturn(stack);
            when(stack.getLayout(anyInt(), anyInt())).thenAnswer(call -> {
                DimensionStackLayout layout = mock(DimensionStackLayout.class);
                DimensionStackLayout.Layer layer = mock(DimensionStackLayout.Layer.class);
                when(layer.biome()).thenReturn((int) call.getArgument(0) % 2 == 0 ? fixture.stacked : null);
                when(layout.surfaceLayer()).thenReturn(layer);
                return layout;
            });
            IrisCarveModifier.CaveInputs inputs = new IrisCarveModifier.CaveInputs(fixture.engine);
            IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
            for (int x : new int[]{-35, -32, -17, -16, 0, 16, 1024, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                for (int z : new int[]{-35, -16, 0, 3, 1024, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                    for (int y : new int[]{0, 30, 32, 33, 34, 39, 40, 41}) {
                        assertSame(fixture.scalar.getCaveBiome(x, y, z, state), inputs.resolve(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    public void repeatedHeightsReuseCapturedColumnAndIgnoreAmbientRuntimeReplacement() {
        try (Fixture fixture = new Fixture()) {
            IrisCarveModifier.CaveInputs inputs = new IrisCarveModifier.CaveInputs(fixture.engine);
            IrisComplex replacement = mock(IrisComplex.class);
            when(fixture.engine.getComplex()).thenReturn(replacement);
            when(fixture.engine.getDimension()).thenReturn(new IrisDimension());
            when(fixture.engine.getData()).thenReturn(mock(IrisData.class));
            for (int y = 0; y < 40; y++) {
                assertSame(y <= 33 ? fixture.cave : fixture.surface, inputs.resolve(-31, y, 6));
            }
            assertEquals(1, fixture.samples.get());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void configuredWorldHeightRangeRetainsCapturedPackAndPrecedesFallback() {
        try (Fixture fixture = new Fixture()) {
            IrisBiome configured = biome("configured", 0);
            ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
            when(fixture.data.getBiomeLoader()).thenReturn(loader);
            when(loader.load("configured")).thenReturn(configured);
            IrisDimensionCarvingEntry root = new IrisDimensionCarvingEntry();
            root.setBiome("configured");
            root.setChildRecursionDepth(0);
            root.setWorldYRange(new IrisRange(-64, -48));
            fixture.dimension.setCarving(new KList<>(List.of(root)));
            IrisCarveModifier.CaveInputs inputs = new IrisCarveModifier.CaveInputs(fixture.engine);
            when(fixture.engine.getDimension()).thenReturn(new IrisDimension());
            when(fixture.engine.getData()).thenReturn(mock(IrisData.class));
            assertSame(configured, inputs.resolve(-31, 0, 6));
            assertSame(configured, inputs.resolve(-31, 16, 6));
            assertEquals(0, fixture.samples.get());
            assertSame(fixture.cave, inputs.resolve(-31, 17, 6));
        }
    }

    @Test
    public void genericAndMainThreadEnginesRetainCustomBiomeDispatch() {
        IrisBiome custom = biome("custom", 0);
        Engine generic = mock(Engine.class);
        when(generic.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class))).thenReturn(custom);
        assertSame(custom, new IrisCarveModifier.CaveInputs(generic).resolve(1, 2, 3));
        try (Fixture fixture = new Fixture()) {
            when(fixture.hooks.isMainThread()).thenReturn(true);
            when(fixture.engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class))).thenReturn(custom);
            assertSame(custom, new IrisCarveModifier.CaveInputs(fixture.engine).resolve(1, 2, 3));
            assertEquals(0, fixture.samples.get());
        }
    }

    @Test
    public void contentStageKeepsCoordinateHistoryDispatch() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.context.isNaturalTerrain()).thenReturn(false);
            IrisBiome historical = biome("historical", 0);
            when(fixture.engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class))).thenReturn(historical);
            assertSame(historical, new IrisCarveModifier.CaveInputs(fixture.engine).resolve(-17, 20, 16));
            assertEquals(0, fixture.samples.get());
        }
    }

    private static IrisBiome biome(String key, int depth) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        biome.setCaveMinDepthBelowSurface(depth);
        return biome;
    }

    private static final class Fixture implements AutoCloseable {
        private final IrisContext.Scope scope;
        private final ChunkContext context = mock(ChunkContext.class);
        private final IrisEngine engine = mock(IrisEngine.class);
        private final Engine scalar = mock(Engine.class, CALLS_REAL_METHODS);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisDimension dimension = new IrisDimension();
        private final IrisData data = mock(IrisData.class);
        private final EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        private final IrisBiome surface = biome("surface", 3);
        private final IrisBiome cave = biome("cave", 7);
        private final IrisBiome unkeyed = biome(null, 2);
        private final IrisBiome stacked = biome("stacked", 5);
        private final AtomicInteger samples = new AtomicInteger();

        private Fixture() {
            when(context.getComplex()).thenReturn(complex);
            when(context.isNaturalTerrain()).thenReturn(true);
            scope = IrisContext.open(engine, 1L, context);
            when(engine.hasGenerationRuntimeScope()).thenReturn(true);
            for (Engine target : new Engine[]{engine, scalar}) {
                doReturn(complex).when(target).getComplex();
                doReturn(dimension).when(target).getDimension();
                doReturn(data).when(target).getData();
                doReturn(hooks).when(target).getPlatformHooks();
                doReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build()).when(target).getWorld();
            }
            when(complex.getTrueBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> surface, Interpolated.of(value -> 0D, value -> null)));
            when(complex.getCaveBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> switch (Math.floorMod(x.intValue(), 3)) {
                case 0 -> null;
                case 1 -> unkeyed;
                default -> cave;
            }, Interpolated.of(value -> 0D, value -> null)));
            when(complex.getHeightStream()).thenReturn(ProceduralStream.of((x, z) -> {
                samples.incrementAndGet();
                return 40D;
            }, Interpolated.DOUBLE));
        }

        @Override
        public void close() {
            scope.close();
        }

    }
}
