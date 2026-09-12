package art.arcane.iris.studio.render;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.HydrologyCandidateKind;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyDiagnosticCandidate;
import art.arcane.iris.generation.hydrology.HydrologyDiagnosticRenderSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyRenderSample;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisRendererRiverTest {
    @Test
    public void everyAcceptedHydrologyFeatureHasItsDiagnosticColor() {
        Map<HydrologyFeatureType, Integer> expected = new EnumMap<>(HydrologyFeatureType.class);
        expected.put(HydrologyFeatureType.SURFACE_POOL, new Color(48, 112, 190).getRGB());
        expected.put(HydrologyFeatureType.RIFFLE, new Color(96, 166, 214).getRGB());
        expected.put(HydrologyFeatureType.CASCADE, new Color(135, 204, 232).getRGB());
        expected.put(HydrologyFeatureType.WATERFALL, new Color(190, 236, 255).getRGB());
        expected.put(HydrologyFeatureType.SINKHOLE, new Color(176, 146, 232).getRGB());
        expected.put(HydrologyFeatureType.RIDGE_BORE, new Color(120, 99, 174).getRGB());
        expected.put(HydrologyFeatureType.UNDERGROUND_POOL, new Color(102, 86, 196).getRGB());
        expected.put(HydrologyFeatureType.UNDERGROUND_DROP, new Color(102, 86, 196).getRGB());
        expected.put(HydrologyFeatureType.COASTAL_GROTTO, new Color(51, 174, 174).getRGB());
        expected.put(HydrologyFeatureType.INLAND_GROTTO, new Color(116, 76, 150).getRGB());
        expected.put(HydrologyFeatureType.MOUTH, new Color(54, 164, 205).getRGB());
        expected.put(HydrologyFeatureType.DEEP_POOL, new Color(194, 50, 22).getRGB());
        expected.put(HydrologyFeatureType.DEEP_CHANNEL, new Color(194, 50, 22).getRGB());
        expected.put(HydrologyFeatureType.STANDING_POOL, new Color(232, 120, 40).getRGB());

        assertEquals(HydrologyFeatureType.values().length, expected.size());
        for (HydrologyFeatureType type : HydrologyFeatureType.values()) {
            assertEquals(type.name(), expected.get(type).intValue(), IrisRenderer.hydrologyFeatureColor(type));
        }
    }

    @Test
    public void headwatersAndWaterfallsRemainDistinct() {
        HydrologyRenderSample headwater = sample(HydrologyFeatureType.SURFACE_POOL, true);
        HydrologyRenderSample waterfall = sample(HydrologyFeatureType.WATERFALL, false);

        assertEquals(IrisRenderer.headwaterColor(), IrisRenderer.hydrologyColor(headwater));
        assertEquals(IrisRenderer.waterfallColor(), IrisRenderer.hydrologyColor(waterfall));
        assertNotEquals(IrisRenderer.headwaterColor(), IrisRenderer.waterfallColor());
    }

    @Test
    public void acceptedHeadwaterArrowsFollowOppositePlanVectors() {
        BufferedImage east = renderHeadwater(1, 0);
        BufferedImage west = renderHeadwater(-1, 0);
        int direction = IrisRenderer.headwaterDirectionColor();

        assertEquals(direction, east.getRGB(4, 2));
        assertNotEquals(direction, west.getRGB(4, 2));
        assertEquals(direction, west.getRGB(2, 2));
        assertNotEquals(direction, east.getRGB(2, 2));
    }

    @Test
    public void projectedCandidatesUseASeparateDiagnosticPalette() {
        assertNotEquals(
                IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.SURFACE_POOL),
                IrisRenderer.hydrologyDiagnosticColor(HydrologyCandidateKind.SOURCE)
        );
        assertNotEquals(
                IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.MOUTH),
                IrisRenderer.hydrologyDiagnosticColor(HydrologyCandidateKind.OUTLET)
        );
        assertNotEquals(
                IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.DEEP_POOL),
                IrisRenderer.hydrologyDiagnosticColor(HydrologyCandidateKind.DEEP_FLUID)
        );
    }

    @Test
    public void acceptedWaterfallTakesPrecedenceOverAnOverlappingPool() {
        HydrologyFeatureRef pool = feature(1L, HydrologyFeatureType.SURFACE_POOL, false);
        HydrologyFeatureRef waterfall = feature(2L, HydrologyFeatureType.WATERFALL, false);
        HydrologyRenderSample sample = new HydrologyRenderSample(0, 0, List.of(pool, waterfall));

        assertEquals(IrisRenderer.waterfallColor(), IrisRenderer.hydrologyColor(sample));
    }

    @Test
    public void acceptedSinkholeTakesPrecedenceOverItsReceivingGrotto() {
        HydrologyFeatureRef grotto = feature(1L, HydrologyFeatureType.INLAND_GROTTO, false);
        HydrologyFeatureRef sinkhole = feature(2L, HydrologyFeatureType.SINKHOLE, false);
        HydrologyRenderSample sample = new HydrologyRenderSample(0, 0, List.of(grotto, sinkhole));

        assertEquals(IrisRenderer.hydrologyFeatureColor(HydrologyFeatureType.SINKHOLE),
                IrisRenderer.hydrologyColor(sample));
    }

    @Test
    public void riverRenderTypeSamplesOneAcceptedWorldFootprintPerRenderedPixel() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.sampleRenderFootprint(12D, -7D, 20D, 1D))
                .thenReturn(sample(HydrologyFeatureType.SURFACE_POOL, false));

        BufferedImage image = new IrisRenderer(engine).renderStudio(
                12D,
                -7D,
                8D,
                1,
                RenderType.RIVER,
                () -> false
        );

        assertEquals(new Color(48, 112, 190).getRGB(), image.getRGB(0, 0));
        verify(runtime).sampleRenderFootprint(12D, -7D, 20D, 1D);
    }

    @Test
    public void riverVisionShowsAProjectedCandidateWithoutTreatingItAsAccepted() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        HydrologyDiagnosticCandidate candidate = new HydrologyDiagnosticCandidate(
                44L,
                HydrologyCandidateKind.SOURCE,
                HydrologyFeatureType.SURFACE_POOL,
                new HydrologyPoint(0, 80, 0),
                HydrologyCandidateRejection.NO_LEGAL_OUTLET, 0
        );
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.sampleRenderFootprint(0D, 0D, 4D, 4D))
                .thenReturn(new HydrologyRenderSample(0, 0, List.of()));
        when(runtime.sampleDiagnosticFootprint(0D, 0D, 4D, 4D))
                .thenReturn(new HydrologyDiagnosticRenderSample(0, 0, List.of(candidate)));

        BufferedImage image = new IrisRenderer(engine).renderStudio(
                0D,
                0D,
                4D,
                1,
                RenderType.RIVER,
                () -> false
        );

        assertEquals(
                IrisRenderer.hydrologyDiagnosticColor(HydrologyCandidateKind.SOURCE),
                image.getRGB(0, 0)
        );
        assertNotEquals(IrisRenderer.headwaterDirectionColor(), image.getRGB(0, 0));
        verify(runtime).sampleRenderFootprint(0D, 0D, 4D, 4D);
        verify(runtime).sampleDiagnosticFootprint(0D, 0D, 4D, 4D);
    }

    @Test
    public void biomeAtlasCompositesTheSameAcceptedFootprint() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        IrisBiome biome = mock(IrisBiome.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> base = mock(ProceduralStream.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getBaseBiomeStream()).thenReturn(base);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(base.get(0D, 0D)).thenReturn(biome);
        when(biome.getColor(engine, RenderType.BIOME)).thenReturn(Color.GREEN);
        when(runtime.sampleRenderFootprint(0D, 0D, 4D, 4D))
                .thenReturn(sample(HydrologyFeatureType.WATERFALL, false));

        BufferedImage image = new IrisRenderer(engine).renderStudio(
                0D,
                0D,
                4D,
                1,
                RenderType.BIOME,
                () -> false
        );

        assertNotEquals(Color.GREEN.getRGB(), image.getRGB(0, 0));
        verify(base).get(0D, 0D);
        verify(runtime).sampleRenderFootprint(0D, 0D, 4D, 4D);
        verify(runtime, never()).sampleDiagnosticFootprint(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    private static HydrologyRenderSample sample(HydrologyFeatureType type, boolean source) {
        return new HydrologyRenderSample(0, 0, List.of(feature(1L, type, source)));
    }

    private static BufferedImage renderHeadwater(int flowX, int flowZ) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        HydrologyFeatureRef headwater = new HydrologyFeatureRef(
                71L,
                HydrologyFeatureType.SURFACE_POOL,
                10L,
                20L,
                3,
                64,
                3,
                flowX,
                flowZ,
                true
        );
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.sampleRenderFootprint(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double minimumX = invocation.getArgument(0, Double.class);
                    double minimumZ = invocation.getArgument(1, Double.class);
                    double maximumX = invocation.getArgument(2, Double.class);
                    double maximumZ = invocation.getArgument(3, Double.class);
                    boolean contains = minimumX <= headwater.x() && maximumX > headwater.x()
                            && minimumZ <= headwater.z() && maximumZ > headwater.z();
                    return new HydrologyRenderSample(
                            (int) StrictMath.floor(minimumX),
                            (int) StrictMath.floor(minimumZ),
                            contains ? List.of(headwater) : List.of()
                    );
                });

        return new IrisRenderer(engine).renderStudio(
                0D,
                0D,
                7D,
                7,
                RenderType.RIVER,
                () -> false
        );
    }

    private static HydrologyFeatureRef feature(long id, HydrologyFeatureType type, boolean source) {
        return new HydrologyFeatureRef(id, type, 10L, 20L, 0, 64, 0, 1, 0, source);
    }
}
