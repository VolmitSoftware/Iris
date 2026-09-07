package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.TerrainMatterView;
import art.arcane.iris.engine.modifier.IrisCarveModifier;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomePaletteLayer;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

public class IrisCarveWallPaletteScopeTest {
    @Test
    public void naturalWallsUseOneSelectedDataLookup() throws Exception {
        verifyWalls(Mode.NATURAL);
    }

    @Test
    public void contentWallsRestoreDataAfterNestedSampling() throws Exception {
        verifyWalls(Mode.CONTENT);
    }

    @Test
    public void detachedWallsUseDetachedData() throws Exception {
        verifyWalls(Mode.DETACHED);
    }

    @Test
    public void assemblyWallsUseUnpublishedData() throws Exception {
        verifyWalls(Mode.ASSEMBLY);
    }

    private static void verifyWalls(Mode mode) throws Exception {
        PlatformBlockState air = block("minecraft:cave_air", false);
        PlatformBlockState stone = block("minecraft:stone", true);
        PlatformBlockState wall = block("minecraft:calcite", true);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState(anyString())).thenReturn(air);
            blocks.when(() -> B.isSolid(any())).thenAnswer(call ->
                    call.<PlatformBlockState>getArgument(0).isSolid());
            Fixture fixture = new Fixture(air, wall);
            GenerationRuntime selected = mode == Mode.DETACHED ? fixture.detached : fixture.active;
            IrisData expectedData = mode == Mode.ASSEMBLY ? fixture.assemblyData : selected.data();
            IrisComplex expectedComplex = mode == Mode.ASSEMBLY ? fixture.assemblyComplex : selected.complex();
            fixture.expectedData = expectedData;
            doReturn(expectedComplex).when(fixture.context).getComplex();
            doReturn(mode == Mode.NATURAL).when(fixture.context).isNaturalTerrain();
            Hunk<PlatformBlockState> output = Hunk.newArrayHunk(16, 64, 16);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 64; y++) {
                    for (int z = 0; z < 16; z++) {
                        output.setRaw(x, y, z, stone);
                    }
                }
            }
            try (IrisEngine.GenerationRuntimeScope generation = fixture.engine.openGenerationRuntimeScope(
                    new IrisEngine.GenerationRuntimeBinding(fixture.engine, selected));
                 IrisContext.Scope context = IrisContext.open(fixture.engine, 71L, fixture.context);
                 MockedStatic<TerrainMatterView> terrain = mockStatic(TerrainMatterView.class)) {
                if (mode == Mode.ASSEMBLY) {
                    fixture.engine.runtimeAssembly.set(fixture.assembly);
                }
                terrain.when(() -> TerrainMatterView.iterate(eq(fixture.chunk), eq(MatterCavern.class), any()))
                        .thenAnswer(call -> {
                            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = call.getArgument(2);
                            for (int coordinate : new int[]{4, 8, 12}) {
                                consumer.accept(coordinate, 10, coordinate, new MatterCavern(true, "", (byte) 0));
                            }
                            return null;
                        });
                try {
                    assertSame(expectedData, fixture.engine.getData());
                    assertEquals(mode == Mode.NATURAL, expectedComplex.isNaturalTerrainContext());
                    fixture.modifier.onModify(-2, 3, output, false, fixture.context);
                    assertSame(expectedData, fixture.engine.getData());
                    assertEquals(12, fixture.paletteCalls.get());
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 64; y++) {
                            for (int z = 0; z < 16; z++) {
                                PlatformBlockState expected = stone;
                                for (int coordinate : new int[]{4, 8, 12}) {
                                    if (y == 10 && x == coordinate && z == coordinate) {
                                        expected = air;
                                    } else if (y == 10 && Math.abs(x - coordinate) + Math.abs(z - coordinate) == 1) {
                                        expected = wall;
                                    }
                                }
                                assertSame(expected, output.getRaw(x, y, z));
                            }
                        }
                    }
                    verify(fixture.chunk).release();
                    assertEquals(1, fixture.dataLookups.get());
                } finally {
                    fixture.engine.runtimeAssembly.remove();
                }
            }
            assertSame(fixture.active.data(), fixture.engine.getData());
        }
    }

    private static PlatformBlockState block(String key, boolean solid) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        doReturn(key).when(block).key();
        doReturn(solid).when(block).isSolid();
        return block;
    }

    private static void field(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static GenerationRuntime runtime(IrisData data, IrisComplex complex, EngineMantle mantle) {
        GenerationRuntime runtime = mock(GenerationRuntime.class);
        doReturn(data).when(runtime).data();
        doReturn(complex).when(runtime).complex();
        doReturn(mantle).when(runtime).mantle();
        doReturn(mock(IrisDimension.class)).when(runtime).dimension();
        return runtime;
    }

    private enum Mode {
        NATURAL,
        CONTENT,
        DETACHED,
        ASSEMBLY
    }

    private static final class Fixture {
        private final IrisEngine engine = mock(IrisEngine.class, CALLS_REAL_METHODS);
        private final IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        private final ChunkContext context = mock(ChunkContext.class);
        private final MantleChunk<Matter> chunk;
        private final GenerationRuntime active;
        private final GenerationRuntime detached;
        private final IrisData assemblyData = mock(IrisData.class);
        private final IrisComplex assemblyComplex = mock(IrisComplex.class);
        private final EngineRuntimeBuilder.RuntimeAssembly assembly;
        private final AtomicInteger dataLookups = new AtomicInteger();
        private final AtomicInteger paletteCalls = new AtomicInteger();
        private IrisData expectedData;

        @SuppressWarnings("unchecked")
        private Fixture(PlatformBlockState air, PlatformBlockState wall) throws Exception {
            Mantle<Matter> mantle = mock(Mantle.class);
            doReturn(new KMap<>()).when(mantle).getLoadedRegions();
            chunk = mock(MantleChunk.class);
            EngineMantle engineMantle = mock(EngineMantle.class);
            doReturn(mantle).when(engineMantle).getMantle();
            doReturn(chunk).when(mantle).getChunk(-2, 3);
            doReturn(chunk).when(chunk).use();
            active = runtime(mock(IrisData.class), mock(IrisComplex.class), engineMantle);
            detached = runtime(mock(IrisData.class), mock(IrisComplex.class), engineMantle);
            field(engine, IrisEngine.class, "lifecycleLock", new Object());
            field(engine, IrisEngine.class, "runtimeAssembly", new ThreadLocal<EngineRuntimeBuilder.RuntimeAssembly>());
            field(engine, IrisEngine.class, "biomeEnvironmentScopes", new ThreadLocal<>());
            field(engine, IrisEngine.class, "generationRuntimeScopes", new GenerationRuntimeScopeState());
            field(engine, IrisEngine.class, "detachedGenerationRuntimes", Collections.newSetFromMap(new IdentityHashMap<>()));
            field(engine, IrisEngine.class, "retiringGenerationRuntimes", Collections.newSetFromMap(new IdentityHashMap<>()));
            engine.detachedGenerationRuntimes.add(detached);
            EngineRuntime published = mock(EngineRuntime.class);
            doReturn(active).when(published).generation();
            engine.runtime = published;
            doReturn(IrisWorld.builder().minHeight(-64).maxHeight(0).build()).when(engine).getWorld();
            doReturn(64).when(engine).getHeight();
            doReturn(mock(EngineMetrics.class, RETURNS_DEEP_STUBS)).when(engine).getMetrics();
            doReturn(48).when(context).getRoundedHeight(anyInt(), anyInt());
            IrisComplex activeComplex = active.complex();
            IrisComplex detachedComplex = detached.complex();
            doCallRealMethod().when(activeComplex).isNaturalTerrainContext();
            doCallRealMethod().when(detachedComplex).isNaturalTerrainContext();
            doCallRealMethod().when(assemblyComplex).isNaturalTerrainContext();
            EngineTarget assemblyTarget = mock(EngineTarget.class);
            doReturn(engine.getWorld()).when(assemblyTarget).getWorld();
            doReturn(assemblyData).when(assemblyTarget).getData();
            doReturn(mock(IrisDimension.class)).when(assemblyTarget).getDimension();
            assembly = new EngineRuntimeBuilder.RuntimeAssembly(19, assemblyTarget);
            assembly.complex = assemblyComplex;
            assembly.mantle = engineMantle;
            IrisBiome biome = mock(IrisBiome.class);
            IrisBiomePaletteLayer palette = mock(IrisBiomePaletteLayer.class);
            doReturn(palette).when(biome).getWall();
            doAnswer(call -> {
                assertSame(expectedData, call.getArgument(4));
                paletteCalls.incrementAndGet();
                return wall;
            }).when(palette).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any(IrisData.class));
            doAnswer(call -> {
                try (IrisEngine.GenerationRuntimeScope ignored = engine.openGenerationRuntimeScope(
                        new IrisEngine.GenerationRuntimeBinding(engine, active))) {
                    assertSame(engine.runtimeAssembly.get() == null ? active.data() : assemblyData, engine.getData());
                }
                return biome;
            }).when(engine).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
            doReturn(engine).when(modifier).getEngine();
            doAnswer(call -> {
                dataLookups.incrementAndGet();
                return call.callRealMethod();
            }).when(modifier).getData();
            field(modifier, IrisCarveModifier.class, "rng", new RNG(319L));
            field(modifier, IrisCarveModifier.class, "AIR", air);
            field(modifier, IrisCarveModifier.class, "LAVA", air);
        }
    }
}
