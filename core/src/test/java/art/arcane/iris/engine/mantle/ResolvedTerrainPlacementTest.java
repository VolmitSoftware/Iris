package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.iris.util.project.matter.slices.PreObjectMatterTest;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ResolvedTerrainPlacementTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private EngineMantle engineMantle;
    private Mantle<Matter> mantle;
    private PlatformBlockState stone;
    private PlatformBlockState air;
    private Matter matter;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        PreObjectMatterTest.setUpBukkit();
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        stone = mock(PlatformBlockState.class);
        air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(registries.block(anyString())).thenReturn(air);
        when(registries.blockOrNull("minecraft:stone")).thenReturn(stone);
        when(registries.blockOrNull("minecraft:air")).thenReturn(air);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisMatterSupport.ensureRegistered();

        BoundaryColumnGeometry.Voxel solid = new BoundaryColumnGeometry.Voxel(
                "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
        BoundaryColumnGeometry.Voxel open = new BoundaryColumnGeometry.Voxel(
                "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
        TerrainBoundarySignature column = mock(TerrainBoundarySignature.class);
        when(column.geometry()).thenReturn(BoundaryColumnGeometry.fromVoxels(-64,
                List.of(solid, open, solid, open)));
        when(column.oceanFloorHeight()).thenReturn(2);
        when(column.surfaceHeight()).thenReturn(5);
        when(column.fluidHeight()).thenReturn(OptionalInt.of(5));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.resolvedTerrainColumn(anyInt(), anyInt())).thenReturn(Optional.of(column));
        when(complex.resolvedTerrainHeight(anyInt(), anyInt(), eq(true)))
                .thenReturn(OptionalInt.of(2));
        when(complex.resolvedTerrainHeight(anyInt(), anyInt(), eq(false)))
                .thenReturn(OptionalInt.of(5));
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getComplex()).thenReturn(complex);
        mantle = mock(Mantle.class);
        when(mantle.getWorldHeight()).thenReturn(4);
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
    public void placementUsesResolvedSurfaceFluidAndEnclosedOccupancy() {
        assertEquals(2, engineMantle.getHighest(0, 0, null, true));
        assertEquals(5, engineMantle.getHighest(0, 0, null, false));
        assertEquals(5, engineMantle.getFluidHeight(0, 0));
        assertSame(stone, engineMantle.get(0, 0, 0));
        assertSame(air, engineMantle.get(0, 1, 0));
        assertTrue(engineMantle.isCarved(0, 1, 0));
        assertFalse(engineMantle.isCarved(0, 3, 0));
    }

    @Test
    public void scalarPlacementQueriesDoNotReloadColumnGeometry() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisComplex complex = engineMantle.getComplex();
        doReturn(complex).when(engine).getComplex();

        assertEquals(2, engine.getHeight(0, 0, true));
        assertEquals(5, engine.getHeight(0, 0, false));
        assertEquals(2, engineMantle.getHighest(0, 0, null, true));
        assertEquals(5, engineMantle.getHighest(0, 0, null, false));
        verify(complex, never()).resolvedTerrainColumn(anyInt(), anyInt());
    }

    @Test
    public void writerPrerequisitesIgnoreContentWhileLiveOccupancyIncludesBlockOnlyFills() {
        matter.<PlatformBlockState>slice(PlatformBlockState.class).set(0, 1, 0, stone);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(0, 1, 0, PreObjectMatterCell.block(null));
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
