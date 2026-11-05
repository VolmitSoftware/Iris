package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A world split at x = 0: plains in the warm region west of it, desert in the dry region east of it. */
public class MapTileRendererTest {
    private static final int SIZE = 8;

    private final Engine engine = mock(Engine.class);
    private final IrisComplex complex = mock(IrisComplex.class);
    private final IrisBiome plains = biome("plains", "Plains", Color.GREEN);
    private final IrisBiome desert = biome("desert", "Desert", Color.YELLOW);
    private final IrisBiome cavern = biome("cavern", "Cavern", Color.DARK_GRAY);
    private IrisRegion warm;
    private IrisRegion dry;

    @Before
    public void splitTheWorld() {
        warm = region("warm", "Warm", Color.RED);
        dry = region("dry", "Dry", Color.ORANGE);
        when(engine.getComplex()).thenReturn(complex);
        ProceduralStream<IrisBiome> base = stream((x, z) -> x < 0 ? plains : desert);
        ProceduralStream<IrisRegion> regions = stream((x, z) -> x < 0 ? warm : dry);
        ProceduralStream<IrisBiome> caves = stream((x, z) -> cavern);
        when(complex.getBaseBiomeStream()).thenReturn(base);
        when(complex.getRegionStream()).thenReturn(regions);
        when(complex.getCaveBiomeStream()).thenReturn(caves);
        heights((x, z) -> 64D + Math.sin(x / 7D) * 20D + Math.cos(z / 5D) * 10D);
    }

    @Test
    public void eachCellNamesItsBiomeAndRegionAndEachLabelIsListedOnce() {
        MapTile tile = render(MapView.BIOMES, -4D, 0D, 1D, false);

        assertEquals(MapLabel.Kind.BIOME, tile.subjectKind());
        assertEquals("plains", tile.subjectAt(0, 0).key());
        assertEquals("desert", tile.subjectAt(4, 0).key());
        assertEquals("warm", tile.regionAt(3, 7).key());
        assertEquals("dry", tile.regionAt(7, 7).key());
        assertEquals(List.of("warm", "dry", "plains", "desert"), tile.labels().stream().map(MapLabel::key).toList());
        assertEquals(Color.GREEN.getRGB() & 0xFFFFFF, tile.subjectAt(0, 0).color());
        assertNull("heights only when asked", tile.heights());
    }

    @Test
    public void theRingSamplesTheNeighbouringTilesEdgePixels() {
        MapTile tile = render(MapView.BIOMES, 0D, 0D, 2D, false);

        assertEquals("the pixel left of the tile is at x = -2", "plains", tile.subjectAt(-1, 3).key());
        assertEquals("desert", tile.subjectAt(0, 3).key());
    }

    @Test
    public void theRegionViewLabelsEachSpotWithItsRegion() {
        MapTile tile = render(MapView.REGIONS, -4D, 0D, 1D, false);

        assertEquals(MapLabel.Kind.REGION, tile.subjectKind());
        assertSame(tile.regions(), tile.subjects());
        assertEquals("warm", tile.subjectAt(0, 0).key());
        assertEquals(Color.RED.getRGB() & 0xFFFFFF, tile.subjectAt(0, 0).color());
    }

    @Test
    public void biomeLayersLabelWithTheirOwnStreamAndColouring() {
        MapTile tile = render(MapView.CAVE_BIOMES, -4D, 0D, 1D, false);

        assertEquals("cavern", tile.subjectAt(0, 0).key());
        assertEquals("warm", tile.regionAt(0, 0).key());
    }

    @Test
    public void theHeightViewAlwaysHasHeightsAndAFlatWorldStaysFlat() {
        heights((x, z) -> 70D);

        MapTile tile = render(MapView.HEIGHT, 0D, 0D, 1D, false);

        for (float height : tile.heights()) {
            assertEquals(70F, height, 1e-4F);
        }
    }

