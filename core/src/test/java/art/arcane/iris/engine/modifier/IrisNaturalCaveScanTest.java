package art.arcane.iris.engine.modifier;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.actuator.IrisDecorantActuator;
import art.arcane.iris.engine.decorator.IrisCeilingDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

public class IrisNaturalCaveScanTest {
    private MockedStatic<B> blocks;

    @Before
    public void prepareBlocks() {
        blocks = mockStatic(B.class, CALLS_REAL_METHODS);
        blocks.when(() -> B.getState(anyString())).thenReturn(null);
    }

    @After
    public void releaseBlocks() {
        blocks.close();
    }

    @Test
    public void narrowScanPreservesDecoratedBlocksMarkersAndMutationOrder() throws Exception {
        verifyScan(5, 3);
    }

    @Test
    public void chunkScanPreservesDecoratedBlocksMarkersAndMutationOrder() throws Exception {
        verifyScan(16, 16);
    }

    @Test
    public void listenerFailureReleasesMantleChunk() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("listener rejected decoration");
        Hunk<PlatformBlockState> output = fixture.output(3, 2).listen((x, y, z, state) -> {
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.modifier.decorateNaturalCaves(-32, 48, output)));
        verify(fixture.chunk).release();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dimensionFailureReleasesMantleChunk() throws Exception {
        Fixture fixture = new Fixture();
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        doReturn(3).when(output).getWidth();
        doReturn(2).when(output).getDepth();
        IllegalStateException failure = new IllegalStateException("height unavailable");
        doAnswer(call -> { throw failure; }).when(output).getHeight();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.modifier.decorateNaturalCaves(-32, 48, output)));
        verify(fixture.chunk).release();
    }

    private static void verifyScan(int width, int depth) throws Exception {
        Fixture fixture = new Fixture();
        Hunk<PlatformBlockState> output = fixture.output(width, depth);
        List<String> writes = new ArrayList<>();
        fixture.modifier.decorateNaturalCaves(-32, 48,
                output.listen((x, y, z, state) -> writes.add(x + ":" + y + ":" + z + ":" + state.key())));
        List<String> expectedCalls = new ArrayList<>();
        List<String> expectedMarkers = new ArrayList<>();
        List<String> expectedWrites = new ArrayList<>();
        Method markerRoll = IrisCarveModifier.class.getDeclaredMethod("markerRoll", int.class, int.class, int.class, long.class);
        markerRoll.setAccessible(true);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int floor : new int[]{2, 20}) {
                    int ceiling = floor == 2 ? 5 : 22;
                    expectedCalls.add("floor:" + x + ":" + z + ":" + (floor - 1) + ":" + (ceiling - floor - 1));
                    expectedCalls.add("ceiling:" + x + ":" + z + ":" + ceiling + ":" + (ceiling - floor - 1));
                    expectedWrites.add(x + ":" + floor + ":" + z + ":minecraft:moss_block");
                    if (floor == 2) {
                        expectedWrites.add(x + ":15:" + z + ":minecraft:stone");
                        for (int y = 20; y <= 22; y++) {
                            expectedWrites.add(x + ":" + y + ":" + z + ":minecraft:cave_air");
                        }
                    }
                    expectedWrites.add(x + ":" + ceiling + ":" + z + ":minecraft:calcite");
                    if ((boolean) markerRoll.invoke(fixture.modifier, x - 32, ceiling, z + 48, 0x9E3779B97F4A7C15L)) {
                        expectedMarkers.add((x - 32) + ":" + ceiling + ":" + (z + 48) + ":" + MarkerMatter.CAVE_CEILING);
                    }
                    if ((boolean) markerRoll.invoke(fixture.modifier, x - 32, floor, z + 48, 0xC2B2AE3D27D4EB4FL)) {
                        expectedMarkers.add((x - 32) + ":" + floor + ":" + (z + 48) + ":" + MarkerMatter.CAVE_FLOOR);
                    }
                }
                for (int y = 0; y < 32; y++) {
                    PlatformBlockState expected = initialState(fixture, y);
                    if (y == 2 || y == 20) {
                        expected = fixture.floor;
                    } else if (y == 5 || y == 22) {
                        expected = fixture.ceiling;
                    } else if (y == 15) {
                        expected = fixture.stone;
                    } else if (y == 21) {
                        expected = fixture.air;
                    }
                    assertSame("block " + x + ":" + y + ":" + z, expected, output.getRaw(x, y, z));
                }
            }
        }
        assertEquals(expectedCalls, fixture.calls);
        assertEquals(expectedWrites, writes);
        assertEquals(expectedMarkers, fixture.markers);
        assertTrue("fixture must exercise marker writes", !expectedMarkers.isEmpty());
        verify(fixture.chunk).use();
        verify(fixture.chunk).release();
    }

    private static PlatformBlockState initialState(Fixture fixture, int y) {
        return y >= 2 && y <= 5 || y == 10 || y >= 14 && y <= 17 || y >= 28
                ? fixture.air : fixture.stone;
    }

    private static PlatformBlockState block(String key, boolean solid) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        doReturn(key).when(state).key();
        doReturn(key).when(state).materialKey();
        doReturn(solid).when(state).isSolid();
        return state;
    }

    private static final class Fixture {
        private final PlatformBlockState air = block("minecraft:cave_air", false);
        private final PlatformBlockState stone = block("minecraft:stone", true);
        private final PlatformBlockState floor = block("minecraft:moss_block", true);
        private final PlatformBlockState ceiling = block("minecraft:calcite", true);
        private final IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        private final MantleChunk<Matter> chunk;
        private final List<String> calls = new ArrayList<>();
        private final List<String> markers = new ArrayList<>();

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            Engine engine = mock(Engine.class);
            EngineMantle engineMantle = mock(EngineMantle.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            chunk = mock(MantleChunk.class);
            doReturn(engine).when(modifier).getEngine();
            doReturn(mock(IrisComplex.class)).when(modifier).getComplex();
            doReturn(32).when(engine).getHeight();
            doReturn(new SeedManager(1337L)).when(engine).getSeedManager();
            doReturn(engineMantle).when(engine).getMantle();
            doReturn(mantle).when(engineMantle).getMantle();
            doReturn(chunk).when(mantle).getChunk(-2, 3);
            doReturn(chunk).when(chunk).use();
            doAnswer(call -> {
                markers.add(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2) + ":" + call.getArgument(3));
                return null;
            }).when(mantle).set(anyInt(), anyInt(), anyInt(), any());
            IrisBiome biome = mock(IrisBiome.class);
            doReturn(new IrisDecorator[]{new IrisDecorator()}).when(biome).getDecoratorBucket(IrisDecorationPart.NONE);
            doReturn(new IrisDecorator[]{new IrisDecorator()}).when(biome).getDecoratorBucket(IrisDecorationPart.CEILING);
            doReturn(biome).when(modifier).resolveCaveBoundaryBiome(eq(chunk), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any());
            IrisDecorantActuator decorant = mock(IrisDecorantActuator.class);
            IrisSurfaceDecorator surface = mock(IrisSurfaceDecorator.class);
            IrisCeilingDecorator roof = mock(IrisCeilingDecorator.class);
            doReturn(surface).when(decorant).getSurfaceDecorator();
            doReturn(roof).when(decorant).getCeilingDecorator();
            Field field = IrisCarveModifier.class.getDeclaredField("decorant");
            field.setAccessible(true);
            field.set(modifier, decorant);
            doAnswer(call -> {
                int x = call.getArgument(0);
                int z = call.getArgument(1);
                int y = call.getArgument(11);
                Hunk<PlatformBlockState> output = call.getArgument(8);
                calls.add("floor:" + x + ":" + z + ":" + y + ":" + call.getArgument(12));
                output.setRaw(x, y + 1, z, floor);
                if (y == 1) {
                    output.setRaw(x, 15, z, stone);
                    for (int height = 20; height <= 22; height++) {
                        output.setRaw(x, height, z, air);
                    }
                }
                return null;
            }).when(surface).decorate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), eq(biome), any(), anyInt(), anyInt());
            doAnswer(call -> {
                int x = call.getArgument(0);
                int z = call.getArgument(1);
                int y = call.getArgument(11);
                Hunk<PlatformBlockState> output = call.getArgument(8);
                calls.add("ceiling:" + x + ":" + z + ":" + y + ":" + call.getArgument(12));
                output.setRaw(x, y, z, ceiling);
                return null;
            }).when(roof).decorate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), eq(biome), any(), anyInt(), anyInt());
        }

        private Hunk<PlatformBlockState> output(int width, int depth) {
            Hunk<PlatformBlockState> output = Hunk.newArrayHunk(width, 32, depth);
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    for (int y = 0; y < 32; y++) {
                        output.setRaw(x, y, z, initialState(this, y));
                    }
                }
            }
            return output;
        }
    }
}
