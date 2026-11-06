package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.InferredType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class MapPickPathTest {
    private static final double X = 12D;
    private static final double Z = -7D;

    private final PickedWorld world = new PickedWorld();

    @Test
    public void theBiomeViewWalksFromTheRegionThroughItsListToTheChildThatKeptTheSpot() {
        List<MapPickPath.PickStep> path = explain(MapLayer.of(MapView.BIOMES));

        assertEquals(List.of(MapPickPath.Kind.REGION, MapPickPath.Kind.BIOME, MapPickPath.Kind.CHILD, MapPickPath.Kind.CHILD),
                kinds(path));
        MapPickPath.PickStep region = path.get(0);
        assertEquals("dry", region.picked().key());
        assertEquals(0.9D, region.noise(), 0D);
        assertBand(region.bands().get(0), "warm", 0D, 0.75D);
        assertBand(region.bands().get(1), "dry", 0.75D, 1D);
        assertEquals("land", path.get(1).list());
        assertEquals("plains", path.get(1).picked().key());
        assertEquals("meadow", path.get(2).picked().key());
        assertEquals("Plains", path.get(2).replaced());
        assertEquals("meadow", path.get(3).picked().key());
        assertNull("meadow kept the spot", path.get(3).replaced());
    }

    @Test
    public void aChildStepJoinsNeighbouringSlotsOfOneBiome() {
        List<MapPickPath.PickBand> bands = explain(MapLayer.of(MapView.BIOMES)).get(2).bands();

        assertEquals(2, bands.size());
        assertBand(bands.get(0), "meadow", 0D, 0.25D);
        assertBand(bands.get(1), "plains", 0.25D, 1D);
    }

    @Test
    public void theLandViewStopsAtTheListPickBecauseItsStreamHasNoChildren() {
        assertEquals(List.of(MapPickPath.Kind.REGION, MapPickPath.Kind.BIOME), kinds(explain(MapLayer.of(MapView.LAND_BIOMES))));
    }

    @Test
    public void anImageMappedRegionWasChosenNotPicked() {
        when(world.imageMap.sampleRegion(X, Z)).thenReturn(world.dry);

        MapPickPath.PickStep region = explain(MapLayer.of(MapView.REGIONS)).getFirst();

        assertEquals(MapPickPath.Kind.IMAGE_MAP, region.kind());
        assertNull(region.noise());
        assertEquals(List.of(), region.bands());
    }

    @Test
    public void aPreviewedRegionOwnsTheSpotAndPicksFromTheListItsSurfaceNeeds() {
        when(world.complex.surfacePickAsIfIn(world.dry, X, Z)).thenReturn(new IrisComplex.BiomePick(world.meadow, InferredType.LAND));

        List<MapPickPath.PickStep> path = explain(MapLayer.preview(world.dry));

        assertEquals(List.of(MapPickPath.Kind.PREVIEW, MapPickPath.Kind.BIOME, MapPickPath.Kind.CHILD, MapPickPath.Kind.CHILD),
                kinds(path));
        assertEquals("dry", path.get(0).picked().key());
        assertEquals("land", path.get(1).list());
    }

    @Test
    public void slotsRoundTheWayTheChildNoiseDoes() {
        assertEquals(0, ChildPicks.slotIndex(0.24D, 3));
        assertEquals(1, ChildPicks.slotIndex(0.26D, 3));
        assertEquals(2, ChildPicks.slotIndex(1.4D, 3));
        assertEquals(0, ChildPicks.slotIndex(0.9D, 1));
    }

    private List<MapPickPath.PickStep> explain(MapLayer layer) {
        return MapPickPath.explain(world.engine, layer, X, Z);
    }

    private static List<MapPickPath.Kind> kinds(List<MapPickPath.PickStep> path) {
        return path.stream().map(MapPickPath.PickStep::kind).toList();
    }

    static void assertBand(MapPickPath.PickBand band, String key, double from, double to) {
        assertEquals(key, band.option().key());
        assertEquals(from, band.from(), 1e-9);
        assertEquals(to, band.to(), 1e-9);
    }
}
