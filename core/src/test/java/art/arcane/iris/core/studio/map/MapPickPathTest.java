package art.arcane.iris.core.studio.map;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapPickPathTest {
    private static final double X = 12D;
    private static final double Z = -7D;

    private final Engine engine = mock(Engine.class);
    private final IrisComplex complex = mock(IrisComplex.class);
    private final IrisBiome plains = biome("plains", "Plains", 1);
    private final IrisBiome forest = biome("forest", "Forest", 1);
    private final IrisBiome meadow = biome("meadow", "Meadow", 1);
    private final IrisBiome glade = biome("glade", "Glade", 1);
    private IrisRegion dry;

    /**
     * A dry region (rarity 3, so the last quarter of the region noise) picked at 0.9; its land list picks plains at 0.2;
     * plains' child noise at 0.1 rounds to its first slot, meadow; meadow's at 0.8 rounds to its last slot, itself.
     */
    @Before
    public void pickTheWayDown() {
        IrisRegion warm = region("warm", "Warm", 1);
        dry = region("dry", "Dry", 3);
        when(engine.getComplex()).thenReturn(complex);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getRegions()).thenReturn(new KList<>(List.of("warm", "dry")));
        when(engine.getDimension()).thenReturn(dimension);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        when(regionLoader.loadAll(any(KList.class))).thenReturn(new KList<>(List.of(warm, dry)));
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(engine.getData()).thenReturn(data);
        IrisImageMapRuntime noImageMap = mock(IrisImageMapRuntime.class);
        when(complex.getImageMapRuntime()).thenReturn(noImageMap);

        ProceduralStream<IrisRegion> regions = stream(dry);
        ProceduralStream<Double> regionNoise = stream(0.9D);
        ProceduralStream<InferredType> continents = stream(InferredType.LAND);
        ProceduralStream<Double> landNoise = stream(0.2D);
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getRegionStyleStream()).thenReturn(regionNoise);
        when(complex.getBridgeStream()).thenReturn(continents);
        when(complex.pickableBiomes(dry, InferredType.LAND)).thenReturn(new KList<>(List.of(plains, forest)));
        when(complex.biomePickNoise(dry, InferredType.LAND)).thenReturn(landNoise);
        children(plains, List.of(meadow, plains, plains), 0.1D);
        children(meadow, List.of(glade, meadow), 0.8D);
    }

    @Test
    public void theBiomeViewWalksFromTheRegionThroughItsListToTheChildThatKeptTheSpot() {
        List<MapPickPath.PickStep> path = MapPickPath.explain(engine, MapView.BIOMES, X, Z);

        assertEquals(List.of(MapPickPath.Kind.REGION, MapPickPath.Kind.BIOME, MapPickPath.Kind.CHILD, MapPickPath.Kind.CHILD),
                path.stream().map(MapPickPath.PickStep::kind).toList());
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
        List<MapPickPath.PickBand> bands = MapPickPath.explain(engine, MapView.BIOMES, X, Z).get(2).bands();

        assertEquals(2, bands.size());
        assertBand(bands.get(0), "meadow", 0D, 0.25D);
        assertBand(bands.get(1), "plains", 0.25D, 1D);
    }

    @Test
    public void theLandViewStopsAtTheListPickBecauseItsStreamHasNoChildren() {
        List<MapPickPath.PickStep> path = MapPickPath.explain(engine, MapView.LAND_BIOMES, X, Z);

        assertEquals(List.of(MapPickPath.Kind.REGION, MapPickPath.Kind.BIOME), path.stream().map(MapPickPath.PickStep::kind).toList());
    }

    @Test
    public void anImageMappedRegionWasChosenNotPicked() {
        IrisImageMapRuntime imageMap = complex.getImageMapRuntime();
        when(imageMap.sampleRegion(X, Z)).thenReturn(dry);

        MapPickPath.PickStep region = MapPickPath.explain(engine, MapView.REGIONS, X, Z).getFirst();

        assertEquals(MapPickPath.Kind.IMAGE_MAP, region.kind());
        assertNull(region.noise());
        assertEquals(List.of(), region.bands());
    }

    @Test
    public void slotsRoundTheWayTheChildNoiseDoes() {
        assertEquals(0, MapPickPath.slotIndex(0.24D, 3));
        assertEquals(1, MapPickPath.slotIndex(0.26D, 3));
        assertEquals(2, MapPickPath.slotIndex(1.4D, 3));
        assertEquals(0, MapPickPath.slotIndex(0.9D, 1));
    }

    private static void assertBand(MapPickPath.PickBand band, String key, double from, double to) {
        assertEquals(key, band.option().key());
        assertEquals(from, band.from(), 1e-9);
        assertEquals(to, band.to(), 1e-9);
    }

    private void children(IrisBiome parent, List<IrisBiome> slots, double noise) {
        when(parent.getChildren()).thenReturn(new KList<>(List.of("child")));
        when(complex.childSlots(parent)).thenReturn(slots);
        CNG childNoise = mock(CNG.class);
        when(childNoise.noiseFast2D(X, Z)).thenReturn(noise);
        when(complex.childNoise(parent)).thenReturn(childNoise);
    }

    private IrisBiome biome(String key, String name, int rarity) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        when(biome.getName()).thenReturn(name);
        when(biome.getRarity()).thenReturn(rarity);
        when(biome.getChildren()).thenReturn(new KList<>());
        when(biome.getInferredType()).thenReturn(InferredType.LAND);
        when(biome.withInferredType(any(InferredType.class))).thenReturn(biome);
        when(biome.getColor(eq(engine), any(RenderType.class))).thenReturn(Color.GREEN);
        return biome;
    }

    private IrisRegion region(String key, String name, int rarity) {
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn(key);
        when(region.getName()).thenReturn(name);
        when(region.getRarity()).thenReturn(rarity);
        when(region.getColor(complex, RenderType.REGION)).thenReturn(Color.ORANGE);
        return region;
    }

    @SuppressWarnings("unchecked")
    private static <T> ProceduralStream<T> stream(T value) {
        ProceduralStream<T> stream = mock(ProceduralStream.class);
        when(stream.get(anyDouble(), anyDouble())).thenReturn(value);
        return stream;
    }
}
