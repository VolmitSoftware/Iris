package art.arcane.iris.studio.map;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanPreview;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.map.components.MapLayer;
import art.arcane.iris.studio.map.components.MapStyle;
import art.arcane.iris.studio.map.components.MapTile;
import art.arcane.iris.studio.map.components.MapTileRenderer;
import art.arcane.iris.studio.map.components.PickDisplay;
import art.arcane.iris.studio.map.components.TileType;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapTileProgressTest {
    @Test
    public void retriesOnlySampleUnresolvedHeightPointsAndReuseBiomeData() {
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicInteger heightSamples = new AtomicInteger();
        AtomicInteger biomeSamples = new AtomicInteger();
        Engine engine = engine(biomeSamples);
        when(engine.getComplex().getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> {
            heightSamples.incrementAndGet();
            if (x == 0D && z == 0D && pending.get()) {
                ProvisionalSampling.mark();
                return 60D;
            }
            return 80D + x * 0.02D + z * 0.01D;
        }));
        MapTileRenderer renderer = new MapTileRenderer(engine);
        MapLayer layer = MapLayer.biomes(PickDisplay.PICKS, false);
        MapTileRenderer.TileRender progress = renderer.prepareTile(-8D, -8D, 0.5D, 16, layer, true);

        progress.render(() -> false);
        int initialHeights = heightSamples.get();
        int initialBiomes = biomeSamples.get();
        assertFalse(progress.complete());
        assertTrue(progress.bytes() > 0L);

        progress.render(() -> false);
        assertEquals(initialHeights + 1, heightSamples.get());
        assertEquals(initialBiomes, biomeSamples.get());
        pending.set(false);
        MapTile settled = progress.render(() -> false);
        assertTrue(progress.complete());
        assertEquals(initialHeights + 2, heightSamples.get());
        assertEquals(initialBiomes, biomeSamples.get());
        assertEquals(0L, progress.bytes());
        progress.render(() -> false);
        assertEquals(initialHeights + 2, heightSamples.get());
        assertEquals(initialBiomes, biomeSamples.get());

        MapTile expected = renderer.renderTile(-8D, -8D, 0.5D, 16, layer, true, () -> false);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(expected.heightAt(x, z), settled.heightAt(x, z), 0D);
            }
        }
        assertArrayEquals(pixels(expected.image(MapStyle.PLAIN.withShading(true))),
                pixels(settled.image(MapStyle.PLAIN.withShading(true))));
    }

    @Test
    public void completedWaterIsNotBlendedAgainWhileHeightsWait() {
        AtomicBoolean pending = new AtomicBoolean(true);
        Engine engine = engine(new AtomicInteger());
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(raster);
        when(raster.role(anyInt(), anyInt())).thenReturn(WaterRole.POND);
        when(engine.getComplex().getHydrologyRuntime()).thenReturn(runtime);
        when(engine.getComplex().getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> {
            if (pending.get()) {
                ProvisionalSampling.mark();
            }
            return 60D;
        }));
        MapTileRenderer renderer = new MapTileRenderer(engine);
        MapTileRenderer.TileRender progress = renderer.prepareTile(0D, 0D, 1D, 16, MapLayer.of(TileType.BIOME), true);

        MapTile first = progress.render(() -> false);
        assertFalse(progress.complete());
        assertNotEquals(0xFF738249, first.image(MapStyle.PLAIN).getRGB(0, 0));
        MapTile second = progress.render(() -> false);
        assertArrayEquals(pixels(first.image(MapStyle.PLAIN)), pixels(second.image(MapStyle.PLAIN)));

        pending.set(false);
        MapTile complete = progress.render(() -> false);
        assertTrue(progress.complete());
        assertEquals(0L, progress.bytes());
        assertArrayEquals(pixels(first.image(MapStyle.PLAIN)), pixels(complete.image(MapStyle.PLAIN)));
    }

    @Test
    public void twoBlockPixelsRefineMixedWaterAndHeightsToTheSameFinalTile() {
        AtomicBoolean pending = new AtomicBoolean(true);
        Engine engine = engine(new AtomicInteger());
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.planPreview(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(HydrologyPlanPreview.EMPTY);
        when(runtime.rasterPreview(anyInt(), anyInt())).thenReturn(raster);
        when(runtime.rasterPreview(0, 0)).thenAnswer(invocation -> pending.get() ? ChunkWaterRaster.PROVISIONAL : raster);
        when(raster.role(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            if (x == 1 && z < 4) {
                return WaterRole.POND;
            }
            if (x == 7 && z < 8) {
                return WaterRole.WATERFALL;
            }
            return x < 4 && z == 9 ? WaterRole.INLET : WaterRole.NONE;
        });
        when(engine.getComplex().getHydrologyRuntime()).thenReturn(runtime);
        when(engine.getComplex().getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> {
            if (x == 0D && z == 0D && pending.get()) {
                ProvisionalSampling.mark();
                return 60D;
            }
            return 80D + x * 0.02D + z * 0.01D;
        }));
        MapTileRenderer renderer = new MapTileRenderer(engine);
        MapLayer layer = MapLayer.of(TileType.BIOME);
        MapTileRenderer.TileRender progress = renderer.prepareTile(-16D, -16D, 2D, 16, layer, true);

        MapTile first = progress.render(() -> false);
        int[] firstPixels = pixels(first.image(MapStyle.PLAIN.withShading(true)));
        assertFalse(progress.complete());
        assertEquals(0xFF738249, first.image(MapStyle.PLAIN).getRGB(8, 8));
        MapTile waiting = progress.render(() -> false);
        assertFalse(progress.complete());
        assertArrayEquals(firstPixels, pixels(waiting.image(MapStyle.PLAIN.withShading(true))));

        pending.set(false);
        MapTile actual = progress.render(() -> false);
        assertTrue(progress.complete());
        assertEquals(0L, progress.bytes());
        BufferedImage colors = actual.image(MapStyle.PLAIN);
        assertEquals(0xFF738249, colors.getRGB(14, 14));
        assertNotEquals(0xFF738249, colors.getRGB(8, 8));
        assertNotEquals(colors.getRGB(8, 8), colors.getRGB(11, 8));
        assertNotEquals(colors.getRGB(8, 8), colors.getRGB(8, 12));
        assertNotEquals(colors.getRGB(11, 8), colors.getRGB(8, 12));
        assertArrayEquals(firstPixels, pixels(first.image(MapStyle.PLAIN.withShading(true))));

        MapTile expected = renderer.renderTile(-16D, -16D, 2D, 16, layer, true, () -> false);
        assertArrayEquals(pixels(expected.image(MapStyle.PLAIN.withShading(true))),
                pixels(actual.image(MapStyle.PLAIN.withShading(true))));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(expected.heightAt(x, z), actual.heightAt(x, z), 0D);
                assertEquals(expected.subjectAt(x, z), actual.subjectAt(x, z));
                assertEquals(expected.regionAt(x, z), actual.regionAt(x, z));
            }
        }
    }

    @Test
    public void cancelledPreparationCanResumeWithoutReusingIncompleteHeightSamples() {
        Engine engine = engine(new AtomicInteger());
        AtomicInteger samples = new AtomicInteger();
        when(engine.getComplex().getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> {
            samples.incrementAndGet();
            return x + z + 80D;
        }));
        MapTileRenderer renderer = new MapTileRenderer(engine);
        MapLayer layer = MapLayer.biomes(PickDisplay.PICKS, false);
        MapTileRenderer.TileRender progress = renderer.prepareTile(-16D, -16D, 1D, 16, layer, true);
        assertThrows(CancellationException.class, () -> progress.render(() -> samples.get() >= 9));
        assertFalse(progress.complete());
        MapTile actual = progress.render(() -> false);
        assertTrue(progress.complete());
        assertEquals(81, samples.get());
        MapTile expected = renderer.renderTile(-16D, -16D, 1D, 16, layer, true, () -> false);
        assertArrayEquals(pixels(expected.image(MapStyle.PLAIN.withShading(true))),
                pixels(actual.image(MapStyle.PLAIN.withShading(true))));
    }

    private static Engine engine(AtomicInteger biomeSamples) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiome biome = new IrisBiome();
        biome.setColor("#738249");
        IrisRegion region = new IrisRegion();
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(256);
        when(complex.getBaseBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> {
            biomeSamples.incrementAndGet();
            return biome;
        }, Interpolated.of(value -> 0D, value -> biome)));
        when(complex.getRegionStream()).thenReturn(ProceduralStream.of((x, z) -> region,
                Interpolated.of(value -> 0D, value -> region)));
        return engine;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
