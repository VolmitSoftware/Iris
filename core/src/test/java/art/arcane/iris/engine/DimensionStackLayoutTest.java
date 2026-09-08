package art.arcane.iris.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class DimensionStackLayoutTest {
    @Test
    public void spacerCountsExactAirBlocksBetweenInclusiveLayers() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                8,
                List.of(input(15, 15), input(15, 15), input(15, 15)),
                new int[]{0, 0}
        );

        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        assertEquals(0, layers.get(0).localBaseY());
        assertEquals(15, layers.get(0).surfaceY());
        assertEquals(24, layers.get(1).localBaseY());
        assertEquals(39, layers.get(1).surfaceY());
        assertEquals(48, layers.get(2).localBaseY());
        assertEquals(63, layers.get(2).surfaceY());
        assertEquals(8, layers.get(1).localBaseY() - layers.get(0).contentTopY() - 1);
        assertEquals(8, layers.get(2).localBaseY() - layers.get(1).contentTopY() - 1);
        assertEquals(63, layout.stackTerrainTopY());
        assertEquals(63, layout.stackTopY());
    }

    @Test
    public void independentlySignedSeamOffsetsShiftOnlyTheirUpperLayer() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                4,
                List.of(input(10, 7), input(10, 7), input(10, 7)),
                new int[]{3, -2}
        );

        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        assertEquals(0, layers.get(0).localBaseY());
        assertEquals(18, layers.get(1).localBaseY());
        assertEquals(31, layers.get(2).localBaseY());
        assertEquals(3, layers.get(1).seamOffsetBelow());
        assertEquals(-2, layers.get(2).seamOffsetBelow());
        assertEquals(41, layout.stackTerrainTopY());
        assertEquals(41, layout.stackTopY());
    }

    @Test
    public void renderingBoundsClipWithoutChangingRawLayout() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                32,
                8,
                List.of(input(15, 15), input(15, 15), input(15, 15)),
                new int[]{0, 0}
        );

        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        DimensionStackLayout.Layer middle = layers.get(1);
        DimensionStackLayout.Layer top = layers.get(2);
        assertEquals(39, middle.contentTopY());
        assertEquals(24, middle.renderMinY());
        assertEquals(31, middle.renderMaxY());
        assertEquals(31, middle.clippedSurfaceY());
        assertTrue(middle.visible());
        assertTrue(middle.containsRenderedY(31));
        assertFalse(middle.containsRenderedY(32));
        assertTrue(layout.containsUpperLayerY(31));
        assertFalse(layout.containsUpperLayerY(23));
        assertEquals(48, top.localBaseY());
        assertFalse(top.visible());
        assertFalse(top.containsRenderedY(31));
        assertEquals(63, layout.stackTerrainTopY());
        assertEquals(63, layout.stackTopY());
        assertEquals(31, layout.clippedStackTerrainTopY());
        assertEquals(31, layout.clippedStackTopY());
    }

    @Test
    public void invisibleLayersDoNotRaiseRenderedHeightToTheCeiling() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                64,
                List.of(input(15, 15), input(15, 15)),
                new int[]{0}
        );

        assertFalse(layout.layersBottomToTop().get(1).visible());
        assertEquals(15, layout.clippedStackTerrainTopY());
        assertEquals(15, layout.clippedStackTopY());
    }

    @Test
    public void layerLookupReturnsRenderedOwnerAndBottomOwnerForAir() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                8,
                List.of(input(15, 15), input(15, 15)),
                new int[]{0}
        );

        assertSame(layout.layersBottomToTop().get(0), layout.layerAt(8));
        assertSame(layout.layersBottomToTop().get(0), layout.layerAt(20));
        assertSame(layout.layersBottomToTop().get(1), layout.layerAt(24));
    }

    @Test
    public void laterGapsEraseEarlierUpperLayerOwnership() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                128,
                0,
                List.of(input(100, 100), input(60, 60), input(10, 10), input(10, 10)),
                new int[]{-81, -41, 39}
        );

        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        assertSame(layers.get(1), layout.layerAt(30));
        assertSame(layers.get(2), layout.layerAt(45));
        assertSame(layers.get(0), layout.layerAt(60));
        assertSame(layers.get(3), layout.layerAt(95));
    }

    @Test
    public void hostFeaturesCannotFillStackGapsOrOverwriteUpperLayers() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                8,
                List.of(input(15, 15), input(15, 15)),
                new int[]{0}
        );

        assertFalse(layout.isHostFeatureProtectedY(15));
        assertTrue(layout.isHostFeatureProtectedY(20));
        assertTrue(layout.isHostFeatureProtectedY(24));
    }

    @Test
    public void fluidExtendsContentWithoutChangingTerrainTop() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                2,
                List.of(input(5, 9), input(4, 7)),
                new int[]{0}
        );

        List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
        assertEquals(9, layers.get(0).contentTopY());
        assertEquals(12, layers.get(1).localBaseY());
        assertEquals(16, layers.get(1).surfaceY());
        assertEquals(19, layers.get(1).contentTopY());
        assertEquals(16, layout.stackTerrainTopY());
        assertEquals(19, layout.stackTopY());
    }

    @Test
    public void physicallyHighestTerrainLayerWinsWhenBlendEmbedsAnUpperLayer() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                0,
                List.of(input(20, 20), input(5, 5)),
                new int[]{-15}
        );

        assertSame(layout.layersBottomToTop().get(0), layout.topTerrainLayer());
    }

    @Test
    public void declaredUpperLayerWinsWhenRenderedSurfacesTie() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                0,
                List.of(input(10, 10), input(5, 5)),
                new int[]{-6}
        );

        assertSame(layout.layersBottomToTop().get(1), layout.topTerrainLayer());
    }

    @Test
    public void upperFluidOccludesAPhysicallyHigherLowerSurface() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                0,
                List.of(input(20, 20), input(5, 15)),
                new int[]{-11}
        );

        assertEquals(15, layout.clippedStackTerrainTopY());
        assertEquals(25, layout.clippedStackTopY());
        assertSame(layout.layersBottomToTop().get(1), layout.topTerrainLayer());
    }

    @Test
    public void laterGapErasesProtrudingTerrainWhenItsUpperLayerIsClipped() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                128,
                0,
                List.of(input(100, 100), input(10, 10), input(10, 10)),
                new int[]{-51, 89}
        );

        assertEquals(50, layout.layersBottomToTop().get(1).localBaseY());
        assertEquals(150, layout.layersBottomToTop().get(2).localBaseY());
        assertFalse(layout.layersBottomToTop().get(2).visible());
        assertEquals(60, layout.clippedStackTerrainTopY());
        assertEquals(60, layout.clippedStackTopY());
        assertSame(layout.layersBottomToTop().get(1), layout.topTerrainLayer());
    }

    @Test
    public void visibleFluidOwnsSurfaceQueriesWhenAllTerrainIsOccludedBelowTheFloor() {
        DimensionStackLayout layout = DimensionStackLayout.create(
                64,
                0,
                List.of(input(5, 5), input(2, 30)),
                new int[]{-20}
        );

        assertEquals(-14, layout.layersBottomToTop().get(1).localBaseY());
        assertEquals(0, layout.clippedStackTerrainTopY());
        assertEquals(16, layout.clippedStackTopY());
        assertNull(layout.topTerrainLayer());
        assertSame(layout.layersBottomToTop().get(1), layout.surfaceLayer());
    }

    @Test
    public void layoutListsAreImmutableAndExposeBothOrders() {
        ArrayList<DimensionStackLayout.LayerInput> inputs = new ArrayList<>();
        DimensionStackLayout.LayerInput bottom = input(3, 3);
        DimensionStackLayout.LayerInput top = input(4, 4);
        inputs.add(bottom);
        inputs.add(top);
        DimensionStackLayout layout = DimensionStackLayout.create(32, 1, inputs, new int[]{0});
        inputs.clear();

        assertEquals(2, layout.layersBottomToTop().size());
        assertEquals(
                layout.layersBottomToTop().get(0),
                layout.layersTopToBottom().get(1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> layout.layersBottomToTop().clear()
        );
    }

    @Test
    public void seamSaltsAreStableAndBoundarySpecific() {
        long first = DimensionStackContext.seamSalt(0, "root", "middle");
        long repeat = DimensionStackContext.seamSalt(0, "root", "middle");
        long differentIndex = DimensionStackContext.seamSalt(1, "root", "middle");
        long differentUpper = DimensionStackContext.seamSalt(0, "root", "top");

        assertEquals(first, repeat);
        assertNotEquals(first, differentIndex);
        assertNotEquals(first, differentUpper);
    }

    @Test
    public void seamOffsetsClampUnboundedAndNonFiniteNoise() {
        assertEquals(4, DimensionStackContext.clampSeamOffset(12D, 4));
        assertEquals(-4, DimensionStackContext.clampSeamOffset(-12D, 4));
        assertEquals(2, DimensionStackContext.clampSeamOffset(1.6D, 4));
        assertEquals(0, DimensionStackContext.clampSeamOffset(Double.NaN, 4));
        assertEquals(0, DimensionStackContext.clampSeamOffset(Double.POSITIVE_INFINITY, 4));
    }

    @Test
    public void queryLayoutsCacheStableColumnsAndBypassTemporaryNaturalFallbacks() {
        Cache<Long, DimensionStackLayout> cache = Caffeine.newBuilder().maximumSize(4).build();
        DimensionStackLayout stable = DimensionStackLayout.create(
                32, 0, List.of(input(10, 10), input(10, 10)), new int[]{0});
        DimensionStackLayout unusedStable = DimensionStackLayout.create(
                32, 0, List.of(input(11, 11), input(10, 10)), new int[]{0});
        AtomicInteger stableSamples = new AtomicInteger();

        DimensionStackLayout first = DimensionStackContext.resolveLayout(
                cache, 7L, false, () -> {
                    stableSamples.incrementAndGet();
                    return stable;
                });
        DimensionStackLayout second = DimensionStackContext.resolveLayout(
                cache, 7L, false, () -> {
                    stableSamples.incrementAndGet();
                    return unusedStable;
                });

        assertSame(stable, first);
        assertSame(stable, second);
        assertEquals(1, stableSamples.get());

        DimensionStackLayout naturalFirst = DimensionStackLayout.create(
                32, 0, List.of(input(12, 12), input(10, 10)), new int[]{0});
        DimensionStackLayout naturalSecond = DimensionStackLayout.create(
                32, 0, List.of(input(13, 13), input(10, 10)), new int[]{0});
        AtomicInteger naturalSamples = new AtomicInteger();

        assertSame(naturalFirst, DimensionStackContext.resolveLayout(
                cache, 7L, true, () -> {
                    naturalSamples.incrementAndGet();
                    return naturalFirst;
                }));
        assertSame(naturalSecond, DimensionStackContext.resolveLayout(
                cache, 7L, true, () -> {
                    naturalSamples.incrementAndGet();
                    return naturalSecond;
                }));
        assertEquals(2, naturalSamples.get());
        assertSame(stable, cache.getIfPresent(7L));
    }

    private static DimensionStackLayout.LayerInput input(int terrainHeight, int fluidHeight) {
        return new DimensionStackLayout.LayerInput(
                mock(DimensionTerrainContext.class),
                null,
                null,
                null,
                null,
                null,
                terrainHeight,
                fluidHeight,
                null
        );
    }
}
