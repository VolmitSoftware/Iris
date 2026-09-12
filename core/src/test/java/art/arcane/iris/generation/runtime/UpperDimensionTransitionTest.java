package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.terrain.Terrain3DColumnFixtures;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpperDimensionTransitionTest {
    @Test
    public void mirroredDensityPreservesEverySolidSpanAndFace() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(80, 0, 30, 40, 60, 100, 110);
        UpperDimensionContext.Column column = new UpperDimensionContext.Column(128, 17, terrain);
        assertTrue(column.isSolid(17));
        assertTrue(column.isSolid(27));
        assertFalse(column.isSolid(28));
        assertFalse(column.isSolid(66));
        assertTrue(column.isSolid(67));
        assertEquals(67, column.faceY(70));
        assertEquals(97, column.faceY(110));
        assertFalse(column.isSolid(128));
    }

    @Test
    public void upperGapClipsToTheNextRealMirroredFace() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("main");
        when(dimension.getUpperDimensionGap()).thenReturn(28);
        IrisComplex complex = mock(IrisComplex.class);
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(80, 0, 30, 40, 60, 100, 110);
        when(complex.naturalTerrainColumn(-17, 8)).thenReturn(terrain);
        when(complex.getUnblendedNaturalHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 110D));
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 64D));
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(128);
        UpperDimensionContext context = UpperDimensionContext.create(engine, dimension);

        assertEquals(97, context.getEffectiveSurfaceY(-17, 8));
        UpperDimensionContext.Column column = context.sampleColumn(-17, 8);
        assertFalse(column.isSolid(92));
        assertTrue(column.isSolid(97));
        assertEquals(97, column.faceY(110));
        verify(complex, never()).terrainColumn(anyInt(), anyInt());
    }
    @Test
    public void currentCeilingInputRespectsCurrentLowerTerrainGap() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("main");
        when(dimension.getUpperDimensionGap()).thenReturn(32);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getUnblendedNaturalHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 139D));
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 75D + 4D * x));
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(plan);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(256);

        UpperDimensionContext context = UpperDimensionContext.create(engine, dimension);

        assertEquals(116, context.getEffectiveSurfaceY(0, 8));
        assertEquals(139, context.getEffectiveSurfaceY(8, 8));
        assertEquals(171, context.getEffectiveSurfaceY(16, 8));
        verify(plan, never()).terrainSampleAt(anyInt(), anyInt());
    }

    @Test
    public void upperTerrainUsesUnblendedCurrentGeneratorBeforeGeometryReconciliation() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getLoadKey()).thenReturn("main");
        when(dimension.getUpperDimensionGap()).thenReturn(32);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getUnblendedNaturalHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 80D));
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 64D));
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(plan);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(256);

        UpperDimensionContext context = UpperDimensionContext.create(engine, dimension);

        assertEquals(175, context.getEffectiveSurfaceY(-17, 8));
        verify(plan, never()).terrainSampleAt(anyInt(), anyInt());
        verify(complex, never()).getNaturalHeightStream();
        verify(complex, never()).getNaturalTrueBiomeStream();
    }

    @Test
    public void preservesBoundaryAndCurrentEndpointsAndFixedGap() {
        UpperDimensionContext.CeilingLayout layout = new UpperDimensionContext.CeilingLayout(384, 32);
        double current = 180D;

        assertEquals(203, UpperDimensionContext.effectiveSurfaceY(current, layout, 100));
        assertEquals(252, UpperDimensionContext.effectiveSurfaceY(current, layout, 220));
        assertEquals(188, -64 + UpperDimensionContext.effectiveSurfaceY(current, layout, 220));
        assertEquals(384, UpperDimensionContext.effectiveSurfaceY(current, layout, Integer.MAX_VALUE));
    }

    @Test
    public void zeroDepthCeilingsRemainOutsideTheHunk() {
        UpperDimensionContext.CeilingLayout layout = new UpperDimensionContext.CeilingLayout(256, 32);
        assertEquals(256, UpperDimensionContext.effectiveSurfaceY(0D, layout, 64));
        assertEquals(256, UpperDimensionContext.effectiveSurfaceY(0.49D, layout, 64));
        assertEquals(254, UpperDimensionContext.effectiveSurfaceY(0.51D, layout, 64));
    }

    @Test
    public void rejectsInvalidCeilingLayouts() {
        assertThrows(IllegalArgumentException.class, () -> new UpperDimensionContext.CeilingLayout(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new UpperDimensionContext.CeilingLayout(256, -1));
    }

}
