package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.engine.terrain.Terrain3DColumn;
import art.arcane.iris.engine.terrain.Terrain3DColumnFixtures;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DimensionTerrainContextFallbackTest {
    @Test
    public void regionMapOnlyTargetsJoinPreparedRegionsWithoutDuplicateIdentities() {
        IrisRegion declared = new IrisRegion();
        IrisRegion mapped = new IrisRegion();
        declared.setLoadKey("declared");
        mapped.setLoadKey("mapped");
        IrisRegion excluded = mock(IrisRegion.class);
        when(excluded.isCompatExcluded()).thenReturn(true);
        IrisImageMapRuntime imageMaps = mock(IrisImageMapRuntime.class);
        Set<IrisRegion> mappedRegions = Collections.newSetFromMap(new IdentityHashMap<>());
        mappedRegions.add(declared);
        mappedRegions.add(mapped);
        mappedRegions.add(excluded);
        when(imageMaps.getMappedRegions()).thenReturn(mappedRegions);
        KList<IrisRegion> regions = DimensionTerrainContext.terrainRegions(List.of(declared), imageMaps);

        assertEquals(2, regions.size());
        assertSame(declared, regions.get(0));
        assertTrue(regions.stream().anyMatch(region -> region == mapped));
    }

    @Test
    public void sourceLedgeSlopeUsesNeighboringSourceSpansRatherThanTheHighestCap() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(128);
        when(dimension.getLoadKey()).thenReturn("root");
        when(complex.hasTerrain3D()).thenReturn(true);
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 40D));
        when(complex.terrainColumn(-17, 8)).thenReturn(Terrain3DColumnFixtures.spans(20, 0, 5, 20, 40));
        when(complex.terrainColumn(-14, 8)).thenReturn(Terrain3DColumnFixtures.spans(20, 0, 8, 20, 60));
        when(complex.terrainColumn(-17, 11)).thenReturn(Terrain3DColumnFixtures.spans(20, 0, 9, 20, 80));
        DimensionTerrainContext context = DimensionTerrainContext.forStack(engine, dimension);

        assertEquals(5D, context.getSurfaceSlopeStream(5).getDouble(-17, 8), 0D);
        assertEquals(40D, context.getSurfaceSlopeStream(40).getDouble(-17, 8), 0D);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void selfSamplerUsesNaturalValuesOnlyForNonblockingColumns() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        ProceduralStream<Double> naturalHeight = mock(ProceduralStream.class);
        ProceduralStream<Double> resolvedHeight = mock(ProceduralStream.class);
        ProceduralStream<Double> resolvedFluidHeight = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> naturalBiome = mock(ProceduralStream.class);
        ProceduralStream<IrisBiome> resolvedBiome = mock(ProceduralStream.class);
        ProceduralStream<IrisRegion> region = mock(ProceduralStream.class);
        ProceduralStream<PlatformBlockState> rock = mock(ProceduralStream.class);
        ProceduralStream<PlatformBlockState> configuredFluid = mock(ProceduralStream.class);
        IrisImageMapRuntime imageMapRuntime = mock(IrisImageMapRuntime.class);
        IrisBiome naturalBiomeValue = mock(IrisBiome.class);
        IrisBiome resolvedBiomeValue = mock(IrisBiome.class);
        PlatformBlockState naturalFluid = mock(PlatformBlockState.class);
        PlatformBlockState resolvedFluid = mock(PlatformBlockState.class);

        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(128);
        when(dimension.getLoadKey()).thenReturn("root");
        when(dimension.getFluidHeight()).thenReturn(12);
        when(complex.getNaturalHeightStream()).thenReturn(naturalHeight);
        when(complex.getHeightStream()).thenReturn(resolvedHeight);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(resolvedFluidHeight);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(naturalBiome);
        when(complex.getTrueBiomeStream()).thenReturn(resolvedBiome);
        when(complex.getRegionStream()).thenReturn(region);
        when(complex.getRockStream()).thenReturn(rock);
        when(complex.getFluidStream()).thenReturn(configuredFluid);
        when(complex.getImageMapRuntime()).thenReturn(imageMapRuntime);
        when(naturalHeight.getDouble(12D, -8D)).thenReturn(15D);
        when(resolvedHeight.getDouble(12D, -8D)).thenReturn(18D);
        when(resolvedFluidHeight.getDouble(12D, -8D)).thenReturn(14D);
        when(naturalBiome.get(12D, -8D)).thenReturn(naturalBiomeValue);
        when(resolvedBiome.get(12D, -8D)).thenReturn(resolvedBiomeValue);
        when(configuredFluid.get(12D, -8D)).thenReturn(naturalFluid);
        when(complex.resolveSurfaceFluid(12D, -8D)).thenReturn(resolvedFluid);

        DimensionTerrainContext context = DimensionTerrainContext.forStack(engine, dimension);
        when(engine.answersFromNaturalTerrain(12, -8)).thenReturn(true);
        assertEquals(15D, context.getNormalTerrainHeight(12D, -8D), 0D);
        assertEquals(12D, context.getFluidHeight(12D, -8D), 0D);
        assertSame(naturalBiomeValue, context.getBiome(12D, -8D));
        assertSame(naturalFluid, context.getFluidBlock(12D, -8D));

        when(engine.answersFromNaturalTerrain(12, -8)).thenReturn(false);
        assertEquals(18D, context.getNormalTerrainHeight(12D, -8D), 0D);
        assertEquals(14D, context.getFluidHeight(12D, -8D), 0D);
        assertSame(resolvedBiomeValue, context.getBiome(12D, -8D));
        assertSame(resolvedFluid, context.getFluidBlock(12D, -8D));
    }

    @Test
    public void selfStackReusesResolvedColumnsAndNaturalFallbackWithoutShapingAgain() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(128);
        when(dimension.getLoadKey()).thenReturn("root");
        Terrain3DColumn natural = Terrain3DColumnFixtures.spans(30, 0, 15, 25, 40);
        Terrain3DColumn resolved = Terrain3DColumnFixtures.spans(30, 0, 12, 28, 35);
        when(complex.naturalTerrainColumn(-17, 8)).thenReturn(natural);
        when(complex.terrainColumn(-17, 8)).thenReturn(resolved);
        DimensionTerrainContext context = DimensionTerrainContext.forStack(engine, dimension);

        assertSame(resolved, context.terrainColumn(-17, 8));
        when(engine.answersFromNaturalTerrain(-17, 8)).thenReturn(true);
        assertSame(natural, context.terrainColumn(-17, 8));
        when(engine.answersFromNaturalTerrain(-17, 8)).thenReturn(false);
        assertSame(resolved, context.terrainColumn(-17, 8));
    }
}
