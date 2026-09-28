package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.decoration.IrisCeilingDecorator;
import art.arcane.iris.generation.decoration.IrisSurfaceDecorator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.mantle.CaveTerrainSnapshot;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.chunk.ChunkDataHunkHolder;
import art.arcane.iris.generation.chunk.ColumnExtentListeningHunk;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import org.bukkit.generator.ChunkGenerator;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
    public void decorationReusesOnlyTheCarveSnapshotOfItsOwnChunk() throws Exception {
        Fixture fixture = new Fixture();
        ChunkContext context = mock(ChunkContext.class);
        doReturn(CaveTerrainSnapshot.capture(fixture.chunk, -2, 3)).when(context).getCaveTerrain();
        clearInvocations(fixture.chunk);
        fixture.modifier.decorateNaturalCaves(-32, 48, fixture.output(3, 2), context);
        verify(fixture.chunk, never()).use();
        verify(context).setCaveTerrain(null);
        assertFalse(fixture.calls.isEmpty());

        Fixture other = new Fixture();
        ChunkContext stale = mock(ChunkContext.class);
        doReturn(CaveTerrainSnapshot.capture(other.chunk, 5, 5)).when(stale).getCaveTerrain();
        clearInvocations(other.chunk);
        other.modifier.decorateNaturalCaves(-32, 48, other.output(3, 2), stale);
        verify(other.chunk).use();
        assertEquals(fixture.calls, other.calls);
    }

    @Test
    public void listenerFailureReleasesMantleChunk() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("listener rejected decoration");
        Hunk<NativeBlockState> output = fixture.output(3, 2).listen((x, y, z, state) -> {
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.modifier.decorateNaturalCaves(-32, 48, output, null)));
        verify(fixture.chunk).release();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void dimensionFailureReleasesMantleChunk() throws Exception {
        Fixture fixture = new Fixture();
        Hunk<NativeBlockState> output = mock(Hunk.class);
        doReturn(3).when(output).getWidth();
        doReturn(2).when(output).getDepth();
        IllegalStateException failure = new IllegalStateException("height unavailable");
        doAnswer(call -> { throw failure; }).when(output).getHeight();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.modifier.decorateNaturalCaves(-32, 48, output, null)));
        verify(fixture.chunk).release();
    }

    @Test
    public void columnExtentScanMatchesFullScanWhenDecorationBuildsAboveTheColumnTop() throws Exception {
        IrisPlatforms.bind(mock(IrisPlatform.class));
        try {
            Fixture full = new Fixture(true);
            Hunk<NativeBlockState> fullOutput = full.sparseOutput(Hunk.newArrayHunk(16, 32, 16));
            List<String> fullWrites = new ArrayList<>();
            full.modifier.decorateNaturalCaves(-32, 48,
                    fullOutput.listen((x, y, z, state) -> fullWrites.add(x + ":" + y + ":" + z + ":" + state.key())), null);

            Fixture bounded = new Fixture(true);
            ChunkGenerator.ChunkData chunkData = mock(ChunkGenerator.ChunkData.class);
            doReturn(0).when(chunkData).getMinHeight();
            doReturn(32).when(chunkData).getMaxHeight();
            ChunkDataHunkHolder holder = new ChunkDataHunkHolder(chunkData);
            bounded.sparseOutput(holder);
            List<String> boundedWrites = new ArrayList<>();
            bounded.modifier.decorateNaturalCaves(-32, 48, new ColumnExtentListeningHunk<>(holder,
                    (x, y, z, state) -> boundedWrites.add(x + ":" + y + ":" + z + ":" + state.key())), null);

            assertTrue("fixture must decorate a zone above the initial column top",
                    full.calls.contains("floor:0:0:27:1"));
            assertEquals(full.calls, bounded.calls);
            assertEquals(fullWrites, boundedWrites);
            assertEquals(full.markers, bounded.markers);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 32; y++) {
                        NativeBlockState expected = fullOutput.getRaw(x, y, z);
                        NativeBlockState actual = holder.getStoredRaw(x, y, z);
                        assertEquals("block " + x + ":" + y + ":" + z,
                                expected == null ? null : expected.key(), actual == null ? null : actual.key());
                    }
                }
            }
        } finally {
            IrisPlatforms.unbind();
        }
    }

    private static void verifyScan(int width, int depth) throws Exception {
        Fixture fixture = new Fixture();
        Hunk<NativeBlockState> output = fixture.output(width, depth);
        List<String> writes = new ArrayList<>();
        fixture.modifier.decorateNaturalCaves(-32, 48,
                output.listen((x, y, z, state) -> writes.add(x + ":" + y + ":" + z + ":" + state.key())), null);
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
                    NativeBlockState expected = initialState(fixture, y);
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

    private static NativeBlockState initialState(Fixture fixture, int y) {
        return y >= 2 && y <= 5 || y == 10 || y >= 14 && y <= 17 || y >= 28
                ? fixture.air : fixture.stone;
    }

    private static NativeBlockState block(String key, boolean solid) {
        NativeBlockState state = mock(NativeBlockState.class);
        doReturn(key).when(state).key();
        doReturn(key).when(state).materialKey();
        doReturn(solid).when(state).isSolid();
        return state;
    }

    private static final class Fixture {
        private final NativeBlockState air = block("minecraft:cave_air", false);
        private final NativeBlockState stone = block("minecraft:stone", true);
        private final NativeBlockState floor = block("minecraft:moss_block", true);
        private final NativeBlockState ceiling = block("minecraft:calcite", true);
        private final IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        private final MantleChunk<Matter> chunk;
        private final List<String> calls = new ArrayList<>();
        private final List<String> markers = new ArrayList<>();

        private Fixture() throws Exception {
            this(false);
        }

        @SuppressWarnings("unchecked")
        private Fixture(boolean buildAboveTop) throws Exception {
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
            doReturn(biome).when(modifier).resolveCaveBoundaryBiome(argThat((CaveTerrainSnapshot terrain) -> terrain != null && terrain.covers(-2, 3)), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any());
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
                Hunk<NativeBlockState> output = call.getArgument(8);
                calls.add("floor:" + x + ":" + z + ":" + y + ":" + call.getArgument(12));
                output.setRaw(x, y + 1, z, floor);
                if (y == 1) {
                    output.setRaw(x, 15, z, stone);
                    for (int height = 20; height <= 22; height++) {
                        output.setRaw(x, height, z, air);
                    }
                    if (buildAboveTop) {
                        output.setRaw(x, 31, z, stone);
                    }
                }
                return null;
            }).when(surface).decorate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), eq(biome), any(), anyInt(), anyInt());
            doAnswer(call -> {
                int x = call.getArgument(0);
                int z = call.getArgument(1);
                int y = call.getArgument(11);
                Hunk<NativeBlockState> output = call.getArgument(8);
                calls.add("ceiling:" + x + ":" + z + ":" + y + ":" + call.getArgument(12));
                output.setRaw(x, y, z, ceiling);
                return null;
            }).when(roof).decorate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), eq(biome), any(), anyInt(), anyInt());
        }

        private <H extends Hunk<NativeBlockState>> H sparseOutput(H output) {
            for (int x = 0; x < output.getWidth(); x++) {
                for (int z = 0; z < output.getDepth(); z++) {
                    for (int y = 0; y < 32; y++) {
                        NativeBlockState state = initialState(this, y);
                        if (state != air) {
                            output.setRaw(x, y, z, state);
                        }
                    }
                }
            }
            return output;
        }

        private Hunk<NativeBlockState> output(int width, int depth) {
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(width, 32, depth);
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
