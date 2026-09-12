package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpperDimensionImageMapRuntimeTest {
    @Test
    public void mappedRegionAndBiomeOverrideProceduralSelections() {
        IrisImageMapRuntime runtime = mock(IrisImageMapRuntime.class);
        IrisRegion proceduralRegion = new IrisRegion();
        IrisRegion mappedRegion = new IrisRegion();
        IrisBiome proceduralBiome = new IrisBiome();
        IrisBiome mappedBiome = new IrisBiome();
        when(runtime.sampleRegion(12D, -7D)).thenReturn(mappedRegion);
        when(runtime.sampleBiome(12D, -7D)).thenReturn(mappedBiome);

        assertSame(mappedRegion, UpperDimensionContext.mappedRegion(
                runtime, proceduralRegion, 12D, -7D));
        assertSame(mappedBiome, UpperDimensionContext.mappedBiome(
                runtime, proceduralBiome, 12D, -7D));
    }

    @Test
    public void absentMappingsPreserveProceduralSelections() {
        IrisImageMapRuntime runtime = mock(IrisImageMapRuntime.class);
        IrisRegion proceduralRegion = new IrisRegion();
        IrisBiome proceduralBiome = new IrisBiome();
        PlatformBlockState proceduralBlock = mock(PlatformBlockState.class);

        assertSame(proceduralRegion, UpperDimensionContext.mappedRegion(
                runtime, proceduralRegion, -2D, 4D));
        assertSame(proceduralBiome, UpperDimensionContext.mappedBiome(
                runtime, proceduralBiome, -2D, 4D));
        assertSame(proceduralBlock, UpperDimensionContext.mappedSurfaceBlock(
                runtime, proceduralBlock, -2D, 4D));
    }

    @Test
    public void mappedHeightAndSurfaceBlockAreAuthoritative() {
        IrisImageMapRuntime runtime = mock(IrisImageMapRuntime.class);
        PlatformBlockState proceduralBlock = mock(PlatformBlockState.class);
        PlatformBlockState mappedBlock = mock(PlatformBlockState.class);
        when(runtime.sampleTerrainHeight(3D, 9D, 80D)).thenReturn(144D);
        when(runtime.sampleSurfaceBlock(3D, 9D)).thenReturn(mappedBlock);

        assertEquals(144D, UpperDimensionContext.mappedTerrainHeight(
                runtime, 80D, 3D, 9D), 0D);
        assertSame(mappedBlock, UpperDimensionContext.mappedSurfaceBlock(
                runtime, proceduralBlock, 3D, 9D));
    }
}
