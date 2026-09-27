package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.iris.world.storage.matter.PreObjectMatterTest;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TerrainPlacementQueryTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private EngineMantle engineMantle;
    private Mantle<Matter> mantle;
    private NativeBlockState stone;
    private NativeBlockState air;
    private Matter matter;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        PreObjectMatterTest.setUpBukkit();
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        stone = mock(NativeBlockState.class);
        air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(registries.block(anyString())).thenReturn(air);
        when(registries.blockOrNull("minecraft:stone")).thenReturn(stone);
        when(registries.blockOrNull("minecraft:air")).thenReturn(air);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisMatterSupport.ensureRegistered();

        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Integer> heights = mock(ProceduralStream.class);
        when(heights.get(anyDouble(), anyDouble())).thenReturn(2);
        when(complex.getRoundedHeighteightStream()).thenReturn(heights);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 5D));
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getComplex()).thenReturn(complex);
        mantle = mock(Mantle.class);
        when(mantle.getLoadedRegions()).thenReturn(new KMap<>());
        when(mantle.getWorldHeight()).thenReturn(4);
        when(mantle.get(0, 0, 0, NativeBlockState.class)).thenReturn(stone);
        when(mantle.get(0, 1, 0, MatterCavern.class)).thenReturn(new MatterCavern(true, "", (byte) 0));
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(mantle.getChunk(0, 0)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(chunk.exists(0)).thenReturn(true);
        matter = new IrisMatter(16, 16, 16);
        when(chunk.get(0)).thenReturn(matter);
        engineMantle = mock(EngineMantle.class, CALLS_REAL_METHODS);
        doReturn(engine).when(engineMantle).getEngine();
        doReturn(mantle).when(engineMantle).getMantle();
    }

    @After
    public void tearDown() {
        IrisPlatforms.unbind();
    }

    @Test
    public void placementUsesTerrainStreamsAndRecordedCarving() {
        assertEquals(2, engineMantle.getHighest(0, 0, null, true));
        assertEquals(5, engineMantle.getHighest(0, 0, null, false));
        assertEquals(5, engineMantle.getFluidHeight(0, 0));
        assertSame(stone, engineMantle.get(0, 0, 0));
        assertSame(air, engineMantle.get(0, 1, 0));
        assertTrue(engineMantle.isCarved(0, 1, 0));
        assertFalse(engineMantle.isCarved(0, 3, 0));
    }

    @Test
    public void scalarPlacementQueriesDoNotGenerateTerrain() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisComplex complex = engineMantle.getComplex();
        doReturn(complex).when(engine).getComplex();
        doReturn(null).when(engine).getData();
        doReturn(false).when(engine).answersFromNaturalTerrain(0, 0);
        doReturn(engineMantle).when(engine).getMantle();
        EngineMode mode = mock(EngineMode.class);
        doReturn(mode).when(engine).getMode();

        assertEquals(2, engine.getHeight(0, 0, true));
        assertEquals(5, engine.getHeight(0, 0, false));
        assertEquals(2, engineMantle.getHighest(0, 0, null, true));
        assertEquals(5, engineMantle.getHighest(0, 0, null, false));
        verifyNoInteractions(mode);
    }

    @Test
    public void writerPrerequisitesRetainNaturalCarvingBeforeContentFills() {
        matter.<NativeBlockState>slice(NativeBlockState.class).set(0, 1, 0, stone);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(0, 1, 0, PreObjectMatterCell.block(null).captureCavern(new MatterCavern(true, "", (byte) 0)));
        try (MantleWriter writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false)) {
            assertSame(air, writer.getPrerequisiteBlock(0, 1, 0));
            assertSame(stone, writer.get(0, 1, 0));
            assertTrue(writer.isPrerequisiteCarved(0, 1, 0));
            assertFalse(writer.isCarved(0, 1, 0));
            assertArrayEquals(new byte[]{0, 1, 0, 0}, writer.getPrerequisiteCarvedColumn(0, 0, 4));
            assertArrayEquals(new byte[]{0, 0, 0, 0}, writer.getCarvedColumn(0, 0, 4));
        }
    }
}
