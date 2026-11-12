package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.InferredType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class MapPickInspectionTest {
    private static final MapPickInspection.Area AREA = new MapPickInspection.Area(-100D, -100D, 100D, 100D);
    private static final long SPOTS = (long) MapPickInspection.SAMPLES_PER_SIDE * MapPickInspection.SAMPLES_PER_SIDE;

    private final PickedWorld world = new PickedWorld();

    @Test
    public void theRegionViewMeasuresTheRegionNoiseAgainstEachRegionsShare() {
        MapPickInspection.Inspection inspection = measure(MapLayer.of(MapView.REGIONS));

        MapPickInspection.Section section = inspection.sections().getFirst();
        assertNull("the regions themselves are picked, not one region's biomes", section.region());
        MapPickInspection.Spread regions = section.spreads().getFirst();
        assertEquals(SPOTS, regions.samples());
        assertEquals("every spot reads 0.9", SPOTS, regions.histogram()[45]);
        assertRow(regions.rows().get(0), "warm", 0.75D, 0D);
        assertRow(regions.rows().get(1), "dry", 0.25D, 1D);
    }

    @Test
    public void theBiomeViewMeasuresEachRegionsListAndEveryChildNoiseItsSpotsReach() {
        MapPickInspection.Inspection inspection = measure(MapLayer.of(MapView.BIOMES));

        assertEquals(1, inspection.sections().size());
        MapPickInspection.Section dry = inspection.sections().getFirst();
        assertEquals("dry", dry.region().key());
        assertEquals(1D, dry.share(), 0D);
        assertEquals("plains, forest, meadow and glade", 4, dry.biomeCount());
        assertEquals(List.of("Land biome noise", "Plains child noise", "Meadow child noise"),
                dry.spreads().stream().map(MapPickInspection.Spread::title).toList());
        assertRow(dry.spreads().get(0).rows().get(0), "plains", 0.5D, 1D);
        MapPickInspection.Spread plainsChildren = dry.spreads().get(1);
        assertEquals("meadow and plains are two bands", 2, plainsChildren.rows().size());
        assertRow(plainsChildren.rows().get(0), "meadow", 0.25D, 1D);
    }

    @Test
    public void aPreviewMeasuresThePreviewedRegionsListsEverywhere() {
        IrisComplex complex = world.complex;
        var landBiomes = complex.pickableBiomes(world.dry, InferredType.LAND);
        var landNoise = complex.biomePickNoise(world.dry, InferredType.LAND);
        var landPicks = complex.biomePicks(world.dry, InferredType.LAND);
        when(complex.surfacePickAsIfIn(eq(world.warm), anyDouble(), anyDouble()))
                .thenReturn(new IrisComplex.BiomePick(world.meadow, InferredType.LAND));
        when(complex.pickableBiomes(world.warm, InferredType.LAND)).thenReturn(landBiomes);
        when(complex.biomePickNoise(world.warm, InferredType.LAND)).thenReturn(landNoise);
        when(complex.biomePicks(world.warm, InferredType.LAND)).thenReturn(landPicks);

        MapPickInspection.Section section = measure(MapLayer.preview(world.warm)).sections().getFirst();

        assertEquals("warm", section.region().key());
        assertEquals(SPOTS, section.spreads().getFirst().samples());
    }

    @Test
    public void anAreaNeedsItsSecondCornerBeyondItsFirst() {
        assertThrows(IllegalArgumentException.class, () -> new MapPickInspection.Area(0D, 0D, 0D, 10D));
    }

    @Test
    public void theBiomeNoiseSettlesAtTheLastChildPick() {
        TileGrid grid = new TileGrid(2, 0D, 0D, 1D, () -> false);

        float[] noise = MapPickNoise.sample(world.engine, MapLayer.of(MapView.BIOMES), grid);

        assertEquals("meadow kept the spot at 0.8", 0.8F, noise[3], 1e-6F);
    }

    private MapPickInspection.Inspection measure(MapLayer layer) {
        return MapPickInspection.measure(world.engine, layer, AREA);
    }

    private static void assertRow(MapPickInspection.Row row, String key, double weight, double inView) {
        assertEquals(key, row.option().key());
        assertEquals(weight, row.weight(), 1e-9);
        assertEquals(inView, row.inView(), 1e-9);
    }
}