    @Test
    public void heightsOnALatticePointAreExactAndAgreeAcrossTiles() {
        MapTile west = render(MapView.BIOMES, 0D, 0D, 3D, true);
        MapTile east = render(MapView.BIOMES, SIZE * 3D, 0D, 3D, true);
        ProceduralStream<Double> exact = complex.getHeightStream();

        assertEquals(exact.getDouble(12D, 12D), west.heights()[west.gridIndex(4, 4)], 1e-4);
        for (int pixelZ = -1; pixelZ <= SIZE; pixelZ++) {
            assertEquals(east.heights()[east.gridIndex(0, pixelZ)], west.heights()[west.gridIndex(SIZE, pixelZ)], 1e-4F);
            assertEquals(west.heights()[west.gridIndex(SIZE - 1, pixelZ)], east.heights()[east.gridIndex(-1, pixelZ)], 1e-4F);
        }
    }

    @Test
    public void aCancelledTileStopsBeforeSamplingTheEngine() {
        Engine untouched = mock(Engine.class);

        assertThrows(CancellationException.class,
                () -> new MapTileRenderer(untouched).render(MapView.BIOMES, 0D, 0D, 1D, SIZE, false, () -> true));
        verifyNoInteractions(untouched);
    }

    @Test
    public void theHoverDescribesTheColumn() {
        PlatformBiome vanilla = mock(PlatformBiome.class);
        when(vanilla.key()).thenReturn("minecraft:desert");
        ProceduralStream<PlatformBiome> vanillaBiomes = stream((x, z) -> vanilla);
        when(complex.getTrueBiomeDerivativeStream()).thenReturn(vanillaBiomes);
        ProceduralStream<Double> slopes = numbers((x, z) -> 3D);
        when(complex.getSlopeStream()).thenReturn(slopes);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getFluidHeight()).thenReturn(63);
        when(engine.getDimension()).thenReturn(dimension);

        MapHover hover = MapHover.sample(engine, 10D, 0D);

        assertEquals("desert", hover.biome().key());
        assertEquals("cavern", hover.caveBiome().key());
        assertEquals("dry", hover.region().key());
        assertEquals("minecraft:desert", hover.vanillaBiome());
        assertEquals(complex.getHeightStream().getDouble(10D, 0D), hover.height(), 1e-9);
        assertEquals(63D, hover.fluidHeight(), 0D);
        assertEquals(45D, hover.slopeDegrees(), 1e-9);
    }

    @Test
    public void viewsAreFoundByTheirIds() {
        for (MapView view : MapView.values()) {
            assertEquals(Optional.of(view), MapView.byId(view.id()));
        }
        assertEquals(Optional.empty(), MapView.byId("nothing"));
    }

    private MapTile render(MapView view, double startX, double startZ, double blocksPerPixel, boolean withHeights) {
        return new MapTileRenderer(engine).render(view, startX, startZ, blocksPerPixel, SIZE, withHeights, () -> false);
    }

    private void heights(DoubleBinaryOperator height) {
        ProceduralStream<Double> stream = numbers(height);
        when(complex.getHeightStream()).thenReturn(stream);
    }

    private IrisBiome biome(String key, String name, Color color) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getLoadKey()).thenReturn(key);
        when(biome.getName()).thenReturn(name);
        when(biome.getColor(org.mockito.ArgumentMatchers.eq(engine), org.mockito.ArgumentMatchers.any(RenderType.class))).thenReturn(color);
        return biome;
    }

    private IrisRegion region(String key, String name, Color color) {
        IrisRegion region = mock(IrisRegion.class);
        when(region.getLoadKey()).thenReturn(key);
        when(region.getName()).thenReturn(name);
        when(region.getColor(complex, RenderType.REGION)).thenReturn(color);
        return region;
    }

    @SuppressWarnings("unchecked")
    private static <T> ProceduralStream<T> stream(Sampler<T> sampler) {
        ProceduralStream<T> stream = mock(ProceduralStream.class);
        when(stream.get(anyDouble(), anyDouble())).thenAnswer(call -> sampler.at(call.getArgument(0), call.getArgument(1)));
        return stream;
    }

    @SuppressWarnings("unchecked")
    private static ProceduralStream<Double> numbers(DoubleBinaryOperator sampler) {
        ProceduralStream<Double> stream = mock(ProceduralStream.class);
        when(stream.getDouble(anyDouble(), anyDouble())).thenAnswer(call -> sampler.applyAsDouble(call.getArgument(0), call.getArgument(1)));
        return stream;
    }

    @FunctionalInterface
    private interface Sampler<T> {
        T at(double x, double z);
    }
}
