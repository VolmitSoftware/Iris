package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisComplexSurfaceBiomeTest {
    @Test
    public void gradingFootprintUsesTheBankBiome() {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                1L,
                HydrologyFeatureType.SURFACE_POOL,
                2L,
                3L,
                4,
                70,
                5,
                1,
                0,
                false
        );
        HydrologyColumnLayer grading = new HydrologyColumnLayer(
                feature,
                72,
                74,
                74,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                "water",
                "river",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
        HydrologyColumnSample sample = new HydrologyColumnSample(
                4,
                5,
                76,
                63,
                false,
                "exact_parent",
                List.of(grading)
        );

        assertEquals("dry", IrisComplex.hydrologySurfaceBiomeKey(sample));
    }

    @Test
    public void naturalOceanMaskTracksContinentalIntent() {
        assertEquals(InferredType.SEA, IrisComplex.resolveNaturalInferredType(
                constantType(InferredType.SEA),
                null,
                0D,
                0D
        ));
        assertEquals(InferredType.LAND, IrisComplex.resolveNaturalInferredType(
                constantType(InferredType.LAND),
                null,
                0D,
                0D
        ));
    }

    @Test
    public void focusNaturalOceanMaskUsesTheFocusedSurfaceType() {
        assertEquals(InferredType.SEA, IrisComplex.resolveNaturalInferredType(
                constantType(InferredType.LAND),
                new IrisBiome().setInferredType(InferredType.SEA),
                0D,
                0D
        ));
        assertEquals(InferredType.LAND, IrisComplex.resolveNaturalInferredType(
                constantType(InferredType.SEA),
                new IrisBiome().setInferredType(InferredType.LAND),
                0D,
                0D
        ));
        assertEquals(InferredType.SHORE, IrisComplex.resolveNaturalInferredType(
                constantType(InferredType.SEA),
                new IrisBiome().setInferredType(InferredType.SHORE),
                0D,
                0D
        ));
    }

    @Test
    public void naturalSlopeMatchesTheRangeThreeStreamFormula() {
        assertEquals(5D, IrisComplex.calculateNaturalSlope(10D, 13D, 14D), 0D);
    }

    @Test
    public void elevatedRiverBankUsesTheLocalWaterHead() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome sea = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(false).when(base).isAquatic();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                68D,
                base,
                region,
                12D,
                18D,
                70D,
                constant(base),
                constant(sea),
                constant(base)
        );

        assertSame(sea, resolved);
    }

    @Test
    public void shorelineHeightSelectsShoreBiome() {
        IrisBiome base = mock(IrisBiome.class);
        IrisBiome shore = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(base).isShore();
        doReturn(3D).when(region).getShoreHeight(12D, 18D);

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                63D,
                base,
                region,
                12D,
                18D,
                63D,
                constant(base),
                constant(base),
                constant(shore)
        );

        assertSame(shore, resolved);
    }

    @Test
    public void raisedAquaticBiomeReturnsToLand() {
        IrisBiome aquatic = mock(IrisBiome.class);
        IrisBiome land = mock(IrisBiome.class);
        IrisRegion region = mock(IrisRegion.class);
        doReturn(false).when(aquatic).isShore();
        doReturn(false).when(aquatic).isLand();
        doReturn(1D).when(region).getShoreHeight(4D, 7D);

        IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                66D,
                aquatic,
                region,
                4D,
                7D,
                63D,
                constant(land),
                constant(aquatic),
                constant(aquatic)
        );

        assertSame(land, resolved);
    }

    private static ProceduralStream<IrisBiome> constant(IrisBiome biome) {
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> stream = mock(ProceduralStream.class);
        doReturn(biome).when(stream).get(anyDouble(), anyDouble());
        return stream;
    }

    private static ProceduralStream<InferredType> constantType(InferredType type) {
        return ProceduralStream.of(
                (x, z) -> type,
                Interpolated.of(value -> 0D, value -> type)
        );
    }
}
