package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationClosedException;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class DecorationFailureContainmentTest {
    private static final int FAILING_X = 3;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        NativeBlockState air = mock(NativeBlockState.class);
        doReturn(true).when(air).isAir();
        doReturn("minecraft:air").when(air).key();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        doReturn(air).when(registries).block(anyString());
        doReturn(air).when(registries).air();
        IrisPlatform platform = mock(IrisPlatform.class);
        doReturn(registries).when(platform).registries();
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void badBlockFaceSkipsOnlyThatColumnAndTheRestOfTheChunkDecorates() {
        Hunk<NativeBlockState> first = decorateRow(x -> new IllegalArgumentException("Invalid blockFace"), false);
        Hunk<NativeBlockState> second = decorateRow(x -> new IllegalArgumentException("Invalid blockFace"), true);

        for (int x = 0; x < 16; x++) {
            assertEquals(key(first.get(x, 9, 0)), key(second.get(x, 9, 0)));
            if (x == FAILING_X) {
                assertNull(first.get(x, 9, 0));
            } else {
                assertEquals("minecraft:vine", first.get(x, 9, 0).key());
            }
        }
    }

    @Test
    public void engineFailureStillEscapesTheDecorator() {
        assertThrows(GenerationClosedException.class,
                () -> decorateRow(x -> new GenerationClosedException("router closed"), false));
    }

    private static String key(NativeBlockState state) {
        return state == null ? null : state.key();
    }

    private static Hunk<NativeBlockState> decorateRow(Function<Integer, RuntimeException> failure, boolean reverse) {
        NativeBlockState vine = mock(NativeBlockState.class);
        doReturn(true).when(vine).isVineBlock();
        doReturn("minecraft:vine").when(vine).key();
        doReturn("minecraft:vine").when(vine).materialKey();

        IrisDecorator decorator = mock(IrisDecorator.class);
        doReturn(true).when(decorator).passesChanceGate(any(), anyDouble(), anyDouble(), any());
        doReturn(true).when(decorator).isForcePlace();
        doReturn(vine).when(decorator).pickBlockData(any(), any(), anyDouble(), anyDouble());
        doReturn(new NativeBlockState[]{vine}).when(decorator).getBlockDataArray(any());

        IrisBiome biome = mock(IrisBiome.class);
        doReturn("test/biome").when(biome).getLoadKey();
        doReturn(InferredType.CAVE).when(biome).getInferredType();
        doReturn(new IrisDecorator[]{decorator}).when(biome).getDecoratorBucket(IrisDecorationPart.CEILING);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 32, 16);
        DecoratorPlatformHooks.Bindings previous = DecoratorPlatformHooks.bind((state, hunk, rX, rZ, x, y, z, mantle) -> {
            if (x == FAILING_X) {
                throw failure.apply(x);
            }
            return state;
        }, (surface, upward) -> true);
        try {
            IrisCeilingDecorator ceiling = new IrisCeilingDecorator(engine());
            for (int i = 0; i < 16; i++) {
                int x = reverse ? 15 - i : i;
                ceiling.decorate(x, 0, x, x, x, 0, 0, 0, output, biome, InferredType.CAVE, 9, 4);
            }
        } finally {
            DecoratorPlatformHooks.restore(previous);
        }
        return output;
    }

    private static Engine engine() {
        Engine engine = mock(Engine.class);
        doReturn(mock(SeedManager.class)).when(engine).getSeedManager();
        doReturn(mock(IrisData.class)).when(engine).getData();
        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(63).when(dimension).getFluidHeight();
        doReturn(dimension).when(engine).getDimension();
        doReturn(mock(IrisComplex.class)).when(engine).getComplex();
        EngineMantle mantle = mock(EngineMantle.class);
        doReturn(63).when(mantle).getFluidHeight(anyInt(), anyInt());
        doReturn(engine).when(mantle).getEngine();
        doReturn(mantle).when(engine).getMantle();
        return engine;
    }
}
